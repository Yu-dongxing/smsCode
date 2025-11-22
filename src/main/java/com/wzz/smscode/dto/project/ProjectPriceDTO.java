package com.wzz.smscode.dto.project;

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

    /**
     * 状态
     */
    private Boolean status;
}