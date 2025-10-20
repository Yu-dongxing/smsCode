package com.wzz.smscode.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 项目价格配置 DTO
 */
@Data
public  class ProjectPriceDTO {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 线路ID
     */
    private Long lineId;

    /**
     * 价格
     */
    private BigDecimal price;
}