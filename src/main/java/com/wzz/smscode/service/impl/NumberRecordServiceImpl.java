package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.common.CommonResultDTO;
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

    @Autowired
    private SystemConfigService systemConfigService;



    // 2. 注入我们统一的API服务
    @Autowired private SmsApiService smsApiService;
    @Autowired private UserProjectLineService userProjectLineService;


    /**
     * 检查指定的手机号是否已经存在于特定项目的记录中。
     *
     * @param projectId   项目ID
     * @param phoneNumber 要检查的手机号
     * @return 如果存在，返回 true；否则返回 false
     */
    private boolean isPhoneNumberExistsInProject(String projectId, String phoneNumber) {
        // 使用 count 方法，这比查询整个列表要高效得多
        // 它会生成类似 SELECT COUNT(*) ... 的 SQL 语句
        long count = this.count(new LambdaQueryWrapper<NumberRecord>()
                .eq(NumberRecord::getProjectId, projectId)
                .eq(NumberRecord::getPhoneNumber, phoneNumber)
        );
        return count > 0;
    }

    @Transactional
    @Override
    public CommonResultDTO<String> getNumber(String userName, String password, String projectId, Integer lineId) {
        // 1. 身份验证
        User user = userService.authenticateUserByUserName(userName, password);
        if (user == null) return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户验证失败");

        SystemConfig config = systemConfigService.getConfig();
        if (user.getStatus() != 0){
            return CommonResultDTO.error(-5, "用户已被禁用");
        }
        if(config.getEnableBanMode()==1 && user.getDailyCodeRate() < config.getMin24hCodeRate()) {
            return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR,"回码率过低封禁");
        }
//        Project project = projectService.getProject(projectId, lineId);
        UserProjectLine userProjectLine = userProjectLineService.getByProjectIdLineID(projectId,lineId,user.getId());
        if (userProjectLine == null) return CommonResultDTO.error(-5, "项目线路不存在用户项目中");

        BigDecimal price = userProjectLine.getAgentPrice();

        if (price == null) return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "项目价格设置错误");


        Project projectT = projectService.getProject(projectId, lineId);
        if (projectT == null) return CommonResultDTO.error(-5, "总项目表中不存在这个项目和线路！");

        if (!projectT.isStatus()){
            return CommonResultDTO.error(Constants.ERROR_NO_NUMBER,"该项目没开启！");
        }

        if (user.getBalance().compareTo(price) < 0) {
            return CommonResultDTO.error(Constants.ERROR_INSUFFICIENT_BALANCE, "余额不足");
        }
        boolean hasOngoingRecord = this.hasOngoingRecord(user.getId());
        if (!BalanceUtil.canGetNumber(user, hasOngoingRecord)) {
            return CommonResultDTO.error(Constants.ERROR_INSUFFICIENT_BALANCE, "余额不足或已有进行中的任务");
        }
        final int MAX_ATTEMPTS = 3; // 定义最大尝试次数
        Map<String, String> successfulIdentifier = null;
        boolean numberFoundAndVerified = false;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
