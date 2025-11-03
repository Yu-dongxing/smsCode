package com.wzz.smscode.dto;


import lombok.Data;
import java.util.List;

@Data
public class PriceTemplateCreateDTO {
    private String name;
    private List<PriceTemplateItemDTO> items;
}