package com.wzz.smscode.dto.CreatDTO;

import com.wzz.smscode.dto.project.ProjectPriceDTO; // 确保引入了 ProjectPriceDTO
import lombok.Data;
import java.math.BigDecimal;
import java.util.List; // 引入 List

/**
 * 用户创建/编辑 DTO
 */
@Data
public class UserCreateDTO {

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户ID（编辑时用，新建时可不传）
     */
    private Long userId;

    /**
     * 密码
     */
    private String password;

    /**
     * 初始充值金额
     */
    private BigDecimal initialBalance;

    /**
     * 项目价格配置，为新用户设置各项目价格（改为ProjectPriceDTO列表）
     */
    private List<ProjectPriceDTO> projectPrices; // <-- 这里已修改

    /**
     * 是否赋予代理权限
     */
    private Boolean isAgent;

    /**
     * 上级用户ID（代理ID或管理员ID）
     */
    private Long parentId;
}