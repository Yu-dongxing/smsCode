package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.moduleService.strategy.AuthStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TokenPathStrategy implements AuthStrategy {

    private static final String TOKEN_PLACEHOLDER = "{token}";

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project) {
        // 约定：在数据库的 getNumberRoute 字段中用 {token} 作为占位符
        String url = (project.getDomain() + project.getGetNumberRoute()).replace(TOKEN_PLACEHOLDER, project.getAuthTokenValue());
        return webClient.get().uri(url).retrieve().bodyToMono(String.class);
    }

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {
        return null;
    }

    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        String url = (project.getDomain() + project.getGetCodeRoute())
                .replace(TOKEN_PLACEHOLDER, project.getAuthTokenValue())
                .replace("{identifier}", identifier);
        return webClient.get().uri(url).retrieve().bodyToMono(String.class);
    }

    @Override
    public AuthType getAuthType() {
        return AuthType.TOKEN_PATH;
    }
}
