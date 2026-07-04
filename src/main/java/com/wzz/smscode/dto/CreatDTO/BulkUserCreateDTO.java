package com.wzz.smscode.dto.CreatDTO;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BulkUserCreateDTO {
    /**
     * 生成数量
     */
    private Integer count;
    /**
     * 初始余额
     */
    private BigDecimal initialBalance;
    /**
     * 关联的价格模板ID
     */
    private Long templateId;
    /**
     * 归属代理ID (如果不传，管理员操作默认为 0L)
     */
    private Long parentId;
    /**
     * 是否具备代理属性 (0-普通用户, 1-代理)
     */
    private Boolean isAgent;
    /**
     * 默认密码 (如果未指定，可生成与用户名相同的密码，或统一默认)
     */
    private String defaultPassword;
    /**
     * 屏蔽的项目线路黑名单
     */
    private List<String> blacklistedProjects;
}