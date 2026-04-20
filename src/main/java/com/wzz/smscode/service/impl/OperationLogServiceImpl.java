package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.OperationLogQueryDTO;
import com.wzz.smscode.entity.OperationLog;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.enums.OperationResult;
import com.wzz.smscode.enums.OperationType;
import com.wzz.smscode.enums.OperatorType;
import com.wzz.smscode.mapper.OperationLogMapper;
import com.wzz.smscode.service.OperationLogService;
import com.wzz.smscode.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 操作日志服务实现类
 * 负责审计日志的记录、环境信息抓取（IP/UA）以及后台分页查询过滤。
 */
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    /**
     * 记录操作成功日志
     *
     * @param operationType 操作类型 (CREATE_USER, RECHARGE_USER, DELETE_USER)
     * @param operator      执行操作的用户对象 (若为null或ID为0则记录为管理员)
     * @param targetUser    被操作的目标用户对象 (用于记录操作时刻的快照)
     * @param amount        涉及金额 (充值操作时必填，其他可为null)
     * @param balanceBefore 变动前余额
     * @param balanceAfter  变动后余额
     * @param remark        备注信息
     */
    @Override
    public void recordSuccess(OperationType operationType,
                              User operator,
                              User targetUser,
                              BigDecimal amount,
                              BigDecimal balanceBefore,
                              BigDecimal balanceAfter,
                              String remark) {
        OperationLog operationLog = new OperationLog();

        // 1. 设置操作人信息
        Long operatorId = operator == null ? 0L : operator.getId();
        operationLog.setOperatorId(operatorId);
        operationLog.setOperatorName(resolveOperatorName(operator));
        operationLog.setOperatorType(operatorId != null && operatorId == 0L
                ? OperatorType.ADMIN.getCode()
                : OperatorType.AGENT.getCode());

        // 2. 设置业务类型与目标对象快照
        operationLog.setOperationType(operationType.getCode());
        operationLog.setTargetUserId(targetUser == null ? null : targetUser.getId());
        operationLog.setTargetUserName(targetUser == null ? null : targetUser.getUserName());

        // 3. 设置金额变动明细
        operationLog.setAmount(amount);
        operationLog.setBalanceBefore(balanceBefore);
        operationLog.setBalanceAfter(balanceAfter);

        // 4. 设置执行结果与时间
        operationLog.setResult(OperationResult.SUCCESS.getCode());
        operationLog.setRemark(remark);
        operationLog.setOperationTime(LocalDateTime.now());

        // 5. 抓取请求环境信息 (IP 地址与 User-Agent)
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            operationLog.setIp(IpUtil.getClientIp(request));
            operationLog.setUserAgent(request.getHeader("User-Agent"));
        }

        this.save(operationLog);
    }

    /**
     * 根据条件分页查询操作日志
     *
     * @param queryDTO 查询参数对象，包含分页信息、操作人、目标人、类型及时间范围
     * @return 包含日志记录的分页对象
     */
    @Override
    public IPage<OperationLog> listLogs(OperationLogQueryDTO queryDTO) {
        OperationLogQueryDTO query = queryDTO == null ? new OperationLogQueryDTO() : queryDTO;
        Page<OperationLog> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        // 精确匹配项
        wrapper.eq(query.getOperatorId() != null, OperationLog::getOperatorId, query.getOperatorId());
        wrapper.eq(StringUtils.hasText(query.getOperatorType()), OperationLog::getOperatorType, query.getOperatorType());
        wrapper.eq(query.getTargetUserId() != null, OperationLog::getTargetUserId, query.getTargetUserId());
        wrapper.eq(StringUtils.hasText(query.getOperationType()), OperationLog::getOperationType, query.getOperationType());

        // 模糊匹配项
        wrapper.like(StringUtils.hasText(query.getOperatorName()), OperationLog::getOperatorName, query.getOperatorName());
        wrapper.like(StringUtils.hasText(query.getTargetUserName()), OperationLog::getTargetUserName, query.getTargetUserName());

        // 时间范围过滤
        wrapper.ge(query.getStartTime() != null, OperationLog::getOperationTime, query.getStartTime());
        wrapper.le(query.getEndTime() != null, OperationLog::getOperationTime, query.getEndTime());

        // 默认按操作时间倒序排列
        wrapper.orderByDesc(OperationLog::getOperationTime);

        return this.page(page, wrapper);
    }

    /**
     * 解析并返回操作人名称
     *
     * @param operator 用户实体对象
     * @return 若对象为空或ID为0则返回"admin"，否则返回用户名
     */
    private String resolveOperatorName(User operator) {
        if (operator == null || operator.getId() == null || operator.getId() == 0L) {
            return "admin";
        }
        return operator.getUserName();
    }

    /**
     * 从当前线程上下文中获取 HttpServletRequest
     *
     * @return 当前请求对象，若不在Web上下文则返回null
     */
    private HttpServletRequest getCurrentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}