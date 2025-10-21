package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.moduleService.strategy.AuthStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TokenParamStrategy implements AuthStrategy {

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project) {
        String baseUrl = project.getDomain() + project.getGetNumberRoute();
        return webClient.get()
                .uri(baseUrl, uriBuilder -> uriBuilder
                        .queryParam(project.getAuthTokenField(), project.getAuthTokenValue())
                        .build())
                .retrieve()
                .bodyToMono(String.class);
    }

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {
        return null;
    }

    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        String baseUrl = project.getDomain() + project.getGetCodeRoute().replace("{identifier}", identifier);
        return webClient.get()
                .uri(baseUrl, uriBuilder -> uriBuilder
                        .queryParam(project.getAuthTokenField(), project.getAuthTokenValue())
                        .build())
                .retrieve()
                .bodyToMono(String.class);
    }

    @Override
    public AuthType getAuthType() {
        return AuthType.TOKEN_PARAM;
    }
}