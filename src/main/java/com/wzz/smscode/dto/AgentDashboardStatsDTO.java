package com.wzz.smscode.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 代理仪表盘统计数据 DTO
 */
@Data
public class AgentDashboardStatsDTO {

    /**
     * 我的余额
     */
    private BigDecimal myBalance;

    /**
     * 我的下级总人数
     */
    private Long totalSubUsers;

    /**
     * 我的下级所有人今日总充值金额
     */
    private BigDecimal todaySubUsersRecharge;

    /**
     * 我的所有下级的总回码率 (%)
     */
    private Double subUsersCodeRate;
}