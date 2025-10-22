package com.wzz.smscode.moduleService.strategy;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.RequestType;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 认证策略的抽象基类，提供了构建不同类型请求的通用方法
 */
public abstract class BaseAuthStrategy implements AuthStrategy {

    /**
     * 构建并执行 WebClient 请求的通用核心方法
     *
     * @param webClient       WebClient 实例
     * @param method          HTTP方法 (e.g., "GET", "POST")
     * @param url             请求的完整URL
     * @param requestType     请求参数类型 (JSON, FORM, PARAM)
     * @param params          请求参数
     * @param authApplier     一个消费者，用于应用特定的认证逻辑 (e.g., 添加Header)
     * @return API 响应的 Mono<String>
     */
    protected Mono<String> buildAndExecuteRequest(WebClient webClient,
                                                  String method,
                                                  String url,
                                                  RequestType requestType,
                                                  Map<String, Object> params,
                                                  Consumer<WebClient.RequestHeadersSpec<?>> authApplier) {

        WebClient.RequestBodyUriSpec requestSpec = webClient.method(org.springframework.http.HttpMethod.valueOf(method.toUpperCase()));
        WebClient.RequestHeadersSpec<?> headersSpec;

        switch (requestType) {
            case JSON:
                headersSpec = requestSpec.uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(params));
                break;

            case FORM:
                MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                params.forEach((key, value) -> formData.add(key, String.valueOf(value)));
                headersSpec = requestSpec.uri(url)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(formData));
                break;

            case PARAM:
            default: // 默认为 PARAM 类型
                MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
                params.forEach((key, value) -> queryParams.add(key, String.valueOf(value)));
                headersSpec = requestSpec.uri(uriBuilder -> uriBuilder.path(url)
                        .queryParams(queryParams)
                        .build());
                break;
        }

        // 应用具体的认证逻辑
        authApplier.accept(headersSpec);

        return headersSpec.retrieve().bodyToMono(String.class);
    }

    /**
     * 辅助方法，确保URL以 http:// 或 https:// 开头
     * @param domain 原始域名
     * @return 完整的域名
     */
    protected String normalizeDomain(String domain) {
        if (domain != null && !domain.toLowerCase().startsWith("http")) {
            return "http://" + domain;
        }
        return domain;
    }
}