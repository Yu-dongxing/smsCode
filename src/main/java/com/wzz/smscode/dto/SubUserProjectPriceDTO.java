package com.wzz.smscode.dto;

import lombok.Data;
import java.util.List;

@Data
public class SubUserProjectPriceDTO {
    private Long userId;
    private String userName;
    private List<ProjectPriceInfoDTO> projectPrices;
}