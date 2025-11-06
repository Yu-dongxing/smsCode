package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.enums.RequestType;
import com.wzz.smscode.moduleService.strategy.BaseAuthStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 无认证策略实现。
 * 适用于直接调用，不需要任何特殊认证信息的API。
 */
@Component
public class NoAuthStrategy extends BaseAuthStrategy {

    private static final Logger log = LogManager.getLogger(NoAuthStrategy.class);

    /**
     * 构建获取手机号的请求（无认证）。
     */
    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {
        log.info("进入 NoAuthStrategy - 处理获取手机号请求，参数类型: {}", project.getGetNumberRequestType());

        // 标准化域名
        String domain = normalizeDomain(project.getDomain());
        String fullUrl = domain + project.getGetNumberRoute();

        log.info("执行的项目线路：{}-{} 构建的获取手机号请求 URI: {}",project.getProjectId(),project.getLineId() ,fullUrl);



        // 使用 UriComponentsBuilder 来构建 URI，以安全地处理路径变量
        // 这里假设 params[0] 是项目ID，params[1] 是数量 (如果存在)
        // 这种方式比简单的字符串替换更健壮
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("projectId", project.getProjectId());
        if (params != null && params.length > 0) {
            uriVariables.put("num", params[0]);
        } else {
            // 如果路由中需要 num 参数但未提供，这里可以设置一个默认值
            // uriVariables.put("num", "1");
        }

        URI uri = UriComponentsBuilder.fromUriString(fullUrl).buildAndExpand(uriVariables).toUri();
        log.debug("构建的获取手机号请求 URI: {}", uri);

        // 定义一个空的认证逻辑，因为这是无认证策略
        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {};

        // 调用基类的通用方法执行请求
        // 对于获取手机号，通常是GET请求，参数已在URI中，所以请求体参数为空
        return buildAndExecuteRequest(webClient, "GET", uri, RequestType.NONE, Collections.emptyMap(), authApplier);
    }

    /**
     * 构建获取验证码的请求（无认证）。
     */
    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        log.info("进入 NoAuthStrategy - 处理获取验证码请求，参数类型: {}", project.getGetCodeRequestType());

        // 准备请求参数
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put(project.getGetCodeField(), identifier);

        // 标准化域名并构建基础 URL
        String fullUrl = normalizeDomain(project.getDomain()) + project.getGetCodeRoute();
        URI uri;
        RequestType requestType = project.getGetCodeRequestType();

        // 根据请求类型构建最终的 URI
        if (requestType == RequestType.PARAM) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(fullUrl);
            requestParams.forEach((key, value) -> builder.queryParam(key, String.valueOf(value)));
            uri = builder.build().encode().toUri();
            // 参数已在URI中，清空请求体参数
            requestParams.clear();
        } else {
            uri = UriComponentsBuilder.fromUriString(fullUrl).build().encode().toUri();
        }

        log.debug("构建的获取验证码请求 URI: {}", uri);

        // 定义空的认证逻辑
        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {};

        // 调用基类的通用方法执行请求，请求方法通常是POST或GET，由项目配置决定
        // 假设获取验证码通常是 POST 请求
        return buildAndExecuteRequest(webClient, "POST", uri, requestType, requestParams, authApplier);
    }

    /**
     * 返回当前策略支持的认证类型。
     * 这是工厂模式识别此策略的关键。
     * @return AuthType.NO_AUTH
     */
    @Override
    public AuthType getAuthType() {
        return AuthType.NO_AUTH;
    }
}