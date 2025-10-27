package com.wzz.smscode.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProjectPriceInfoDTO {

    /**
     * 项目配置表id
     */
    private Long id;

    /**
     * 用户项目线路配置表id
     */
    private Long userProjectLineTableId;
    /**
     * 项目名
     */
    private String projectName;
    /**
     *项目id
     */
    private String projectId;
    /**
     * 线路id
     */
    private String lineId;
    /**
     * 项目价格
     */
    private BigDecimal price;
    /**
     * 项目成本价
     */
    private BigDecimal costPrice;

    /**
     * 项目最高价
     */
    private BigDecimal maxPrice;

    /**
     * 项目最低价
     */
    private BigDecimal minPrice;
}
