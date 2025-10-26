package com.wzz.smscode.dto;

import lombok.Data;
import java.util.List;

/**
 * 返回用户项目配置表
 */
@Data
public class SubUserProjectPriceDTO {
    private Long userId;
    private String userName;
    private List<ProjectPriceInfoDTO> projectPrices;
}