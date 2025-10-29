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
    private String cpid;
    /**
     * 认证密钥
     */
    private String token;
    /**
     * 国家区号 (可选, 默认为 "86")
     */
    private String countryCode;
}
