package com.wzz.smscode.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 用户信息传输对象（用于前端展示，已脱敏）
 */
@Data
public class UserDTO {

    private Long userId;
    private BigDecimal balance;
    /**
     * 用户的项目价格配置（键为 "项目ID-线路ID" 字符串）
     */
    private Map<String, BigDecimal> projectPrices;
    private Integer status;
    private Integer dailyGetCount;
    private Integer dailyCodeCount;
    private Double dailyCodeRate;
    private Integer totalGetCount;
    private Integer totalCodeCount;
    private Double totalCodeRate;
    private Boolean isAgent;
    private Long parentId;
    /**
     * （可选）是否为管理员
     */
    private Boolean isAdmin;
}