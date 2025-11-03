package com.wzz.smscode.dto;

import com.wzz.smscode.dto.project.ProjectPriceDTO;
import lombok.Data;
import java.util.List;

/**
 * 为用户新增项目价格配置的请求DTO
 */
@Data
public class AddUserProjectPricesRequestDTO {

    /**
     * 目标用户的ID
     */
    private Long userId;

    /**
     * 要为用户新增的价格配置列表
     */
    private List<ProjectPriceDTO> pricesToAdd;
}