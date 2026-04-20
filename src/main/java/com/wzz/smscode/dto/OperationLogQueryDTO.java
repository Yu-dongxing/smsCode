package com.wzz.smscode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志分页查询请求对象
 * 用于封装管理员或代理在后台查询日志时的过滤条件
 */
@Data
public class OperationLogQueryDTO {

    /**
     * 操作人名称（支持模糊匹配）
     */
    private String operatorName;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人类型 (ADMIN-管理员, AGENT-代理)
     */
    private String operatorType;

    /**
     * 目标用户名称（支持模糊匹配）
     */
    private String targetUserName;

    /**
     * 目标用户ID
     */
    private Long targetUserId;

    /**
     * 操作类型 (CREATE_USER-创建用户, RECHARGE_USER-充值用户, DELETE_USER-删除用户)
     */
    private String operationType;

    /**
     * 查询起始时间（操作时间大于或等于此值）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 查询截止时间（操作时间小于或等于此值）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 当前页码，默认第1页
     */
    private long page = 1L;

    /**
     * 每页显示记录数，默认10条
     */
    private long size = 10L;
}