//            log.info("正在进行第 {}/{} 次获取并验证手机号...", attempt, MAX_ATTEMPTS);
            Map<String, String> currentIdentifier;
            try {
                String numForApi = "1";
                currentIdentifier = smsApiService.getPhoneNumber(projectT, numForApi);
//                log.info("第 {} 次尝试，获取到手机号信息：{}", attempt, currentIdentifier);
                if (!StringUtils.hasText(currentIdentifier.get("phone"))) {
//                    log.warn("第 {} 次尝试，上游平台未返回有效手机号，即将重试...", attempt);
                    if (attempt < MAX_ATTEMPTS) Thread.sleep(500);
                    continue; // 继续下一次循环
                }
            } catch (BusinessException e) {
                log.error("调用接口获取号码失败 (尝试 {}/{})，这是一个致命错误，终止流程: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "接口错误: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("重试等待时线程被中断", e);
                return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "系统线程中断");
            }
            String phoneNumber = currentIdentifier.get("phone");
            if (isPhoneNumberExistsInProject(projectId, phoneNumber)) {
                log.warn("号码 [{}] 在项目 [{}] 的记录中已存在，将丢弃并重新获取...", phoneNumber, projectId);
                // 可以在这里加一个短暂的延时，避免过于频繁地请求上游
                try {
                    if (attempt < MAX_ATTEMPTS) Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue; // 跳过当前号码，进行下一次尝试
            }
            if (config.getEnableNumberFiltering() && projectT.getEnableFilter()) {
                try {
//                    String phoneNumber = currentIdentifier.get("phone");
//                    log.info("号码筛选已开启，正在检查号码 [{}] 可用性...", phoneNumber);

                    Boolean isAvailable = smsApiService.checkPhoneNumberAvailability(projectT, phoneNumber, null)
                            .block(Duration.ofSeconds(60)); // 60秒超时保护

                    if (Boolean.TRUE.equals(isAvailable)) {
//                        log.info("号码 [{}] 筛选通过！成功获取可用号码。", phoneNumber);
                        successfulIdentifier = currentIdentifier;
                        numberFoundAndVerified = true;
                        break; // 成功，跳出循环
                    } else {
                        log.warn("号码 [{}] 筛选结果为不可用。即将尝试获取下一个号码...", phoneNumber);
                        // 让循环自然进入下一次迭代
                    }
                } catch (Exception e) {
                    log.error("号码筛选过程中发生异常 (尝试 {}/{})，将视为号码不可用: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                }
            } else {
                // 步骤 C: 如果不需要筛选，获取到的第一个号码即为成功
//                log.info("号码筛选未开启，默认获取的号码可用。");
                successfulIdentifier = currentIdentifier;
                numberFoundAndVerified = true;
                break; // 成功，跳出循环
            }
        }
        if (!numberFoundAndVerified) {
//            log.error("在 {} 次尝试后，未能获取到一个可用的手机号。", MAX_ATTEMPTS);
            return CommonResultDTO.error(Constants.ERROR_NO_CODE, "获取可用号码失败，请稍后重试");
        }

        // 4. 写入号码记录并启动取码线程
        NumberRecord record = new NumberRecord();
        record.setUserId(user.getId());
        record.setProjectId(projectId);
        record.setLineId(lineId);
        record.setUserName(user.getUserName());
        record.setPhoneNumber(successfulIdentifier.get("phone"));
        record.setApiPhoneId(successfulIdentifier.get("id"));
        record.setStatus(0); // 待取码
        record.setCharged(0); // 默认未扣费
        record.setPrice(price);
        record.setCostPrice(userProjectLine.getCostPrice());
        record.setBalanceBefore(user.getBalance());
        record.setBalanceAfter(user.getBalance());
        record.setGetNumberTime(LocalDateTime.now());
        this.save(record);

        final String finalIdentifierId;
        String identifierKey = projectT.getCodeRetrievalIdentifierKey();

        if ("id".equalsIgnoreCase(identifierKey)) {
            finalIdentifierId = successfulIdentifier.get("id");
        } else {
            // 默认或明确配置为 "phone" 时，使用手机号
            finalIdentifierId = successfulIdentifier.get("phone");
        }

        final Long recordId = record.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
//                log.info("事务已提交，开始为记录ID {} 异步获取验证码...", recordId);
                self.retrieveCode(recordId, finalIdentifierId);
            }
        });

        //更新统计
        userService.updateUserStats(user.getId());

        // 6. 返回操作ID或部分号码给用户 (使用最终成功的 `successfulIdentifier`)
        return CommonResultDTO.success("取号成功，请稍后查询验证码", successfulIdentifier.get("phone"));
    }



    @Async("taskExecutor")
    @Override
    public void retrieveCode(Long numberId, String identifier) { // 接收 identifier
        NumberRecord record = this.getById(numberId);
        if (record == null || record.getStatus() != 0) {
//            log.info("号码记录 {} 无需取码，状态: {}", numberId, record != null ? record.getStatus() : "null");
            return;
        }

        record.setStatus(1); // 更新为取码中
        record.setStartCodeTime(LocalDateTime.now());
        this.updateById(record);

        Project project = projectService.getProject(record.getProjectId(), record.getLineId());

        // 8. 【核心改造】调用统一服务获取验证码，内部已包含轮询和超时逻辑
        String result;
        try {
            result = smsApiService.getVerificationCode(project, identifier);
        } catch (BusinessException e) {
            log.warn("获取验证码过程中发生业务异常 for record {}: {}", numberId, e.getMessage());
            record.setErrorInfo(String.valueOf(CodeResult.failure()));
            result = null; // 视作失败
            record.setRemark(e.getMessage());
            record.setStatus(3);
//            userService.updateUserStatsForNewNumber(record.getUserId(), false);
            userService.updateUserStats(record.getUserId());

        }

        // 9. 更新最终状态和执行扣费
        updateRecordAfterRetrieval(record, result);
    }

    @Transactional
    @Override
    public void updateRecordAfterRetrieval(NumberRecord record, boolean is, String result) {
        // 重新从数据库获取记录，确保数据最新
        NumberRecord latestRecord = this.getById(record.getId());
        if (latestRecord.getStatus() != 1) { // 防止重复处理
            return;
        }

        latestRecord.setCodeReceivedTime(LocalDateTime.now());

        if (result != null) {
            latestRecord.setStatus(2);
            latestRecord.setCode(result);
            latestRecord.setCharged(1);
            this.updateById(latestRecord);
            User user = userService.getById(latestRecord.getUserId());

            // 使用统一的账本创建服务来处理扣费和记账
            LedgerCreationDTO ledgerDto = LedgerCreationDTO.builder()
                    .userId(user.getId())
                    .amount(latestRecord.getPrice()) // 统一方法要求金额为正数
                    .ledgerType(0) // 0-出账
                    .fundType(FundType.BUSINESS_DEDUCTION) // 资金业务类型为业务扣费
                    .remark("业务扣费") // 备注
                    .phoneNumber(record.getPhoneNumber())
                    .code(result)
                    .lineId(record.getLineId())
                    .projectId(record.getProjectId())
                    .build();

            // 调用统一的账本服务，该方法内部会处理用户余额的更新和账本记录的创建
            ledgerService.createLedgerAndUpdateBalance(ledgerDto);
            User updatedUser = userService.getById(user.getId());
            latestRecord.setBalanceAfter(updatedUser.getBalance());
//            userService.updateUserStatsForNewNumber(user.getId(), true);
            userService.updateUserStats(user.getId());
        } else {
            latestRecord.setStatus(3);
            latestRecord.setCharged(0);
        }

        latestRecord.setRemark(record.getRemark());
        this.updateById(latestRecord);
    }

