package com.wzz.smscode.dto;

import lombok.Data;
import java.util.List;

/**
 * 价格模板 创建/更新 DTO
 */
@Data
public class PriceTemplateCreateDTO {
    /**
     * 模板ID (创建时为空，更新时必填)
     */
    private Long id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 备注 (可选)
     */
    private String remark;

    /**
     * 模板包含的具体项目价格配置
     */
    private List<PriceTemplateItemDTO> items;
}