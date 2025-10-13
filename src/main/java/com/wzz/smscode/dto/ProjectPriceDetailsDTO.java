package com.wzz.smscode.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 项目线路价格详情 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPriceDetailsDTO {
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal costPrice;
}