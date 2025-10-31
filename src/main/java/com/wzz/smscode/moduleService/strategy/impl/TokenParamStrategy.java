package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.enums.RequestType;
import com.wzz.smscode.moduleService.strategy.BaseAuthStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 实现 Token 和其他参数通过 URL 查询字符串进行认证的策略。
 * 这种策略适用于认证信息直接附加在 getNumberRoute 和 getCodeRoute 的 URL 后的场景。
 */
@Component
public class TokenParamStrategy extends BaseAuthStrategy {

    private static final Logger log = LogManager.getLogger(TokenParamStrategy.class);

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {
        log.info("进入 TokenParamStrategy - 处理获取手机号请求");

        // 使用修正后的 buildUriFromRoute 方法
        URI finalUri = buildUriFromRoute(project.getDomain(), project.getGetNumberRoute());
//        log.info("构建的获取手机号请求 URI: {}", finalUri);

        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {};

        // 关键：此调用将正确解析到 BaseAuthStrategy 中新的、接收 URI 对象的重载方法
        return buildAndExecuteRequest(webClient, "GET", finalUri, RequestType.PARAM, Collections.emptyMap(), authApplier);
    }

    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        log.info("进入 TokenParamStrategy - 处理获取验证码请求，标识符: {}", identifier);

        String getCodeRoute = project.getGetCodeRoute();

        if (getCodeRoute.contains("=phone")) {
            // 注意：简单替换可能在某些边缘情况下有问题，但对于 '=phone' 场景是可行的
            getCodeRoute = getCodeRoute.replace("=phone", "=" + identifier);
        } else {
            log.warn("在 getCodeRoute 中未找到 '=phone' 占位符，将直接使用原始路由。");
        }

        // 使用修正后的 buildUriFromRoute 方法
        URI finalUri = buildUriFromRoute(project.getDomain(), getCodeRoute);
        log.info("构建的获取验证码请求 URI: {}", finalUri);

        Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {};

        // 关键：此调用也将正确解析到新的重载方法
        return buildAndExecuteRequest(webClient, "GET", finalUri, RequestType.PARAM, Collections.emptyMap(), authApplier);
    }

    /**
     * 【已修正】一个健壮的、基于组件的 URI 构建辅助方法。
     * 此版本直接让 UriComponentsBuilder 处理完整的 URL，以正确编码查询参数中的特殊字符。
     *
     * @param domain 域名，例如 "http://api.example.com"
     * @param route 路由，可以包含路径和查询字符串，例如 "/api/getNumber?token=xyz=="
     * @return 构建并编码好的 URI 对象
     */
    private URI buildUriFromRoute(String domain, String route) {
        // 拼接完整的 URL 字符串
        String fullUrl = normalizeDomain(domain) + route;

        // 让 UriComponentsBuilder 从完整的 URL 字符串进行构建
        // 它会自动解析路径、查询参数，并对它们进行正确的编码
        return UriComponentsBuilder.fromUriString(fullUrl)
                .build()
                .encode() // 确保所有组件都被正确编码
                .toUri();
    }




    @Override
    public AuthType getAuthType() {
        return AuthType.TOKEN_PARAM;
    }
}