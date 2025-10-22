package com.wzz.smscode.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnumDTO {

    /**
     * 字段值
     */
    private String value;

    /**
     * 字段描述
     */
    private String description;
}