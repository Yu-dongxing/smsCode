package com.wzz.smscode.module.strategy.auth;


import com.wzz.smscode.entity.Project;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

import java.util.Map;

/**
 * 认证策略接口
 * 定义如何将认证信息应用到HTTP请求中
 */
public interface AuthStrategy {

    /**
     * 将认证信息应用到请求中
     *
     * @param project     项目配置信息
     * @param headers     HTTP请求头
     * @param params      URL请求参数 (适用于GET或FORM POST)
     * @param body        JSON请求体 (适用于POST)
     */
    void applyAuth(Project project, HttpHeaders headers, MultiValueMap<String, String> params, Map<String, Object> body);
}