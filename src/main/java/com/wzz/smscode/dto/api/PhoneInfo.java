package com.wzz.smscode.dto.api;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封装从 "获取手机号" 接口返回的关键信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhoneInfo {
    /**
     * 获取到的手机号码
     */
    private String phone;

    /**
     * 用于查询验证码的唯一标识 (对应 API 中的 extraParams)
     */
    private String uuid;
}