package com.wzz.smscode.dto.agent;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 代理商项目价格配置展示 DTO
 */
@Data
public class AgentProjectPriceDTO {

    /**
     * 项目表主键ID (用于后续更新操作)
     */
    private Long projectTableId;

    /**
     * 项目ID (例如 "id0001")
     */
    private String projectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目线路ID
     */
    private String lineId;

    /**
     * 代理的成本价 (你拿货的价格)
     */
    private BigDecimal costPrice;

    /**
     * 系统允许的最高售价
     */
    private BigDecimal priceMax;

    /**
     * 代理当前为下级设置的售价
     */
    private BigDecimal currentAgentPrice;
}