package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.enums.RequestType;
import com.wzz.smscode.moduleService.strategy.BaseAuthStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class TokenHeaderStrategy extends BaseAuthStrategy {

    private static final Logger log = LogManager.getLogger(TokenHeaderStrategy.class);

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {
        log.info("进入 TokenHeaderStrategy - 处理获取手机号请求，参数类型: {}", project.getGetNumberRequestType());

        String baseUrl = normalizeDomain(project.getDomain()) + project.getGetNumberRoute();
        log.info("执行的项目线路：{}-{} 构建的获取手机号请求 URI 模板: {}", project.getProjectId(), project.getLineId(), baseUrl);

        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("projectId", project.getProjectId());
        // 如果 params 存在且不为空，则使用第一个参数作为 num，否则默认为 "1"
        uriVariables.put("num", (params != null && params.length > 0) ? params[0] : "1");

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .buildAndExpand(uriVariables)
                .toUri();

        // 定义认证逻辑
        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {
            String tokenValue = (StringUtils.hasText(project.getAuthTokenPrefix()) ? project.getAuthTokenPrefix() : "") + project.getAuthTokenValue();
            spec.header(project.getAuthTokenField(), tokenValue);
        };

        // 对于GET请求，参数已在URI中，请求体参数为空
        Map<String, Object> requestParams = Collections.emptyMap();

        // 【修改】调用新的、接收 URI 对象的方法
        return buildAndExecuteRequest(webClient, "GET", uri, project.getGetNumberRequestType(), requestParams, authApplier);
    }

    /**
     * 【增强版】构建获取验证码的请求
     * 此方法完全由 Project 实体驱动，适配性极强。
     *
     * @param webClient WebClient 实例
     * @param project 项目配置，包含了构建请求所需的所有信息
     * @param identifier 唯一标识符 (例如手机号或上一步返回的UUID)
     * @return 经过认证包装的 Mono<String>
     */
    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        log.info("TokenHeaderStrategy - 正在为项目 [ID:{}] 构建获取验证码请求...", project.getProjectId());

        // 1. 从配置构建基础URL
        String baseUrl = normalizeDomain(project.getDomain()) + project.getGetCodeRoute();

        Map<String, Object> requestParams = new HashMap<>();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl);

        // 2. 动态处理参数：参数可能在路径、URL参数或请求体中
        // 检查路由中是否包含占位符 {identifier}
        if (baseUrl.contains("{identifier}")) {
            // 场景: HuApi (GET /.../getCodeManuallyLeast/{identifier})
            // 参数在路径中，使用 expand 进行替换
            Map<String, String> uriVariables = new HashMap<>();
            uriVariables.put("identifier", identifier);
            // expand 会自动处理 URL 编码
            URI uri = uriBuilder.buildAndExpand(uriVariables).toUri();
            log.info("构建的URI (路径变量替换后): {}", uri);
            return executeRequest(webClient, project, uri, Collections.emptyMap());
        } else {
            // 场景: 传统API (GET /.../getCode?phone=xxx 或 POST ... body:{"phone":"xxx"})
            // 参数在查询参数或请求体中
            String paramName = project.getGetCodeField(); // 从配置获取参数名，例如 "phone" 或 "uuid"
            if (StringUtils.hasText(paramName)) {
                if (project.getGetCodeRequestType() == RequestType.PARAM) {
                    // 如果是URL参数类型，则附加到查询参数
                    uriBuilder.queryParam(paramName, identifier);
                } else {
                    // 如果是JSON或FORM类型，则放入请求体
                    requestParams.put(paramName, identifier);
                }
            }
            URI uri = uriBuilder.build().toUri();
            log.info("构建的URI (查询参数或请求体): {}", uri);
            return executeRequest(webClient, project, uri, requestParams);
        }
    }

    /**
     * 内部辅助方法，用于执行最终的请求
     */
    private Mono<String> executeRequest(WebClient webClient, Project project, URI uri, Map<String, Object> requestParams) {

        // 3. 从配置构建认证头
        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {
            // 读取Token字段名、前缀和值
            String tokenValue = (StringUtils.hasText(project.getAuthTokenPrefix()) ? project.getAuthTokenPrefix() : "") + project.getAuthTokenValue();
            spec.header(project.getAuthTokenField(), tokenValue);
        };

        // 4. 从配置获取请求方法和类型，并调用基类执行
        return buildAndExecuteRequest(
                webClient,
                project.getGetCodeMethod(),         // 动态读取请求方法 (GET, POST, etc.)
                uri,
                project.getGetCodeRequestType(),    // 动态读取请求类型 (JSON, FORM, PARAM)
                requestParams,                      // 请求体参数
                authApplier                         // 应用认证逻辑
        );
    }

    @Override
    public AuthType getAuthType() {
        return AuthType.TOKEN_HEADER;
    }
}