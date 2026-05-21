package com.wzz.smscode.dto;

import lombok.Data;
import java.util.List;

@Data
public class PriceTemplateResponseDTO {
    private Long id; // 模板ID
    private String name; // 模板名称
    private Long creatId;
    private Integer templateSyncStatus;
    private String templateSyncMessage;
    private List<PriceTemplateItemDTO> items; // 项目线路价格配置列表
}
