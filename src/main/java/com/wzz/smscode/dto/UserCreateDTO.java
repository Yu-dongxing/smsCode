package com.wzz.smscode.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 用户创建/编辑 DTO
 */
@Data
public class UserCreateDTO {

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
     * 项目价格配置，为新用户设置各项目价格
     */
    private Map<String, BigDecimal> projectPrices;

    /**
     * 是否赋予代理权限
     */
    private Boolean isAgent;

    /**
     * 上级用户ID（代理ID或管理员ID）
     */
    private Long parentId;
}