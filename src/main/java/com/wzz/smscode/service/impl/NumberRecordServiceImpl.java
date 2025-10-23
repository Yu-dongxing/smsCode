package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.common.CommonResultDTO;
import com.wzz.smscode.dto.CodeResult;
import com.wzz.smscode.dto.NumberDTO;
import com.wzz.smscode.entity.NumberRecord;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.SystemConfig;
import com.wzz.smscode.entity.User;
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
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class NumberRecordServiceImpl extends ServiceImpl<NumberRecordMapper, NumberRecord> implements NumberRecordService {

    @Autowired private UserService userService;
    @Autowired private ProjectService projectService;
    @Autowired private UserLedgerService ledgerService;


    @Lazy @Autowired private NumberRecordService self;

    @Autowired
    private SystemConfigService systemConfigService;

    @Override
    public CommonResultDTO<String> getNumber(Long userId, String password, String projectId, Integer lineId) {
        return null;
    }

    // 2. 注入我们统一的API服务
    @Autowired private SmsApiService smsApiService;

    @Transactional
    @Override
    public CommonResultDTO<String> getNumber(String userName, String password, String projectId, Integer lineId) {
        // 1. 身份验证
        User user = userService.authenticateUserByUserName(userName, password);
        if (user == null) return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户验证失败");

        // ... 省略其他业务校验代码，保持不变 ...
        SystemConfig config = systemConfigService.getConfig();
        if (user.getStatus() != 0) return CommonResultDTO.error(-5, "用户已被禁用");
        if(config.getEnableBanMode()==1 && user.getDailyCodeRate() < config.getMin24hCodeRate()) {
            return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR,"回码率过低封禁");
        }
        Project project = projectService.getProject(projectId, lineId);
        if (project == null) return CommonResultDTO.error(-5, "项目线路不存在");
        BigDecimal price = getUserPriceForProject(user, projectId, lineId, project.getCostPrice());
        if (user.getBalance().compareTo(price) < 0) {
            return CommonResultDTO.error(Constants.ERROR_INSUFFICIENT_BALANCE, "余额不足");
        }
        boolean hasOngoingRecord = this.hasOngoingRecord(user.getId());
        if (!BalanceUtil.canGetNumber(user, hasOngoingRecord)) {
            return CommonResultDTO.error(Constants.ERROR_INSUFFICIENT_BALANCE, "余额不足或已有进行中的任务");
        }

        // 3. 【核心改造】调用统一服务获取号码或操作ID
        Map<String,String>  identifier;
        try {
            String numForApi = "1";
             identifier = smsApiService.getPhoneNumber(project, numForApi);
             log.info("获取到手机号的信息：{}",identifier);
            if (!StringUtils.hasText(identifier.get("phone"))) {
                return CommonResultDTO.error(Constants.ERROR_NO_NUMBER, "未获取到有效号码或ID");
            }
        } catch (BusinessException e) {
            log.error("调用统一接口服务获取号码失败: projectId={}, lineId={}. Error: {}", projectId, lineId, e.getMessage());
            // 将底层的详细错误信息返回，方便排查
            return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "接口错误: " + e.getMessage());
        }

        // 4. 写入号码记录并启动取码线程
        NumberRecord record = new NumberRecord();
        record.setUserId(user.getId());
        record.setProjectId(projectId);
        record.setLineId(lineId);
        // 注意：这里存入的是 identifier，它可能是手机号，也可能是一个操作ID(UUID)
        record.setPhoneNumber(identifier.get("phone"));
        record.setApiPhoneId(identifier.get("id"));
        record.setStatus(0); // 待取码
        record.setCharged(1); // 默认未扣费
        record.setPrice(price);
        record.setBalanceBefore(user.getBalance());
        record.setBalanceAfter(user.getBalance());
        record.setGetNumberTime(LocalDateTime.now());
        this.save(record);

        // 5. 异步启动取码任务，并将 identifier 传递过去，避免二次查询
