package com.wzz.smscode.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 代理每日统计数据传输对象
 */
@Data
public class AgentDailyStatsDTO {

    /**
     * 代理ID
     */
    private Long agentId;

    /**
     * 代理名称
     */
    private String agentName;

    /**
     * 跑量下级用户数
     */
    private Long subUserCount;

    /**
     * 总取号量
     */
    private Long totalNumbers;

    /**
     * 成功取码量
     */
    private Long successCount;

    /**
     * 成功率
     */
    private String successRate;

    /**
     * 销售额
     */
    private BigDecimal totalRevenue;

    /**
     * 成本
     */
    private BigDecimal totalCost;

    /**
     * 利润
     */
    private BigDecimal totalProfit;

    /**
     * 计算成功率
     */
    public void calculateRate() {
        if (totalNumbers == null || totalNumbers == 0) {
            this.successRate = "0.00%";
            return;
        }
        BigDecimal rate = BigDecimal.valueOf(successCount == null ? 0 : successCount)
                .divide(BigDecimal.valueOf(totalNumbers), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        this.successRate = rate + "%";
    }
}