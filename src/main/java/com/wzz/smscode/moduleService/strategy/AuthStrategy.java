package com.wzz.smscode.moduleService.strategy;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public interface AuthStrategy {

    /**
     * 构建获取手机号的请求
     * @param webClient WebClient 实例
     * @param project 项目配置
     * @param params  动态参数 (例如，在URL中替换占位符)
     * @return 经过认证包装的 Mono<String>
     */
    Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params);

    /**
     * 构建获取验证码的请求
     * @param webClient WebClient 实例
     * @param project 项目配置
     * @param identifier 标识符（手机号或上一步返回的ID等）
     * @return 经过认证包装的 Mono<String>
     */
    Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier);

    /**
     * 返回当前策略支持的认证类型
     * @return AuthType
     */
    AuthType getAuthType();
}