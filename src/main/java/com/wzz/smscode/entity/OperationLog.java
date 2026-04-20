package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.ColumnType;
import com.wzz.smscode.annotation.Index;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 操作日志实体类
 * 用于审计系统中管理员及代理的关键操作行为，如用户创建、充值、删除等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
@TableComment("操作日志表")
@Index(name = "idx_operation_time", columns = {"operation_time"}, comment = "操作时间索引")
@Index(name = "idx_operation_type", columns = {"operation_type"}, comment = "操作类型索引")
@Index(name = "idx_operator_id", columns = {"operator_id"}, comment = "操作人ID索引")
@Index(name = "idx_target_user_id", columns = {"target_user_id"}, comment = "目标用户ID索引")
public class OperationLog extends BaseEntity {

    /**
     * 操作人ID (管理员统一为 0)
     */
    @ColumnComment("操作人ID")
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 操作人名称快照
     */
    @ColumnComment("操作人名称")
    @TableField("operator_name")
    private String operatorName;

    /**
     * 操作人类型 (ADMIN-管理员, AGENT-代理)
     */
    @ColumnComment("操作人类型")
    @TableField("operator_type")
    private String operatorType;

    /**
     * 操作类型 (CREATE_USER-创建用户, RECHARGE_USER-充值用户, DELETE_USER-删除用户)
     */
    @ColumnComment("操作类型")
    @TableField("operation_type")
    private String operationType;

    /**
     * 目标用户ID (逻辑关联，不设强物理外键以防删除用户导致日志丢失)
     */
    @ColumnComment("目标用户ID")
    @TableField("target_user_id")
    private Long targetUserId;

    /**
     * 目标用户名称快照 (用户被物理删除后，此处名称仍可用于追溯)
     */
    @ColumnComment("目标用户名称")
    @TableField("target_user_name")
    private String targetUserName;

    /**
     * 涉及金额 (主要针对充值操作，创建/删除可为空)
     */
    @ColumnComment("操作金额")
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 操作前的用户余额
     */
    @ColumnComment("变动前余额")
    @TableField("balance_before")
    private BigDecimal balanceBefore;

    /**
     * 操作后的用户余额
     */
    @ColumnComment("变动后余额")
    @TableField("balance_after")
    private BigDecimal balanceAfter;

    /**
     * 操作结果 (SUCCESS-成功, FAIL-失败)
     */
    @ColumnComment("操作结果")
    @TableField("result")
    private String result;

    /**
     * 操作备注或失败原因详情
     */
    @ColumnComment("备注")
    @TableField("remark")
    @ColumnType("TEXT")
    private String remark;

    /**
     * 操作发生的具体时间
     */
    @ColumnComment("操作时间")
    @TableField("operation_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operationTime;

    /**
     * 操作人的 IP 地址
     */
    @ColumnComment("IP地址")
    @TableField("ip")
    private String ip;

    /**
     * 操作人的浏览器或客户端 UserAgent 信息
     */
    @ColumnComment("用户代理信息")
    @TableField("user_agent")
    @ColumnType("TEXT")
    private String userAgent;
}