//        self.retrieveCode(record.getId(), identifier.get("id"));
        // 5. 【核心优化】注册一个事务成功提交后的回调，来安全地启动异步取码任务
        final String finalIdentifierId = project.getResponsePhoneField() != null
                ? identifier.get("id")
                : identifier.get("phone");

        final Long recordId = record.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                log.info("事务已提交，开始为记录ID {} 异步获取验证码...", recordId);
                self.retrieveCode(recordId, finalIdentifierId);
            }
        });

        // 6. 更新统计
        userService.updateUserStatsForNewNumber(user.getId(), false);

        // 7. 返回操作ID或部分号码给用户
        return CommonResultDTO.success("取号成功，请稍后查询验证码", identifier.get("phone"));
    }

    @Override
    public void retrieveCode(Long numberId) {
        return;
    }

    @Async("taskExecutor")
    @Override
    public void retrieveCode(Long numberId, String identifier) { // 接收 identifier
        NumberRecord record = this.getById(numberId);
        if (record == null || record.getStatus() != 0) {
            log.info("号码记录 {} 无需取码，状态: {}", numberId, record != null ? record.getStatus() : "null");
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
            result = String.valueOf(CodeResult.failure()); // 视作失败
            record.setRemark(e.getMessage());

        }

        // 9. 更新最终状态和执行扣费
        updateRecordAfterRetrieval(record, result);
    }

    @Transactional
    @Override
    public void updateRecordAfterRetrieval(NumberRecord record, CodeResult result) {
        NumberRecord latestRecord = this.getById(record.getId());
        if (latestRecord.getStatus() != 1) return; // 防止重复处理

        latestRecord.setCodeReceivedTime(LocalDateTime.now());

        if (result.isSuccess()) {
            // 成功获取验证码
            latestRecord.setStatus(2);
            latestRecord.setCode(result.getCode());
            latestRecord.setCharged(0); // 标记为已扣费

            // 【关键】如果API返回了手机号（例如UUID查询场景），则更新记录中的手机号
            if (result.getPhoneNumber() != null && !result.getPhoneNumber().equals(latestRecord.getPhoneNumber())) {
                log.info("回填手机号 for record {}: old={}, new={}", latestRecord.getId(), latestRecord.getPhoneNumber(), result.getPhoneNumber());
                latestRecord.setPhoneNumber(result.getPhoneNumber());
            }

            // 执行扣费
            User user = userService.getById(latestRecord.getUserId());
            BigDecimal newBalance = user.getBalance().subtract(latestRecord.getPrice());
            latestRecord.setBalanceAfter(newBalance);
            userService.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getBalance, newBalance)
                    .eq(User::getId, user.getId()));

            ledgerService.createLedgerEntry(user.getId(), FundType.BUSINESS_DEDUCTION, latestRecord.getPrice().negate(), newBalance, "业务扣费");
            userService.updateUserStatsForNewNumber(user.getId(), true);
        } else {
            // 超时或失败
            latestRecord.setStatus(3);
        }

        this.updateById(latestRecord);
    }

    // ... 其他方法保持不变 ...
    @Override
    public CommonResultDTO<String> getCode(String userName, String password, String identifier) {
        User user =  userService.authenticateUserByUserName(userName, password);
        if ( user == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户验证失败");
        }

        // 使用 identifier 查询，可以是手机号或UUID
        NumberRecord record = this.getOne(new LambdaQueryWrapper<NumberRecord>()
                .eq(NumberRecord::getPhoneNumber, identifier) // phoneNumber 字段现在存储 identifier
                .eq(NumberRecord::getUserId, user.getId())
                .orderByDesc(NumberRecord::getGetNumberTime)
                .last("LIMIT 1"));

        if (record == null) return CommonResultDTO.error(Constants.ERROR_NO_CODE, "无验证码记录");

        switch (record.getStatus()) {
            case 2:
                return CommonResultDTO.success("验证码获取成功", record.getCode());
            case 3:
                return CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码获取超时/失败");
            case 4:
                return CommonResultDTO.error(Constants.ERROR_NO_CODE, "该号码无效，请重新取号");
            default:
                return CommonResultDTO.error(Constants.ERROR_NO_CODE, "验证码尚未获取，请稍后重试");
        }
    }
//    @Override
//    public CommonResultDTO<String> getCode(Long userId, String password, String phoneNumber) {
//        return null;
//    }

    @Transactional
    public void updateRecordAfterRetrieval(NumberRecord record, String code) {
        // 重新从数据库获取记录，确保数据最新
        NumberRecord latestRecord = this.getById(record.getId());
        if (latestRecord.getStatus() != 1) return; // 防止重复处理

        latestRecord.setCodeReceivedTime(LocalDateTime.now());

        if (code != null) {
            // 成功获取验证码
            latestRecord.setStatus(2);
            latestRecord.setCode(code);
            latestRecord.setCharged(0);

            // 执行扣费
            User user = userService.getById(latestRecord.getUserId());
            BigDecimal newBalance = user.getBalance().subtract(latestRecord.getPrice());
            latestRecord.setBalanceAfter(newBalance);

            userService.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getBalance, newBalance)
                    .eq(User::getId, user.getId()));



            // 新增账本记录
            ledgerService.createLedgerEntry(user.getId(), FundType.BUSINESS_DEDUCTION, latestRecord.getPrice().negate(), newBalance, "业务扣费");

            // 更新用户成功回码统计
            userService.updateUserStatsForNewNumber(user.getId(), true);
        } else {
            // 超时未获取
            latestRecord.setStatus(3);
        }
        latestRecord.setRemark(record.getRemark());

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
        addCommonFilters(wrapper, statusFilter, startTime, endTime);

        IPage<NumberRecord> recordPage = this.page(page, wrapper);
        return recordPage.convert(this::convertToDTO);
    }
    @Override
    public IPage<NumberDTO> listUserNumbersByUSerName(String userName, String password, Integer statusFilter, Date startTime, Date endTime, IPage<NumberRecord> page) {
        User user =  userService.authenticateUserByUserName(userName, password);
        if ( user== null) return null;
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NumberRecord::getUserId, user.getId());
        addCommonFilters(wrapper, statusFilter, startTime, endTime);

        IPage<NumberRecord> recordPage = this.page(page, wrapper);
        return recordPage.convert(this::convertToDTO);
    }

    @Override
    public IPage<NumberRecord> listAllNumbers(Integer statusFilter, Date startTime, Date endTime, IPage<NumberRecord> page) {
        // TODO: 添加管理员权限校验
        LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<>();
        addCommonFilters(wrapper, statusFilter, startTime, endTime);
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

    private void addCommonFilters(LambdaQueryWrapper<NumberRecord> wrapper, Integer status, Date start, Date end) {
        wrapper.eq(status != null, NumberRecord::getStatus, status);
        wrapper.ge(start != null, NumberRecord::getGetNumberTime, start);
        wrapper.le(end != null, NumberRecord::getGetNumberTime, end);
        wrapper.orderByDesc(NumberRecord::getGetNumberTime);
    }

    private NumberDTO convertToDTO(NumberRecord record) {
        NumberDTO dto = new NumberDTO();
        BeanUtils.copyProperties(record, dto);
        return dto;
    }
}