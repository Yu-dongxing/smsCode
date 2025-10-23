package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.moduleService.strategy.BaseAuthStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

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



        String domain = normalizeDomain(project.getDomain());
        // 假设 getNumberRoute 是 /api/getNumber/{projectId}/{num} 这样的格式
        String url = domain + project.getGetNumberRoute();

        // 准备动态参数
        Map<String, Object> requestParams = new HashMap<>();
        // 假设项目ID和数量是作为URL路径参数，这里我们通过字符串替换来处理
        // 更健壮的方式是使用 WebClient 的 uri(url, uriVariables) 功能
//        url = url.replace("{projectId}", project.getProjectId());
//        if (params != null && params.length > 0) {
//            url = url.replace("{num}", params[0]);
//        } else {
//            url = url.replace("{num}", "1"); // 默认值
//        }

        // 定义认证逻辑
        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {
            String tokenValue = (StringUtils.hasText(project.getAuthTokenPrefix()) ? project.getAuthTokenPrefix() : "") + project.getAuthTokenValue();
            spec.header(project.getAuthTokenField(), tokenValue);
        };

        // 调用基类的通用方法
        // 假设获取手机号通常是GET请求
        return buildAndExecuteRequest(webClient, "GET", url, project.getGetNumberRequestType(), requestParams, authApplier);
    }

    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        log.info("进入 TokenHeaderStrategy - 处理获取验证码请求，参数类型: {}", project.getGetCodeRequestType());

        // 使用 UriComponentsBuilder 构建 URL
        String url = UriComponentsBuilder.fromUriString(normalizeDomain(project.getDomain()))
                .path(project.getGetCodeRoute())
                .build()
                .toUriString();

        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("uuidList", Collections.singletonList(identifier));

        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {
            String tokenValue = (StringUtils.hasText(project.getAuthTokenPrefix()) ? project.getAuthTokenPrefix() : "") + project.getAuthTokenValue();
            spec.header(project.getAuthTokenField(), tokenValue);
        };

        return buildAndExecuteRequest(webClient, "POST", url, project.getGetCodeRequestType(), requestParams, authApplier);
    }

    @Override
    public AuthType getAuthType() {
        return AuthType.TOKEN_HEADER;
    }
}