/**
     * 【核心修改】重写 getCode 方法
     */
    @Override
    public CommonResultDTO<String> getCode(String userName, String password, String identifier,String projectId,String lineId) {
        User user = userService.authenticateUserByUserName(userName, password);
        if (user == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户验证失败");
        }
        // --- 修改开始 ---
        // 1. 校验 lineId 是否为空
        if (!StringUtils.hasText(lineId)) { // 或者使用其他非空判断
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

        // 如果状态已经是成功，直接返回结果，避免不必要的API调用
        if (record.getStatus() == 2) {
            return CommonResultDTO.success("验证码获取成功", record.getCode());
        }

        if (record.getStatus() == 4 || record.getStatus() == 3) {
//            log.info("记录 {} 状态为进行中，用户主动查询，触发一次即时验证码获取...", record.getId());
            Project project = projectService.getProject(record.getProjectId(), record.getLineId());
            if (project != null) {
                // 根据项目配置决定使用 phone 还是 id 作为API请求的 identifier
                String key = project.getCodeRetrievalIdentifierKey();
                Optional<String> codeOpt;
                if (key != null && key.equals("phone") ) {
                    log.info("使用手机号请求：{}", record.getPhoneNumber());
                    codeOpt = smsApiService.fetchVerificationCodeOnce(project, record.getPhoneNumber());
                }else {
                    log.info("使用id请求：{}", record.getPhoneNumber());
                    codeOpt = smsApiService.fetchVerificationCodeOnce(project, record.getApiPhoneId());
                }
//                final String finalIdentifierId = project.getResponsePhoneField() != null
//                        ? record.getApiPhoneId()
//                        : record.getPhoneNumber();

                // 调用单次获取方法


                if (codeOpt.isPresent()) {
                    String code = codeOpt.get();
                    if (!code.isEmpty()){
//                        log.info("即时获取成功！为记录 {} 获取到验证码: {}", record.getId(), code);
//                        CodeResult codeResult = new CodeResult();
//                        codeResult.setCode(code);
//                        codeResult.setPhoneNumber(record.getPhoneNumber());
//                        codeResult.setSuccess(true);

//                        self.updateRecordAfterRetrieval(record, code);
                        self.updateRecordAfterRetrieval(record,true, code);
                        //userService.updateUserStats(user.getId());
                        return CommonResultDTO.success("验证码获取成功", code);
                    }
                    //userService.updateUserStats(user.getId());
                    return CommonResultDTO.error(Constants.ERROR_NO_CODE,"没有获取到验证码");
                } else {
                    //userService.updateUserStats(user.getId());
//                    log.info("即时获取未返回有效验证码，继续等待异步任务结果。");
                    return CommonResultDTO.error(Constants.ERROR_NO_CODE,"验证码获取失败");
                }
            }
        }

        // 如果即时获取失败或状态不允许，则按原逻辑返回当前状态
        return switch (record.getStatus()) {
            case 2 -> // 虽然前面处理过，但保留以防万一
                    CommonResultDTO.success("验证码获取成功", record.getCode());
            case 3 -> CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码获取超时/失败");
            case 4 -> CommonResultDTO.error(Constants.ERROR_NO_CODE, "该号码无效，请重新取号");
            default -> // 状态为 0 或 1
                    CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码尚未获取，请稍后重试");
        };
    }
//    @Override
//    public CommonResultDTO<String> getCode(Long userId, String password, String phoneNumber) {
//        return null;
//    }

    @Transactional
    public void updateRecordAfterRetrieval(NumberRecord record, String code) {
        // 重新从数据库获取记录，确保数据最新
        NumberRecord latestRecord = this.getById(record.getId());
        if (latestRecord.getStatus() != 1) { // 防止重复处理
            return;
        }
        User user = userService.getById(latestRecord.getUserId());

        latestRecord.setCodeReceivedTime(LocalDateTime.now());

        if (code != null) {
            latestRecord.setStatus(2);
            latestRecord.setCode(code);
            latestRecord.setCharged(1);
            this.updateById(latestRecord);

            // 使用统一的账本创建服务来处理扣费和记账
            LedgerCreationDTO ledgerDto = LedgerCreationDTO.builder()
                    .userId(user.getId())
                    .amount(latestRecord.getPrice()) // 统一方法要求金额为正数
                    .ledgerType(0) // 0-出账
                    .fundType(FundType.BUSINESS_DEDUCTION) // 资金业务类型为业务扣费
                    .remark("业务扣费") // 备注
                    .phoneNumber(record.getPhoneNumber())
                    .code(code)
                    .lineId(record.getLineId())
                    .projectId(record.getProjectId())
                    .build();

            // 调用统一的账本服务，该方法内部会处理用户余额的更新和账本记录的创建
            ledgerService.createLedgerAndUpdateBalance(ledgerDto);
            User updatedUser = userService.getById(user.getId());
            latestRecord.setBalanceAfter(updatedUser.getBalance());
//            userService.updateUserStatsForNewNumber(user.getId(), true);
            userService.updateUserStats(user.getId());
        } else {
            latestRecord.setStatus(3);
            latestRecord.setCharged(0);
        }
        latestRecord.setRemark(record.getRemark());
        userService.updateUserStats(user.getId());
        this.updateById(latestRecord);
    }

//
//    @Override
//    public CommonResultDTO<String> getCode(String userName, String password, String phoneNumber) {
//        User user =  userService.authenticateUserByUserName(userName, password);
//        if ( user == null) {
//            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户验证失败");
//        }
//
//        NumberRecord record = this.getOne(new LambdaQueryWrapper<NumberRecord>()
//                .eq(NumberRecord::getPhoneNumber, phoneNumber)
//                .eq(NumberRecord::getUserId, user.getId())
//                .orderByDesc(NumberRecord::getGetNumberTime)
//                .last("LIMIT 1"));
//
//        if (record == null) return CommonResultDTO.error(Constants.ERROR_NO_CODE, "无验证码记录");
//
//        switch (record.getStatus()) {
//            case 2: // 成功
//                return CommonResultDTO.success("验证码获取成功", record.getCode());
//            case 3: // 超时
//                return CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码获取超时/失败");
//            case 4: // 号码无效
//                return CommonResultDTO.error(Constants.ERROR_NO_CODE, "该号码无效，请重新取号");
//            default: // 0, 1
//                return CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码尚未获取，请稍后重试");
//        }
//    }

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
        if (user == null) {
            return null;
        }

        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NumberRecord::getUserId, user.getId());
        addCommonFilters(wrapper, statusFilter, startTime, endTime, null, null, null, null, null);

        // ------------------- 核心修改逻辑开始 -------------------

        final long MAX_RECORDS_LIMIT = 50;

        // 1. 先查询符合条件的总记录数 (真实总数)
        //    使用 wrapper.clone() 避免影响后续的查询
        long actualTotal = this.count(wrapper.clone());

        // 2. 确定最终要告诉前端的总数是多少 (不能超过50)
        long effectiveTotal = Math.min(actualTotal, MAX_RECORDS_LIMIT);

        // 3. 检查用户请求的页是否超出了我们的限制范围
        //    page.offset() 会计算出起始记录的索引，例如 page=2, size=10, offset=10
        if (page.offset() >= MAX_RECORDS_LIMIT) {
            // 如果请求的起始位置已经超过或等于50，直接返回一个空的分页结果
            // 但需要设置正确的总数，以便前端分页组件显示正确
            IPage<NumberRecord> resultPage = new Page<>(page.getCurrent(), page.getSize(), effectiveTotal);
            resultPage.setRecords(java.util.Collections.emptyList()); // 设置记录为空列表
            return resultPage.convert(this::convertToDTO);
        }

        // 4. 如果请求的页面大小会跨越50条的界限，需要调整查询的size
        //    例如：offset=40, size=20 (想查第41-60条)，我们只让他查第41-50条 (size=10)
        long remainingRecords = MAX_RECORDS_LIMIT - page.offset();
        if (page.getSize() > remainingRecords) {
            page.setSize(remainingRecords);
        }

        // 5. 执行正常的分页查询，此时page对象的参数已经是安全且调整过的
        IPage<NumberRecord> recordPage = this.page(page, wrapper);

        // 6. 最后，修正分页结果中的总数，确保前端拿到的总数不会超过50
        recordPage.setTotal(effectiveTotal);

        // ------------------- 核心修改逻辑结束 -------------------

        return recordPage.convert(this::convertToDTO);
    }

    @Override
    public IPage<NumberRecord> listAllNumbers(Integer statusFilter, Date startTime, Date endTime,
                                              Long userId, String projectId, String phoneNumber, Integer charged,
                                              IPage<NumberRecord> page, String lineId, String userName) {

        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();

        // 使用标准Java方法判断 userName 是否有效
        if (userName != null && !userName.trim().isEmpty()) {

            // 根据userName模糊查询匹配的用户ID列表
            List<Long> subUserIds = userService.list(new LambdaQueryWrapper<User>().like(User::getUserName, userName))
                    .stream().map(User::getId).toList();

            // 优化：如果根据userName没有找到任何用户，直接返回空结果集
            if (subUserIds.isEmpty()) {
                return page.setTotal(0).setRecords(Collections.emptyList());
            }

            // 将找到的用户ID作为筛选条件
            // 假设NumberRecord实体中有关联用户ID的字段，例如叫 userId
            wrapper.in(NumberRecord::getUserId, subUserIds);
        }

        // 添加其他通用的筛选条件
        addCommonFilters(wrapper, statusFilter, startTime, endTime, userId, projectId, phoneNumber, charged, lineId);

        // 执行分页查询并返回
        return this.page(page, wrapper);
    }


    @Override
    public NumberRecord getRecordByPhone(String phone) {
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NumberRecord::getPhoneNumber,phone);
        NumberRecord numberRecord = this.getOne(wrapper);

