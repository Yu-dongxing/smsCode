package com.wzz.smscode.dto.ResultDTO;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserResultDTO {
    /**
     *用户名
     */
    private String userName;

    /**
     * 密码（存储为加密哈希值，用于登录验证）
     */
    private String password;

    /**
     * 用户账户余额
     */
    private BigDecimal balance;

    /**
     * 项目价格配置的 JSON 字符串。
     * 例如：{"id0001-1": 5.0, "id0002-1": 3.5}
     */
    private String projectPrices; // 数据库中建议使用 TEXT 或 JSON 类型

    /**
     * 用户状态（0=正常，1=冻结/禁用等）
     */
    private Integer status;

    /**
     * 最近24小时取号次数
     */
    private Integer dailyGetCount;

    /**
     * 最近24小时成功取码（获取到验证码）次数
     */
    private Integer dailyCodeCount;

    /**
     * 最近24小时回码率
     */
    private Double dailyCodeRate;

    /**
     * 总取号次数
     */
    private Integer totalGetCount;

    /**
     * 总取码次数
     */
    private Integer totalCodeCount;

    /**
     * 总回码率
     */
    private Double totalCodeRate;

    /**
     * 上级用户ID。顶级管理员该值可为空或0。
     */
    private Long parentId;

    /**
     * 是否具有代理权限。0表示普通用户，1表示代理用户。
     */
    private Integer isAgent;
}
