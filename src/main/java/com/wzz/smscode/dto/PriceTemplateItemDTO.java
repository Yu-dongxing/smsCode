package com.wzz.smscode.dto;


import lombok.Data;
import java.math.BigDecimal;

@Data
public class PriceTemplateItemDTO {
    private Long projectId;
    private String projectName;
    private Long lineId;
    private BigDecimal price;
    private BigDecimal costPrice;
}