package com.wzz.smscode.moduleService.strategy;

import com.wzz.smscode.enums.RequestType;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 认证策略的抽象基类
 */
public abstract class BaseAuthStrategy implements AuthStrategy {

    /**
     * 【旧方法 - 仅为兼容保留】
     * 构建并执行 WebClient 请求的通用核心方法。
     * 注意：此方法中的 PARAM 类型处理存在缺陷，不应在新策略中使用。
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
            case NONE:
                headersSpec = requestSpec.uri(url);
                break;

            case PARAM:
            default: // 默认为 PARAM 类型 (这是有问题的实现)
                MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
                params.forEach((key, value) -> queryParams.add(key, String.valueOf(value)));
                headersSpec = requestSpec.uri(uriBuilder -> uriBuilder.path(url)
                        .queryParams(queryParams)
                        .build());
                break;
        }

        authApplier.accept(headersSpec);
        return headersSpec.retrieve().bodyToMono(String.class);
    }

    /**
     * 【新方法 - 推荐使用】
     * 根据预先构建好的 URI 对象来执行请求，这是更安全和健壮的方式。
     */
    protected Mono<String> buildAndExecuteRequest(WebClient webClient,
                                                  String method,
                                                  URI uri, // 接收一个正确的 URI 对象
                                                  RequestType requestType,
                                                  Map<String, Object> params,
                                                  Consumer<WebClient.RequestHeadersSpec<?>> authApplier) {

        WebClient.RequestBodyUriSpec requestSpec = webClient.method(org.springframework.http.HttpMethod.valueOf(method.toUpperCase()));
        WebClient.RequestHeadersSpec<?> headersSpec;

        // 由于 URI 已经完整，此 switch 主要用于处理请求体
        switch (requestType) {
            case JSON:
                headersSpec = requestSpec.uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(params));
                break;

            case FORM:
                MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                params.forEach((key, value) -> formData.add(key, String.valueOf(value)));
                headersSpec = requestSpec.uri(uri)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(formData));
                break;
            case NONE:
            case PARAM: // 对于 PARAM，所有参数都在 URI 中，请求体为空
            default:
                headersSpec = requestSpec.uri(uri);
                break;
        }

        authApplier.accept(headersSpec);
        return headersSpec.retrieve().bodyToMono(String.class);
    }

    /**
     * 辅助方法，确保URL以 http:// 或 https:// 开头
     */
    protected String normalizeDomain(String domain) {
        if (StringUtils.hasText(domain)) {
            if (domain.endsWith("/")) {
                domain = domain.substring(0, domain.length() - 1);
            }
            if (!domain.toLowerCase().startsWith("http://") && !domain.toLowerCase().startsWith("https://")) {
                return "http://" + domain;
            }
        }
        return domain;
    }
}