//        smsApiService.getVerificationCode()
        return numberRecord;
    }

    // --- 私有辅助方法 ---

    private boolean hasOngoingRecord(Long userId) {
        return this.count(new LambdaQueryWrapper<NumberRecord>()
                .eq(NumberRecord::getUserId, userId)
                .in(NumberRecord::getStatus, 0, 1)) > 0;
    }

    private BigDecimal getUserPriceForProject(User user, String projectId, Integer lineId, BigDecimal defaultPrice) {
        try {
            Map<String, BigDecimal> prices = new ObjectMapper().readValue(user.getProjectPrices(), new TypeReference<>() {});
            return prices.getOrDefault(projectId + "-" + lineId, defaultPrice);
        } catch (Exception e) {
            return defaultPrice;
        }
    }

    /**
     * 【核心修改】扩展 addCommonFilters 方法以支持更多查询条件
     * 我们将新参数添加到这个方法中，而不是直接在 listAllNumbers 中堆砌 wrapper.eq() 调用，
     * 这样 listUserNumbersByUSerName 等其他方法如果需要也可以复用这部分逻辑。
     */
    private void addCommonFilters(LambdaQueryWrapper<NumberRecord> wrapper, Integer status, Date start, Date end,
                                  Long userId, String projectId, String phoneNumber, Integer charged,String lineId) {
        // --- 原有逻辑 ---
        wrapper.eq(status != null, NumberRecord::getStatus, status);
        wrapper.ge(start != null, NumberRecord::getGetNumberTime, start);
        wrapper.le(end != null, NumberRecord::getGetNumberTime, end);

        // --- 新增的过滤逻辑 ---
        // 1. 按用户ID过滤
        wrapper.eq(userId != null, NumberRecord::getUserId, userId);

        // 2. 按项目ID过滤 (使用 StringUtils.hasText 判断非空，更严谨)
        wrapper.eq(StringUtils.hasText(projectId), NumberRecord::getProjectId, projectId);

        wrapper.eq(StringUtils.hasText(lineId), NumberRecord::getLineId, lineId);

        // 3. 按手机号模糊查询 (LIKE %phoneNumber%)
        wrapper.like(StringUtils.hasText(phoneNumber), NumberRecord::getPhoneNumber, phoneNumber);

        // 4. 按扣费状态过滤
        wrapper.eq(charged != null, NumberRecord::getCharged, charged);

        // 排序逻辑保持不变
        wrapper.orderByDesc(NumberRecord::getGetNumberTime);
    }

    private NumberDTO convertToDTO(NumberRecord record) {
        NumberDTO dto = new NumberDTO();
        BeanUtils.copyProperties(record, dto);
        return dto;
    }


    /**
     * 根据项目ID查询所有关联的手机号码
     *
     * @param projectId 项目的唯一标识
     * @return 包含该项目所有手机号的字符串列表
     */
    @Override
    public List<String> getPhoneNumbersByProjectId(String projectId) {
        // 1. 创建查询条件构造器
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(NumberRecord::getPhoneNumber);
        wrapper.eq(NumberRecord::getProjectId, projectId);
        List<NumberRecord> records = this.list(wrapper);
        return records.stream()
                .map(NumberRecord::getPhoneNumber)
                .collect(java.util.stream.Collectors.toList());
    }


    /**
     * 主入口方法，移除了所有 username 相关的逻辑。
     */
    @Override
    public IPage<ProjectStatisticsDTO> getStatisticsReport(Long operatorId, StatisticsQueryDTO queryDTO) {
        User operator = new User();
        if (operatorId != 0L) {
            operator = userService.getById(operatorId);
            if (operator == null) {
                throw new BusinessException("操作用户不存在");
            }
        }

        // 管理员逻辑
        if (operatorId == 0L) {
            log.info("Generating statistics report for admin user: {}", operatorId);
            return generateAdminStatistics(queryDTO);
        }
        // 代理逻辑
        else if (operator.getIsAgent() == 1) {
            log.info("Generating statistics report for agent user: {}", operatorId);
            // 代理的报表查询不再需要特殊处理用户名，筛选条件统一在 applyFilters 中处理
            return generateAgentStatistics(operatorId, queryDTO);
        }
        // 其他普通用户无权限
        else {
            log.warn("User {} is not an admin or agent, no permission to view statistics.", operatorId);
            return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        }
    }

    /**
     * 为管理员生成全站统计报表（支持分页和筛选）。
     * 此方法本身无需修改，因为筛选逻辑已封装到 applyFilters 中。
     */
    private IPage<ProjectStatisticsDTO> generateAdminStatistics(StatisticsQueryDTO queryDTO) {
        // 1. 构建筛选条件
        QueryWrapper<NumberRecord> wrapper = new QueryWrapper<>();
        applyFilters(wrapper, queryDTO); // 统一应用筛选逻辑


        // 2. 构建聚合查询
        wrapper.groupBy("project_id", "line_id");
        wrapper.select(
                "project_id",
                "line_id",
                "COUNT(id) as totalRequests",
                "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as successCount",
                "COALESCE(SUM(CASE WHEN charged = 1 THEN price ELSE 0 END), 0) as totalRevenue",
                "COALESCE(SUM(CASE WHEN charged = 1 THEN cost_price ELSE 0 END), 0) as totalCost"
        );

        // 3. 执行分页聚合查询
        Page<Map<String, Object>> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        IPage<Map<String, Object>> aggregatedDataPage = this.pageMaps(page, wrapper);

        if (aggregatedDataPage.getRecords().isEmpty()) {
            return new Page<ProjectStatisticsDTO>(queryDTO.getCurrent(), queryDTO.getSize(), aggregatedDataPage.getTotal());
        }

        // 4. 获取所有项目名称用于数据填充
        Map<String, String> projectNames = projectService.list().stream()
                .collect(Collectors.toMap(Project::getProjectId, Project::getProjectName, (p1, p2) -> p1));

        // 5. 处理聚合结果并构建 DTO
        List<ProjectStatisticsDTO> dtoList = processAggregatedData(aggregatedDataPage.getRecords(), null, projectNames);

        // 6. 构造并返回最终的 IPage<ProjectStatisticsDTO>
        IPage<ProjectStatisticsDTO> resultPage = new Page<>(aggregatedDataPage.getCurrent(), aggregatedDataPage.getSize(), aggregatedDataPage.getTotal());
        resultPage.setRecords(dtoList);

        return resultPage;
    }

    /**
     * 为代理生成其下级的统计报表（支持分页和筛选）。
     * 移除了根据 username 筛选下级用户的逻辑。
     */
    private IPage<ProjectStatisticsDTO> generateAgentStatistics(Long agentId, StatisticsQueryDTO queryDTO) {
        // 1. 获取该代理的 *所有* 下级用户ID
        List<Long> subUserIds = userService.list(new LambdaQueryWrapper<User>().eq(User::getParentId, agentId))
                .stream().map(User::getId).collect(Collectors.toList());

        if (subUserIds.isEmpty()) {
            return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        }

        // 2. 获取代理本人所有线路的拿货价（成本）
        Map<String, BigDecimal> agentCostMap = userProjectLineService.list(new LambdaQueryWrapper<UserProjectLine>().eq(UserProjectLine::getUserId, agentId))
                .stream()
                .collect(Collectors.toMap(
                        line -> line.getProjectId() + "-" + line.getLineId(),
                        UserProjectLine::getAgentPrice
                ));

        // 3. 构建筛选和聚合查询
        QueryWrapper<NumberRecord> wrapper = new QueryWrapper<>();
        wrapper.in("user_id", subUserIds);
        applyFilters(wrapper, queryDTO); // 应用通用筛选（包含项目名等）

        // ... (后续聚合、分页、处理数据的代码与之前完全相同)

        wrapper.groupBy("project_id", "line_id");
        wrapper.select(
                "project_id",
                "line_id",
                "COUNT(id) as totalRequests",
                "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as successCount",
                "COALESCE(SUM(CASE WHEN charged = 1 THEN price ELSE 0 END), 0) as totalRevenue"
        );

        // 4. 执行分页聚合查询
        Page<Map<String, Object>> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        IPage<Map<String, Object>> aggregatedDataPage = this.pageMaps(page, wrapper);

        if (aggregatedDataPage.getRecords().isEmpty()) {
            return new Page<ProjectStatisticsDTO>(queryDTO.getCurrent(), queryDTO.getSize(), aggregatedDataPage.getTotal());
        }

        // 5. 获取项目名称
        Map<String, String> projectNames = projectService.list().stream()
                .collect(Collectors.toMap(Project::getProjectId, Project::getProjectName, (p1, p2) -> p1));

        // 6. 处理聚合结果
        List<ProjectStatisticsDTO> dtoList = processAggregatedData(aggregatedDataPage.getRecords(), agentCostMap, projectNames);

        // 7. 构造并返回分页结果
        IPage<ProjectStatisticsDTO> resultPage = new Page<>(aggregatedDataPage.getCurrent(), aggregatedDataPage.getSize(), aggregatedDataPage.getTotal());
        resultPage.setRecords(dtoList);

        return resultPage;
    }

    /**
     * 【核心修改】通用的筛选条件应用方法。
     * 替换了按用户名筛选的逻辑为按项目名筛选。
     *
     * @param wrapper QueryWrapper 实例
     * @param queryDTO 筛选参数
     */
    private void applyFilters(QueryWrapper<NumberRecord> wrapper, StatisticsQueryDTO queryDTO) {
        // 按项目名筛选 (模糊查询)
        if (StringUtils.hasText(queryDTO.getProjectName())) {
            // 1. 根据项目名（模糊）查询出所有匹配的项目ID
            List<String> projectIds = projectService.list(
                    new LambdaQueryWrapper<Project>().like(Project::getProjectName, queryDTO.getProjectName())
            ).stream().map(Project::getProjectId).collect(Collectors.toList());

            if (projectIds.isEmpty()) {
                // 2. 如果没有项目匹配，为保证查询结果为空，添加一个永远为假的条件
                wrapper.eq("1", 0);
                return; // 后续条件无需再拼接
            }
            // 3. 将匹配到的项目ID列表加入查询条件
            wrapper.in("project_id", projectIds);
        }

        // 按项目ID筛选 (精确查询)
        // 这个条件可以和项目名筛选同时生效，提供更精确的过滤
        if (StringUtils.hasText(queryDTO.getProjectId())) {
            wrapper.eq("project_id", queryDTO.getProjectId());
        }
        // 按线路ID筛选
        if (queryDTO.getLineId() != null) {
            wrapper.eq("line_id", queryDTO.getLineId());
        }
        LocalDateTime startTime = parseAndAdjustDateTime(queryDTO.getStartTime(), false);
        if (startTime != null) {
            wrapper.ge("get_number_time", startTime);
        }

        LocalDateTime endTime = parseAndAdjustDateTime(queryDTO.getEndTime(), true);
        if (endTime != null) {
            wrapper.le("get_number_time", endTime);
        }
    }

    /**
     * 解析并调整日期时间字符串。
     * @param dateTimeStr 前端传入的日期时间字符串
     * @param isEndDate   标记这是否是一个结束日期，用于处理仅日期的情况
     * @return 格式化后的 LocalDateTime 对象，如果格式错误则返回 null
     */
    private LocalDateTime parseAndAdjustDateTime(String dateTimeStr, boolean isEndDate) {
        if (!StringUtils.hasText(dateTimeStr)) {
            return null;
        }

        try {
            // 新增：优先处理 ISO 8601 格式 (带 'T' 和 'Z')
            if (dateTimeStr.contains("T") && dateTimeStr.endsWith("Z")) {
                // ZonedDateTime 可以正确解析包含时区信息的 ISO 字符串
                // toLocalDateTime() 会将其转换为系统默认时区的本地时间
                // 如果你的服务器时区不是期望的，需要进一步处理，例如：
                // return ZonedDateTime.parse(dateTimeStr).withZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
                return ZonedDateTime.parse(dateTimeStr).toLocalDateTime();
            }
            // 情况一：字符串包含空格，说明带有时间部分
            else if (dateTimeStr.contains(" ")) {
                String standardizedStr = dateTimeStr;
                // 如果格式是 "yyyy-MM-dd HH:mm"，补上秒
                if (standardizedStr.length() == 16) {
                    standardizedStr += ":00";
                }
                // 原有逻辑，将空格替换为'T'以符合LocalDateTime的默认格式
                return LocalDateTime.parse(standardizedStr.replace(" ", "T"));
            }
            // 情况二：字符串不含空格，说明只有日期部分
            else {
                LocalDate date = LocalDate.parse(dateTimeStr);
                if (isEndDate) {
                    // 如果是结束日期，则设置为当天的 23:59:59
                    return date.atTime(23, 59, 59);
                } else {
                    // 如果是开始日期，则设置为当天的 00:00:00
                    return date.atStartOfDay();
                }
            }
        } catch (DateTimeParseException e) {
            log.warn("无法解析的日期时间格式: '{}'。该筛选条件将被忽略。", dateTimeStr, e);
            return null;
        }
    }



    /**
     * 【无需修改】通用的聚合数据处理方法
     */
    private List<ProjectStatisticsDTO> processAggregatedData(List<Map<String, Object>> aggregatedData, Map<String, BigDecimal> agentCostMap, Map<String, String> projectNames) {

        Map<String, ProjectStatisticsDTO> projectReportMap = new LinkedHashMap<>();

        for (Map<String, Object> lineData : aggregatedData) {
            String projectId = (String) lineData.get("project_id");
            Object lineIdObj = lineData.get("line_id");

            if (projectId == null || lineIdObj == null) {
                log.warn("Skipping aggregated data row with null projectId or lineId.");
                continue;
            }
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
                lineTotalCost = new BigDecimal(lineData.get("totalCost").toString());
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


    /**
     * 为代理查询其下级用户的取号记录
     *
     * @param agentId  当前操作的代理ID
     * @param queryDTO 查询条件
     * @return 分页后的记录
     */
    @Override
    public IPage<NumberDTO> listSubordinateRecordsForAgent(Long agentId, SubordinateNumberRecordQueryDTO queryDTO) {
        List<Long> targetUserIds = new ArrayList<>();

        // 步骤 1: 根据 userName 筛选条件，确定要查询的用户ID范围
        if (StringUtils.hasText(queryDTO.getUserName())) {
            // 如果指定了用户名，则精确查找该用户
            User targetUser = userService.getOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUserName, queryDTO.getUserName()));

            // 安全校验：确保该用户存在，并且是当前代理的直接下级
            if (targetUser != null && agentId.equals(targetUser.getParentId())) {
                targetUserIds.add(targetUser.getId());
            } else {
                // 如果用户不存在或不属于该代理，直接返回空结果，防止信息泄露
                return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
            }
        } else {
            // 如果未指定用户名，则查询该代理的所有下级用户ID
            List<User> subUsers = userService.list(new LambdaQueryWrapper<User>()
                    .select(User::getId) // 优化查询，只获取ID字段
                    .eq(User::getParentId, agentId));
            if (subUsers.isEmpty()) {
                // 如果代理没有任何下级，也返回空结果
                return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
            }
            targetUserIds = subUsers.stream().map(User::getId).collect(Collectors.toList());
        }

        // 步骤 2: 构建对 NumberRecord 表的查询条件
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();

        // 核心条件：只查询目标用户ID列表内的记录
        wrapper.in(NumberRecord::getUserId, targetUserIds);

        // 步骤 3: 处理项目名称筛选
        if (StringUtils.hasText(queryDTO.getProjectName())) {
            // 根据项目名称模糊查询，获取匹配的项目ID列表
            List<String> projectIds = projectService.list(
                    new LambdaQueryWrapper<Project>().like(Project::getProjectName, queryDTO.getProjectName())
            ).stream().map(Project::getProjectId).collect(Collectors.toList());

            if (projectIds.isEmpty()) {
                // 如果没有项目匹配，直接返回空，因为不可能有符合条件的记录
                return new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
            }
            // 将匹配到的项目ID列表加入查询条件
            wrapper.in(NumberRecord::getProjectId, projectIds);
        }

        // 步骤 4: 应用其他筛选条件
        wrapper.eq(StringUtils.hasText(queryDTO.getProjectId()), NumberRecord::getProjectId, queryDTO.getProjectId());
        wrapper.eq(queryDTO.getLineId() != null, NumberRecord::getLineId, queryDTO.getLineId());
        wrapper.like(StringUtils.hasText(queryDTO.getPhoneNumber()), NumberRecord::getPhoneNumber, queryDTO.getPhoneNumber());
        wrapper.eq(queryDTO.getStatus() != null, NumberRecord::getStatus, queryDTO.getStatus());
        wrapper.eq(queryDTO.getCharged() != null, NumberRecord::getCharged, queryDTO.getCharged());
        // 使用 parseAndAdjustDateTime 方法处理字符串类型的日期时间
        LocalDateTime startTime = parseAndAdjustDateTime(queryDTO.getStartTime(), false);
        LocalDateTime endTime = parseAndAdjustDateTime(queryDTO.getEndTime(), true);

        wrapper.ge(startTime != null, NumberRecord::getGetNumberTime, startTime);
        wrapper.le(endTime != null, NumberRecord::getGetNumberTime, endTime);
        // =======================================================

        // 步骤 5: 设置排序规则
        wrapper.orderByDesc(NumberRecord::getGetNumberTime);

        // 步骤 6: 执行分页查询
        Page<NumberRecord> pageRequest = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        IPage<NumberRecord> recordPage = this.page(pageRequest, wrapper);

        // 步骤 7: 将查询结果 (IPage<NumberRecord>) 转换为 DTO (IPage<NumberDTO>)
        return recordPage.convert(this::convertToDTO);
    }

}