package com.wzz.smscode.dto;

import lombok.Data;

import java.util.List;

@Data
public class PriceTemplateBatchDeleteDTO {

    private List<Long> ids;

    private List<Long> templateIds;

    public List<Long> getEffectiveIds() {
        return ids != null ? ids : templateIds;
    }
}
