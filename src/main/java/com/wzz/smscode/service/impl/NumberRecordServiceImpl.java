package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.common.CommonResultDTO;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.dto.*;
import com.wzz.smscode.dto.CreatDTO.LedgerCreationDTO;
import com.wzz.smscode.dto.number.NumberDTO;
import com.wzz.smscode.entity.*;
import com.wzz.smscode.enums.FundType;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.NumberRecordMapper;
import com.wzz.smscode.moduleService.SmsApiService;
import com.wzz.smscode.service.*;
import com.wzz.smscode.util.BalanceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NumberRecordServiceImpl extends ServiceImpl<NumberRecordMapper, NumberRecord> implements NumberRecordService {

    @Autowired private UserService userService;
    @Autowired private ProjectService projectService;
    @Autowired private UserLedgerService ledgerService;

    @Lazy @Autowired private NumberRecordService self;

    @Autowired private SystemConfigService systemConfigService;

    // 注入重构后的通用 API 服务
    @Autowired private SmsApiService smsApiService;
    @Autowired private UserProjectLineService userProjectLineService;

    /**
     * 检查指定的手机号是否已经存在于特定项目的记录中。
     */
    private boolean isPhoneNumberExistsInProject(String projectId, String phoneNumber) {
        long count = this.count(new LambdaQueryWrapper<NumberRecord>()
                .eq(NumberRecord::getProjectId, projectId)
                .eq(NumberRecord::getPhoneNumber, phoneNumber)
        );
        return count > 0;
    }

    @Transactional
    @Override
    public CommonResultDTO<String> getNumber(String userName, String password, String projectId, Integer lineId) {
        // 1. 身份验证与余额检查
        User user = userService.authenticateUserByUserName(userName, password);
        if (user == null) return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户验证失败");

        SystemConfig config = systemConfigService.getConfig();
        if (user.getStatus() != 0) {
            return CommonResultDTO.error(-5, "用户已被禁用");
        }
        if (config.getEnableBanMode() == 1 && user.getDailyCodeRate() < config.getMin24hCodeRate()) {
            return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "回码率过低封禁");
        }

        UserProjectLine userProjectLine = userProjectLineService.getByProjectIdLineID(projectId, lineId, user.getId());
        if (userProjectLine == null) return CommonResultDTO.error(-5, "项目线路不存在用户项目中");

        BigDecimal price = userProjectLine.getAgentPrice();
        if (price == null) return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "项目价格设置错误");

        Project projectT = projectService.getProject(projectId, lineId);
        if (projectT == null) return CommonResultDTO.error(-5, "总项目表中不存在这个项目和线路！");
        if (!projectT.isStatus()) {
            return CommonResultDTO.error(Constants.ERROR_NO_NUMBER, "该项目没开启！");
        }

        if (user.getBalance().compareTo(price) < 0) {
            return CommonResultDTO.error(Constants.ERROR_INSUFFICIENT_BALANCE, "余额不足");
        }
        boolean hasOngoingRecord = this.hasOngoingRecord(user.getId());

        if (!BalanceUtil.canGetNumber(user, hasOngoingRecord)) {
            return CommonResultDTO.error(Constants.ERROR_INSUFFICIENT_BALANCE, "余额不足或已有进行中的任务");
        }

        final int MAX_ATTEMPTS = 3;
        Map<String, String> successfulIdentifier = null;
        boolean numberFoundAndVerified = false;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Map<String, String> currentIdentifier;
            try {
                // 【修改点 1】: 调用新接口，不再传 "null"，如果有动态参数可以传 String...
                // getPhoneNumber 返回的是一个包含所有提取变量的 Map (必须包含 phone)
                currentIdentifier = smsApiService.getPhoneNumber(projectT);

                if (!StringUtils.hasText(currentIdentifier.get("phone"))) {
                    if (attempt < MAX_ATTEMPTS) Thread.sleep(500);
                    continue;
                }
            } catch (BusinessException e) {
                log.error("调用接口获取号码失败 (尝试 {}/{})，终止流程: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "接口错误: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "系统线程中断");
            } catch (Exception e) {
                log.error("未知异常", e);
                return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "系统未知错误");
            }

            String phoneNumber = currentIdentifier.get("phone");

            // 重复号码检查
            if (isPhoneNumberExistsInProject(projectId, phoneNumber)) {
                log.warn("号码 [{}] 已存在，重试...", phoneNumber);
                try { if (attempt < MAX_ATTEMPTS) Thread.sleep(200); } catch (InterruptedException ignored) {}
                continue;
            }

            // 号码筛选 (Filter)
            if (config.getEnableNumberFiltering() && projectT.getEnableFilter()) {
                try {
                    Boolean isAvailable = smsApiService.checkPhoneNumberAvailability(projectT, phoneNumber, null)
                            .block(Duration.ofSeconds(60));

                    if (Boolean.TRUE.equals(isAvailable)) {
                        successfulIdentifier = currentIdentifier;
                        numberFoundAndVerified = true;
                        break;
                    } else {
                        log.warn("号码 [{}] 筛选不通过", phoneNumber);
                    }
                } catch (Exception e) {
                    log.error("筛选异常", e);
                }
            } else {
                successfulIdentifier = currentIdentifier;
                numberFoundAndVerified = true;
                break;
            }
        }

        if (!numberFoundAndVerified) {
            return CommonResultDTO.error(Constants.ERROR_NO_CODE, "获取可用号码失败，请稍后重试");
        }

        // 4. 写入号码记录
        NumberRecord record = new NumberRecord();
        record.setUserId(user.getId());
        record.setProjectId(projectId);
        record.setLineId(lineId);
        record.setUserName(user.getUserName());
        record.setPhoneNumber(successfulIdentifier.get("phone"));
        // 【注意】: 确保前端配置中将ID提取为了 "id" 变量
        record.setApiPhoneId(successfulIdentifier.get("id"));
        record.setStatus(0);
        record.setCharged(0);
        record.setPrice(price);
        record.setCostPrice(userProjectLine.getCostPrice());
        record.setBalanceBefore(user.getBalance());
        record.setBalanceAfter(user.getBalance());
        record.setGetNumberTime(LocalDateTime.now());
        this.save(record);

        final Long recordId = record.getId();
        // 这里我们不再传递单一的 string identifier，而是传递记录ID，在 retrieveCode 中重建 Map
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                // 注意：retrieveCode 签名我们保持不变 (Long, String)，
                // 但第二个参数在新逻辑下实际上可以传 null 或者 phone，我们在方法内部会重新构建 Context
                self.retrieveCode(recordId, null);
            }
        });

        userService.updateUserStats(user.getId());
        return CommonResultDTO.success("取号成功，请稍后查询验证码", successfulIdentifier.get("phone"));
    }

    @Async("taskExecutor")
    @Transactional
    @Override
    public void retrieveCode(Long numberId, String unusedIdentifier) {
        NumberRecord record = this.getById(numberId);
        if (record == null || record.getStatus() != 0) {
            return;
        }

        record.setStatus(1); // 更新为取码中
        record.setStartCodeTime(LocalDateTime.now());
        this.updateById(record);

        Project project = projectService.getProject(record.getProjectId(), record.getLineId());

        // 【修改点 2】: 构建 Context Map 传递给 SmsApiService
        Map<String, String> context = new HashMap<>();
        context.put("phone", record.getPhoneNumber());
        if (StringUtils.hasText(record.getApiPhoneId())) {
            context.put("id", record.getApiPhoneId());
        }
        // 如果项目有Token，SmsApiService 内部会自动从 Project 实体读取，这里只需传 phone/id 即可

        String result;
        try {
            // 调用新的 getVerificationCode(Project, Map) 接口
            result = smsApiService.getVerificationCode(project, context);
        } catch (BusinessException e) {
            log.warn("获取验证码业务异常: {}", e.getMessage());
            record.setErrorInfo(String.valueOf(CodeResult.failure()));
            result = null;
            record.setRemark(e.getMessage());
            record.setStatus(3);
            userService.updateUserStats(record.getUserId());
        }

        updateRecordAfterRetrieval_(record, result != null, result);
    }

    /**
     * 【修改点 3】: 同步修改 getCode (手动获取) 逻辑
     */
    @Override
    public CommonResultDTO<String> getCode(String userName, String password, String identifier, String projectId, String lineId) {
        User user = userService.authenticateUserByUserName(userName, password);
        if (user == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户验证失败");
        }
        if (!StringUtils.hasText(lineId)) {
            return CommonResultDTO.error(Constants.ERROR_NO_CODE, "线路ID不能为空");
        }

        Long lineIdAsLong;
        try {
            lineIdAsLong = Long.parseLong(lineId);
        } catch (NumberFormatException e) {
            return CommonResultDTO.error(Constants.ERROR_NO_CODE, "线路ID格式不正确");
        }
        NumberRecord record = this.getOne(new LambdaQueryWrapper<NumberRecord>()
                .eq(NumberRecord::getPhoneNumber, identifier)
                .eq(NumberRecord::getUserId, user.getId())
                .eq(NumberRecord::getLineId, lineIdAsLong)
                .eq(NumberRecord::getProjectId, projectId)
                .orderByDesc(NumberRecord::getGetNumberTime)
                .last("LIMIT 1"));

        if (record == null) {
            return CommonResultDTO.error(Constants.ERROR_NO_CODE, "无验证码记录");
        }

        if (record.getStatus() == 2) {
            return CommonResultDTO.success("验证码获取成功", record.getCode());
        }

        if (record.getStatus() == 4 || record.getStatus() == 3 || record.getStatus() == 1 ) {

            Project project = projectService.getProject(record.getProjectId(), record.getLineId());
            if (project != null) {
                // 【修改点】构建 Context Map
                Map<String, String> context = new HashMap<>();
                context.put("phone", record.getPhoneNumber());
                if (StringUtils.hasText(record.getApiPhoneId())) {
                    context.put("id", record.getApiPhoneId());
                }

                // 调用单次获取方法 (注意：SmsApiService 需要支持 fetchVerificationCodeOnce 且接收 Map 参数)
                // 如果 SmsApiService 中移除了 fetchVerificationCodeOnce，这里需要使用 getVerificationCode 并注意它是阻塞轮询的
                // 假设你已经同步更新了 SmsApiService 增加了 fetchVerificationCodeOnce(Project, Map)
                Optional<String> codeOpt = smsApiService.fetchVerificationCodeOnce(project, context);

                if (codeOpt.isPresent()) {
                    String code = codeOpt.get();
                    if (!code.isEmpty()) {
                        self.updateRecordAfterRetrieval(record, true, code);
                        return CommonResultDTO.success("验证码获取成功", code);
                    }
                    return CommonResultDTO.error(Constants.ERROR_NO_CODE, "没有获取到验证码");
                } else {
                    return CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码获取失败");
                }
            }
        }

        return switch (record.getStatus()) {
            case 2 -> CommonResultDTO.success("验证码获取成功", record.getCode());
            case 3 -> CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码获取超时/失败");
            case 4 -> CommonResultDTO.error(Constants.ERROR_NO_CODE, "该号码无效，请重新取号");
            default -> CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码尚未获取，请稍后重试");
        };
    }

    @Transactional
    @Override
    public void updateRecordAfterRetrieval(NumberRecord record, boolean isSuccess, String result) {
        // 重新从数据库获取记录
        NumberRecord latestRecord = this.getById(record.getId());
        if (latestRecord.getStatus() != 1 && latestRecord.getStatus() != 3 && latestRecord.getStatus() != 4) {
            // 允许从3(超时)或4(失败)状态通过手动刷新变更为成功，但不能重复处理已成功的
            if(latestRecord.getStatus() == 2) return;
        }

        latestRecord.setCodeReceivedTime(LocalDateTime.now());

        if (isSuccess && result != null) {
            latestRecord.setStatus(2);
            latestRecord.setCode(result);
            latestRecord.setCharged(1);
            this.updateById(latestRecord);
            User user = userService.getById(latestRecord.getUserId());

            LedgerCreationDTO ledgerDto = LedgerCreationDTO.builder()
                    .userId(user.getId())
                    .amount(latestRecord.getPrice())
                    .ledgerType(0)
                    .fundType(FundType.BUSINESS_DEDUCTION)
                    .remark("业务扣费")
                    .phoneNumber(record.getPhoneNumber())
                    .code(result)
                    .lineId(record.getLineId())
                    .projectId(record.getProjectId())
                    .build();

            ledgerService.createLedgerAndUpdateBalance(ledgerDto);
            User updatedUser = userService.getById(user.getId());
            latestRecord.setBalanceAfter(updatedUser.getBalance());
            userService.updateUserStats(user.getId());

            try {
                userService.processRebates(latestRecord);
            } catch (Exception e) {
                log.error("处理代理返款时发生严重错误，事务将回滚。记录ID: {}", latestRecord.getId(), e);
                throw new BusinessException("业务扣费成功，但代理返款失败: " + e.getMessage());
            }
        } else {
            // 如果原本就是3或4，保持原状；如果是1（取码中）变成3（超时）
            if(latestRecord.getStatus() == 1) {
                latestRecord.setStatus(3);
                latestRecord.setCharged(0);
            }
        }

        latestRecord.setRemark(record.getRemark());
        this.updateById(latestRecord);
    }


    public void updateRecordAfterRetrieval_(NumberRecord record, boolean isSuccess, String result) {
        // 重新从数据库获取记录
        NumberRecord latestRecord = this.getById(record.getId());
        if (latestRecord.getStatus() != 1 && latestRecord.getStatus() != 3 && latestRecord.getStatus() != 4) {
            // 允许从3(超时)或4(失败)状态通过手动刷新变更为成功，但不能重复处理已成功的
            if(latestRecord.getStatus() == 2) return;
        }

        latestRecord.setCodeReceivedTime(LocalDateTime.now());

        if (isSuccess && result != null) {
            latestRecord.setStatus(2);
            latestRecord.setCode(result);
            latestRecord.setCharged(1);
            this.updateById(latestRecord);
            User user = userService.getById(latestRecord.getUserId());

            LedgerCreationDTO ledgerDto = LedgerCreationDTO.builder()
                    .userId(user.getId())
                    .amount(latestRecord.getPrice())
                    .ledgerType(0)
                    .fundType(FundType.BUSINESS_DEDUCTION)
                    .remark("业务扣费")
                    .phoneNumber(record.getPhoneNumber())
                    .code(result)
                    .lineId(record.getLineId())
                    .projectId(record.getProjectId())
                    .build();

            ledgerService.createLedgerAndUpdateBalance(ledgerDto);
            User updatedUser = userService.getById(user.getId());
            latestRecord.setBalanceAfter(updatedUser.getBalance());
            userService.updateUserStats(user.getId());

            try {
                userService.processRebates(latestRecord);
            } catch (Exception e) {
                log.error("处理代理返款时发生严重错误，事务将回滚。记录ID: {}", latestRecord.getId(), e);
                throw new BusinessException("业务扣费成功，但代理返款失败: " + e.getMessage());
            }
        } else {
            // 如果原本就是3或4，保持原状；如果是1（取码中）变成3（超时）
            if(latestRecord.getStatus() == 1) {
                latestRecord.setStatus(3);
                latestRecord.setCharged(0);
            }
        }

        latestRecord.setRemark(record.getRemark());
        this.updateById(latestRecord);
    }

    // ... 其他统计查询代码 (listUserNumbers, listAllNumbers, getStatisticsReport 等) 保持不变 ...
    // 为节省篇幅，此处省略未变动的统计和列表查询方法

    @Override
    public IPage<NumberDTO> listUserNumbers(Long userId, String password, Integer statusFilter, Date startTime, Date endTime, IPage<NumberRecord> page) {
        if (userService.authenticate(userId, password) == null) return null;
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NumberRecord::getUserId, userId);
        addCommonFilters(wrapper, statusFilter, startTime, endTime, null, null, null, null,null);
        IPage<NumberRecord> recordPage = this.page(page, wrapper);
        return recordPage.convert(this::convertToDTO);
    }

    @Override
    public IPage<NumberDTO> listUserNumbersByUSerName(String userName, String password, Integer statusFilter, Date startTime, Date endTime, IPage<NumberRecord> page) {
        User user = userService.authenticateUserByUserName(userName, password);
        if (user == null) return null;

        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NumberRecord::getUserId, user.getId());
        addCommonFilters(wrapper, statusFilter, startTime, endTime, null, null, null, null, null);

        final long MAX_RECORDS_LIMIT = 50;
        long actualTotal = this.count(wrapper.clone());
        long effectiveTotal = Math.min(actualTotal, MAX_RECORDS_LIMIT);

        if (page.offset() >= MAX_RECORDS_LIMIT) {
            IPage<NumberRecord> resultPage = new Page<>(page.getCurrent(), page.getSize(), effectiveTotal);
            resultPage.setRecords(Collections.emptyList());
            return resultPage.convert(this::convertToDTO);
        }

        long remainingRecords = MAX_RECORDS_LIMIT - page.offset();
        if (page.getSize() > remainingRecords) {
            page.setSize(remainingRecords);
        }

        IPage<NumberRecord> recordPage = this.page(page, wrapper);
        recordPage.setTotal(effectiveTotal);

        return recordPage.convert(this::convertToDTO);
    }

    @Override
    public IPage<NumberRecord> listAllNumbers(Integer statusFilter, Date startTime, Date endTime,
                                              Long userId, String projectId, String phoneNumber, Integer charged,
                                              IPage<NumberRecord> page, String lineId, String userName) {
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        if (userName != null && !userName.trim().isEmpty()) {
            List<Long> subUserIds = userService.list(new LambdaQueryWrapper<User>().like(User::getUserName, userName))
                    .stream().map(User::getId).toList();
            if (subUserIds.isEmpty()) {
                return page.setTotal(0).setRecords(Collections.emptyList());
            }
            wrapper.in(NumberRecord::getUserId, subUserIds);
        }
        addCommonFilters(wrapper, statusFilter, startTime, endTime, userId, projectId, phoneNumber, charged, lineId);
        return this.page(page, wrapper);
    }

    @Override
    public NumberRecord getRecordByPhone(String phone) {
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NumberRecord::getPhoneNumber,phone);
        return this.getOne(wrapper);
    }

    private boolean hasOngoingRecord(Long userId) {
        return this.count(new LambdaQueryWrapper<NumberRecord>()
                .eq(NumberRecord::getUserId, userId)
                .in(NumberRecord::getStatus, 0, 1)) > 0;
    }

    private void addCommonFilters(LambdaQueryWrapper<NumberRecord> wrapper, Integer status, Date start, Date end,
                                  Long userId, String projectId, String phoneNumber, Integer charged,String lineId) {
        wrapper.eq(status != null, NumberRecord::getStatus, status);
        wrapper.ge(start != null, NumberRecord::getGetNumberTime, start);
        wrapper.le(end != null, NumberRecord::getGetNumberTime, end);
        wrapper.eq(userId != null, NumberRecord::getUserId, userId);
        wrapper.eq(StringUtils.hasText(projectId), NumberRecord::getProjectId, projectId);
        wrapper.eq(StringUtils.hasText(lineId), NumberRecord::getLineId, lineId);
        wrapper.like(StringUtils.hasText(phoneNumber), NumberRecord::getPhoneNumber, phoneNumber);
        wrapper.eq(charged != null, NumberRecord::getCharged, charged);
        wrapper.orderByDesc(NumberRecord::getGetNumberTime);
    }

    private NumberDTO convertToDTO(NumberRecord record) {
        NumberDTO dto = new NumberDTO();
        BeanUtils.copyProperties(record, dto);
        return dto;
    }

    @Override
    public List<String> getPhoneNumbersByProjectId(String projectId) {
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(NumberRecord::getPhoneNumber);
        wrapper.eq(NumberRecord::getProjectId, projectId);
        List<NumberRecord> records = this.list(wrapper);
        return records.stream()
                .map(NumberRecord::getPhoneNumber)
                .collect(Collectors.toList());
    }

    // ... 报表相关方法 (getStatisticsReport 等) 保持原样 ...
    @Override
    public IPage<ProjectStatisticsDTO> getStatisticsReport(Long operatorId, StatisticsQueryDTO queryDTO) {
        User operator = new User();
        if (operatorId != 0L) {
            operator = userService.getById(operatorId);
            if (operator == null) throw new BusinessException("操作用户不存在");
        }
        if (operatorId == 0L) {
            return generateAdminStatistics(queryDTO);
        } else if (operator.getIsAgent() == 1) {
            return generateAgentStatistics(operatorId, queryDTO);
        } else {
            return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        }
    }

    private IPage<ProjectStatisticsDTO> generateAdminStatistics(StatisticsQueryDTO queryDTO) {
        QueryWrapper<NumberRecord> wrapper = new QueryWrapper<>();
        applyFilters(wrapper, queryDTO);
        wrapper.groupBy("project_id", "line_id");
        wrapper.select("project_id", "line_id", "COUNT(id) as totalRequests",
                "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as successCount",
                "COALESCE(SUM(CASE WHEN charged = 1 THEN price ELSE 0 END), 0) as totalRevenue",
                "COALESCE(SUM(CASE WHEN charged = 1 THEN cost_price ELSE 0 END), 0) as totalCost");
        Page<Map<String, Object>> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        IPage<Map<String, Object>> aggregatedDataPage = this.pageMaps(page, wrapper);
        if (aggregatedDataPage.getRecords().isEmpty()) return new Page<>(queryDTO.getCurrent(), queryDTO.getSize(), aggregatedDataPage.getTotal());

        Map<String, String> projectNames = projectService.list().stream()
                .collect(Collectors.toMap(Project::getProjectId, Project::getProjectName, (p1, p2) -> p1));
        List<ProjectStatisticsDTO> dtoList = processAggregatedData(aggregatedDataPage.getRecords(), null, projectNames);
        IPage<ProjectStatisticsDTO> resultPage = new Page<>(aggregatedDataPage.getCurrent(), aggregatedDataPage.getSize(), aggregatedDataPage.getTotal());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    private IPage<ProjectStatisticsDTO> generateAgentStatistics(Long agentId, StatisticsQueryDTO queryDTO) {
        List<Long> subUserIds = userService.list(new LambdaQueryWrapper<User>().eq(User::getParentId, agentId))
                .stream().map(User::getId).collect(Collectors.toList());
        if (subUserIds.isEmpty()) return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());

        Map<String, BigDecimal> agentCostMap = userProjectLineService.list(new LambdaQueryWrapper<UserProjectLine>().eq(UserProjectLine::getUserId, agentId))
                .stream().collect(Collectors.toMap(line -> line.getProjectId() + "-" + line.getLineId(), UserProjectLine::getAgentPrice));

        QueryWrapper<NumberRecord> wrapper = new QueryWrapper<>();
        wrapper.in("user_id", subUserIds);
        applyFilters(wrapper, queryDTO);
        wrapper.groupBy("project_id", "line_id");
        wrapper.select("project_id", "line_id", "COUNT(id) as totalRequests",
                "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as successCount",
                "COALESCE(SUM(CASE WHEN charged = 1 THEN price ELSE 0 END), 0) as totalRevenue");
        Page<Map<String, Object>> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        IPage<Map<String, Object>> aggregatedDataPage = this.pageMaps(page, wrapper);
        if (aggregatedDataPage.getRecords().isEmpty()) return new Page<>(queryDTO.getCurrent(), queryDTO.getSize(), aggregatedDataPage.getTotal());

        Map<String, String> projectNames = projectService.list().stream()
                .collect(Collectors.toMap(Project::getProjectId, Project::getProjectName, (p1, p2) -> p1));
        List<ProjectStatisticsDTO> dtoList = processAggregatedData(aggregatedDataPage.getRecords(), agentCostMap, projectNames);
        IPage<ProjectStatisticsDTO> resultPage = new Page<>(aggregatedDataPage.getCurrent(), aggregatedDataPage.getSize(), aggregatedDataPage.getTotal());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    private void applyFilters(QueryWrapper<NumberRecord> wrapper, StatisticsQueryDTO queryDTO) {
        if (StringUtils.hasText(queryDTO.getProjectName())) {
            List<String> projectIds = projectService.list(
                    new LambdaQueryWrapper<Project>().like(Project::getProjectName, queryDTO.getProjectName())
            ).stream().map(Project::getProjectId).collect(Collectors.toList());
            if (projectIds.isEmpty()) { wrapper.eq("1", 0); return; }
            wrapper.in("project_id", projectIds);
        }
        if (StringUtils.hasText(queryDTO.getProjectId())) wrapper.eq("project_id", queryDTO.getProjectId());
        if (queryDTO.getLineId() != null) wrapper.eq("line_id", queryDTO.getLineId());
        LocalDateTime startTime = parseAndAdjustDateTime(queryDTO.getStartTime(), false);
        if (startTime != null) wrapper.ge("get_number_time", startTime);
        LocalDateTime endTime = parseAndAdjustDateTime(queryDTO.getEndTime(), true);
        if (endTime != null) wrapper.le("get_number_time", endTime);
    }

    private LocalDateTime parseAndAdjustDateTime(String dateTimeStr, boolean isEndDate) {
        if (!StringUtils.hasText(dateTimeStr)) return null;
        try {
            if (dateTimeStr.contains("T") && dateTimeStr.endsWith("Z")) {
                return ZonedDateTime.parse(dateTimeStr).toLocalDateTime();
            } else if (dateTimeStr.contains(" ")) {
                String standardizedStr = dateTimeStr.length() == 16 ? dateTimeStr + ":00" : dateTimeStr;
                return LocalDateTime.parse(standardizedStr.replace(" ", "T"));
            } else {
                LocalDate date = LocalDate.parse(dateTimeStr);
                return isEndDate ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
        } catch (DateTimeParseException e) {
            log.warn("无法解析的日期时间格式: '{}'", dateTimeStr, e);
            return null;
        }
    }

    private List<ProjectStatisticsDTO> processAggregatedData(List<Map<String, Object>> aggregatedData, Map<String, BigDecimal> agentCostMap, Map<String, String> projectNames) {
        Map<String, ProjectStatisticsDTO> projectReportMap = new LinkedHashMap<>();
        for (Map<String, Object> lineData : aggregatedData) {
            String projectId = (String) lineData.get("project_id");
            Object lineIdObj = lineData.get("line_id");
            if (projectId == null || lineIdObj == null) continue;
            Integer lineId = ((Number) lineIdObj).intValue();
            long totalRequests = ((Number) lineData.get("totalRequests")).longValue();
            long successCount = ((Number) lineData.get("successCount")).longValue();
            BigDecimal totalRevenue = new BigDecimal(lineData.get("totalRevenue").toString());

            ProjectStatisticsDTO projectDTO = projectReportMap.computeIfAbsent(projectId, k -> {
                ProjectStatisticsDTO dto = new ProjectStatisticsDTO();
                dto.setProjectId(k);
                dto.setProjectName(projectNames.getOrDefault(k, "未知项目"));
                dto.setTotalRequests(0L);
                dto.setSuccessCount(0L);
                dto.setTotalRevenue(BigDecimal.ZERO);
                dto.setTotalCost(BigDecimal.ZERO);
                dto.setLineDetails(new ArrayList<>());
                return dto;
            });

            BigDecimal lineTotalCost;
            if (agentCostMap != null) {
                String priceKey = projectId + "-" + lineId;
                BigDecimal agentPricePerSuccess = agentCostMap.getOrDefault(priceKey, BigDecimal.ZERO);
                lineTotalCost = agentPricePerSuccess.multiply(new BigDecimal(successCount));
            } else {
                lineTotalCost = new BigDecimal(lineData.get("totalCost") != null ? lineData.get("totalCost").toString() : "0");
            }

            LineStatisticsDTO lineDTO = new LineStatisticsDTO();
            lineDTO.setProjectId(projectId);
            lineDTO.setLineId(lineId);
            lineDTO.setProjectName(projectNames.getOrDefault(projectId, "未知项目"));
            lineDTO.setTotalRequests(totalRequests);
            lineDTO.setSuccessCount(successCount);
            lineDTO.setSuccessRate(totalRequests > 0 ? (double) successCount * 100.0 / totalRequests : 0.0);
            lineDTO.setTotalRevenue(totalRevenue);
            lineDTO.setTotalCost(lineTotalCost);
            lineDTO.setTotalProfit(totalRevenue.subtract(lineTotalCost));
            projectDTO.getLineDetails().add(lineDTO);
            projectDTO.setTotalRequests(projectDTO.getTotalRequests() + totalRequests);
            projectDTO.setSuccessCount(projectDTO.getSuccessCount() + successCount);
            projectDTO.setTotalRevenue(projectDTO.getTotalRevenue().add(totalRevenue));
            projectDTO.setTotalCost(projectDTO.getTotalCost().add(lineTotalCost));
        }
        projectReportMap.values().forEach(p -> {
            p.setLineCount(p.getLineDetails().size());
            p.setSuccessRate(p.getTotalRequests() > 0 ? (double) p.getSuccessCount() * 100.0 / p.getTotalRequests() : 0.0);
            p.setTotalProfit(p.getTotalRevenue().subtract(p.getTotalCost()));
        });
        return new ArrayList<>(projectReportMap.values());
    }

    @Override
    public IPage<NumberDTO> listSubordinateRecordsForAgent(Long agentId, SubordinateNumberRecordQueryDTO queryDTO) {
        List<Long> targetUserIds = new ArrayList<>();
        if (StringUtils.hasText(queryDTO.getUserName())) {
            User targetUser = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUserName, queryDTO.getUserName()));
            if (targetUser != null && agentId.equals(targetUser.getParentId())) targetUserIds.add(targetUser.getId());
            else return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        } else {
            List<User> subUsers = userService.list(new LambdaQueryWrapper<User>().select(User::getId).eq(User::getParentId, agentId));
            if (subUsers.isEmpty()) return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
            targetUserIds = subUsers.stream().map(User::getId).collect(Collectors.toList());
        }

        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(NumberRecord::getUserId, targetUserIds);
        if (StringUtils.hasText(queryDTO.getProjectName())) {
            List<String> projectIds = projectService.list(new LambdaQueryWrapper<Project>().like(Project::getProjectName, queryDTO.getProjectName())).stream().map(Project::getProjectId).collect(Collectors.toList());
            if (projectIds.isEmpty()) return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
            wrapper.in(NumberRecord::getProjectId, projectIds);
        }
        wrapper.eq(StringUtils.hasText(queryDTO.getProjectId()), NumberRecord::getProjectId, queryDTO.getProjectId());
        wrapper.eq(queryDTO.getLineId() != null, NumberRecord::getLineId, queryDTO.getLineId());
        wrapper.like(StringUtils.hasText(queryDTO.getPhoneNumber()), NumberRecord::getPhoneNumber, queryDTO.getPhoneNumber());
        wrapper.eq(queryDTO.getStatus() != null, NumberRecord::getStatus, queryDTO.getStatus());
        wrapper.eq(queryDTO.getCharged() != null, NumberRecord::getCharged, queryDTO.getCharged());
        LocalDateTime startTime = parseAndAdjustDateTime(queryDTO.getStartTime(), false);
        LocalDateTime endTime = parseAndAdjustDateTime(queryDTO.getEndTime(), true);
        wrapper.ge(startTime != null, NumberRecord::getGetNumberTime, startTime);
        wrapper.le(endTime != null, NumberRecord::getGetNumberTime, endTime);
        wrapper.orderByDesc(NumberRecord::getGetNumberTime);
        return this.page(new Page<>(queryDTO.getCurrent(), queryDTO.getSize()), wrapper).convert(this::convertToDTO);
    }

    @Override
    public IPage<UserLineStatsDTO> getUserLineStats(UserLineStatsRequestDTO requestDTO, Long agentId) {
        // -----------------------------------------------------------------
        // 步骤 1: 处理用户关联过滤 (如果需要按用户名或代理商筛选)
        // -----------------------------------------------------------------
        List<Long> filterUserIds = null;
        boolean needUserFilter = StringUtils.hasText(requestDTO.getUserName()) || agentId != null;

        if (needUserFilter) {
            // 使用 Lambda 查询用户表
            List<User> users = userService.lambdaQuery()
                    .select(User::getId) // 只查ID，优化性能
                    .like(StringUtils.hasText(requestDTO.getUserName()), User::getUserName, requestDTO.getUserName())
                    .eq(agentId != null, User::getParentId, agentId)
                    .list();

            if (CollectionUtils.isEmpty(users)) {
                // 如果筛选条件下没有找到用户，直接返回空分页
                return new Page<>(requestDTO.getPage(), requestDTO.getSize());
            }
            filterUserIds = users.stream().map(User::getId).collect(Collectors.toList());
        }

        // -----------------------------------------------------------------
        // 步骤 2: 构建分组统计查询 (混合 Lambda 和 String Select)
        // -----------------------------------------------------------------
        // 注意：这里必须用 QueryWrapper 而不是 LambdaQueryWrapper，因为 .select() 需要写聚合函数
        QueryWrapper<NumberRecord> queryWrapper = new QueryWrapper<>();

        // 2.1 定义 Select 聚合字段
        // 使用数据库函数 SUM(CASE...) 来统计成功的验证码数
        queryWrapper.select(
                "user_id",
                "project_id",
                "line_id",
                "COUNT(*) as totalNumbers",
                "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as totalCodes"
        );

        // 2.2 转换到 Lambda 模式添加 Where 条件 (保持类型安全)
        queryWrapper.lambda()
                .eq(StringUtils.hasText(requestDTO.getProjectId()), NumberRecord::getProjectId, requestDTO.getProjectId())
                .ge(requestDTO.getStartTime() != null, NumberRecord::getGetNumberTime, requestDTO.getStartTime())
                .le(requestDTO.getEndTime() != null, NumberRecord::getGetNumberTime, requestDTO.getEndTime())
                // 如果步骤1计算出了 ID 列表，这里进行 IN 查询
                .in(needUserFilter, NumberRecord::getUserId, filterUserIds);

        // 2.3 添加分组
        queryWrapper.groupBy("user_id", "project_id", "line_id");

        // 2.4 添加排序 (按取号量降序)
        queryWrapper.orderByDesc("totalNumbers");

        // -----------------------------------------------------------------
        // 步骤 3: 执行查询并转换结果 (selectMapsPage)
        // -----------------------------------------------------------------
        Page<Map<String, Object>> mapPage = new Page<>(requestDTO.getPage(), requestDTO.getSize());
        // 使用 Mybatis-Plus 的 selectMapsPage，返回 Map 列表
        IPage<Map<String, Object>> resultPage = this.baseMapper.selectMapsPage(mapPage, queryWrapper);

        // -----------------------------------------------------------------
        // 步骤 4: Map 转 DTO 并 补全用户名
        // -----------------------------------------------------------------
        List<UserLineStatsDTO> dtoList = new ArrayList<>();

        // 收集所有结果中的 userId，用于批量查询用户名
        Set<Long> resultUserIds = new HashSet<>();

        for (Map<String, Object> map : resultPage.getRecords()) {
            UserLineStatsDTO dto = new UserLineStatsDTO();
            // 注意：Map 的 Key 通常是 select 里的字段名
            Long uId = Long.valueOf(map.get("user_id").toString());
            dto.setUserId(uId);
            dto.setProjectId((String) map.get("project_id"));
            dto.setLineId((Integer) map.get("line_id"));
            // 处理 Long/BigDecimal 类型转换 (数据库返回的 Count/Sum 类型可能不同)
            dto.setTotalNumbers(new BigDecimal(map.get("totalNumbers").toString()).longValue());
            dto.setTotalCodes(new BigDecimal(map.get("totalCodes").toString()).longValue());

            // 计算成功率
            dto.calculateRate();

            dtoList.add(dto);
            resultUserIds.add(uId);
        }

        // 批量补全用户名 (避免循环查库 N+1 问题)
        if (!resultUserIds.isEmpty()) {
            Map<Long, String> userMap = userService.listByIds(resultUserIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUserName));

            for (UserLineStatsDTO dto : dtoList) {
                dto.setUserName(userMap.getOrDefault(dto.getUserId(), "未知用户"));
            }
        }

        // 构造最终返回的 Page 对象
        Page<UserLineStatsDTO> finalPage = new Page<>(requestDTO.getPage(), requestDTO.getSize());
        finalPage.setTotal(resultPage.getTotal());
        finalPage.setRecords(dtoList);

        return finalPage;
    }
}