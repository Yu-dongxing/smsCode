package com.wzz.smscode.moduleService.strategy;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.RequestType;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
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

    /**
     * 【新增的通用实现】
     * 构建并执行筛选号码API请求的通用实现。
     * 该方法从 Project 配置中读取所有必要信息来构建和发送请求。
     *
     * 注意：Project 实体中的字段 selectNumberApiReauestValue 的类型应为 String，
     * 代表请求参数的字段名（例如 "phone" 或 "phoneNumber"）。
     * 这里我们假设它是一个 String 类型的字段。
     */
    @Override
    public Mono<String> buildCheckNumberRequest(WebClient webClient, Project project, String phoneNumber) {
        // 构造请求参数
        Map<String, Object> params = new HashMap<>();
        // 假设 selectNumberApiReauestValue 存储的是请求字段名
        // **注意：** 原始代码中此字段为 RequestType，可能是一个笔误。这里我们将其视为 String。
        // 如果字段确实有其他含义，请相应调整此处的逻辑。
        if (project.getSelectNumberApiRequestValue() != null) {
            // 将字段名和手机号放入参数 map
            params.put(String.valueOf(project.getSelectNumberApiRequestValue()), phoneNumber);
        }

        // 构建请求 URI
        String fullUrl = normalizeDomain(project.getDomain()) + project.getSelectNumberApiRoute();
        URI uri;

        // 如果是 PARAM 类型，需要将参数附加到 URI 上
        if (project.getSelectNumberApiReauestType() == RequestType.PARAM) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(fullUrl);
            params.forEach((key, value) -> builder.queryParam(key, String.valueOf(value)));
            uri = builder.build().encode().toUri();
            // 参数已在URI中，清空请求体参数
            params.clear();
        } else {
            uri = UriComponentsBuilder.fromUriString(fullUrl).build().encode().toUri();
        }

        // 定义一个空的认证处理器，因为具体的认证逻辑会在下面的通用方法中被应用
        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {};

        // 调用已有的、更健壮的请求执行方法
        return buildAndExecuteRequest(
                webClient,
                project.getSelectNumberApiRouteMethod(), // GET, POST, etc.
                uri,
                project.getSelectNumberApiReauestType(),  // JSON, FORM, PARAM
                params,
                authApplier  // 注意：具体的认证逻辑由 getStrategy 之后调用时包装
        );
    }
}