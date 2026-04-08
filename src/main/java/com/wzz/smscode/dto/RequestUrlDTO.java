package com.wzz.smscode.dto;

import lombok.Data;

@Data
public class RequestUrlDTO {
    private String data;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 密码
     */
    private String password;
    /**
     * 需要查询的手机号码
     */
    private String phone;
    /**
     * 项目/渠道ID (由调用方根据筛选服务的要求提供)
     */
    private String type;
    /**
     * 旧版项目/渠道ID字段，保留兼容
     */
    private String cpid;
    /**
     * 认证密钥
     */
    private String card;
    /**
     * 旧版认证字段，保留兼容
     */
    private String token;
    /**
     * 国家区号 (可选, 默认为 "86")
     */
    private String countryCode;

    public String resolveType() {
        return type != null && !type.isBlank() ? type : cpid;
    }

    public String resolveCard() {
        return card != null && !card.isBlank() ? card : token;
    }

    public String resolveCountryCode() {
        return countryCode != null && !countryCode.isBlank() ? countryCode : "86";
    }
}
