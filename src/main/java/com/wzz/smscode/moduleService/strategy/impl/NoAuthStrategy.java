package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.moduleService.strategy.AuthStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class NoAuthStrategy implements AuthStrategy {

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project) {
        String url = project.getDomain() + project.getGetNumberRoute();
        return webClient.get().uri(url).retrieve().bodyToMono(String.class);
    }

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {
        return null;
    }

    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        // 假设获取验证码时，手机号或uuid是作为路径的一部分。如果不是，需要调整。
        // 例如，如果路径是 /getCode?phone=xxx，则需要修改为 .uri(uriBuilder -> uriBuilder.path(...).queryParam(...) )
        String url = project.getDomain() + project.getGetCodeRoute().replace("{identifier}", identifier);
        return webClient.get().uri(url).retrieve().bodyToMono(String.class);
    }

    @Override
    public AuthType getAuthType() {
        return AuthType.NO_AUTH;
    }
}