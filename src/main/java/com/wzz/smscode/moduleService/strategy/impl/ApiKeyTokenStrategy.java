package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.moduleService.strategy.BaseAuthStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ApiKeyTokenStrategy  extends BaseAuthStrategy {
    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {




        return null;
    }

    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        return null;
    }

    @Override
    public AuthType getAuthType() {
        return AuthType.API_KEY_TOKEN_LOGIN;
    }
}
