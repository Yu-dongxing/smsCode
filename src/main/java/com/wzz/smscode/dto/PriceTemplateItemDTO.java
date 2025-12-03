package com.wzz.smscode.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 模板内单项配置 DTO
 */
@Data
public class PriceTemplateItemDTO {

    /**
     * 配置项ID (更新时可传，新建时可空)
     */
    private Long id;

    /**
     * 对应总项目表的主键ID (建议使用 Project表的 ID，而不是字符串的 project_id)
     * 用于后端准确找到对应的项目
     */
    private Long projectTableId;

    /**
     * 项目标识 (如 "tg", "fb") - 冗余字段，方便前端展示
     */
    private String projectId;

    /**
     * 项目名称 - 冗余字段
     */
    private String projectName;

    /**
     * 线路ID
     */
    private Long lineId;

    /**
     * 设置的售价 (必填)
     */
    private BigDecimal price;

    /**
     * 成本价 (可选，用于前端展示当前成本，后端需二次校验)
     */
    private BigDecimal costPrice;

    /**
     * 最低限价 (可选，回显给前端用于校验提示)
     */
    private BigDecimal minPrice;

    /**
     * 最高限价 (可选，回显给前端用于校验提示)
     */
    private BigDecimal maxPrice;
}