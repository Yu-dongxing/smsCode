package com.wzz.smscode.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 项目价格摘要数据传输对象
 */
@Data
public class ProjectPriceSummaryDTO {
    private String projectId;
    private BigDecimal maxPrice;
    private BigDecimal minPrice;
}