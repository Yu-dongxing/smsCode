// File: src/main/java/com/wzz/smscode/moduleService/strategy/impl/ApiKeyTokenStrategy.java

package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.moduleService.strategy.BaseAuthStrategy;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.util.ResponseParser; // 假设你有一个响应解析工具类
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyTokenStrategy extends BaseAuthStrategy {

    // 注入响应解析工具，用于从JSON中安全地提取字段
    private final ResponseParser responseParser;

    private final ProjectService projectService;

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {
        log.info("项目 [{}]: 进入 API_KEY_TOKEN_LOGIN 策略 - 获取手机号", project.getProjectId());

        return getValidToken(webClient, project).flatMap(token -> {
            String url = normalizeDomain(project.getDomain()) + project.getGetNumberRoute();
            log.info("项目 [{}]: 使用有效Token发起取号请求: {}", project.getProjectId(), url);

            // 根据配置构建请求体，这里我们假设取号需要projectId
            // 注意：这里的实现需要根据你业务的通用性进行调整，params[0] 只是一个示例
            Map<String, Object> requestBody = new HashMap<>();
            if (params != null && params.length > 0) {
                // 接口文档要求projectId是Integer
                requestBody.put("projectId", Integer.parseInt(params[0]));
            }

            // 根据文档，创建任务支持批量，这里为了通用性，暂时先实现单个创建的逻辑
            // 如果需要支持批量，可以在SmsApiService中构建更复杂的requestBody

            Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {
                String tokenValue = (StringUtils.hasText(project.getAuthTokenPrefix()) ? project.getAuthTokenPrefix() : "") + token;
                spec.header(project.getAuthTokenField(), tokenValue);
            };

            return buildAndExecuteRequest(webClient,
                    project.getGetNumberMethod(),
                    url,
                    project.getGetNumberRequestType(),
                    requestBody,
                    authApplier);
        });
    }

    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        log.info("项目 [{}]: 进入 API_KEY_TOKEN_LOGIN 策略 - 获取验证码，标识符: {}", project.getProjectId(), identifier);

        return getValidToken(webClient, project).flatMap(token -> {
            String url = normalizeDomain(project.getDomain()) + project.getGetCodeRoute();
            log.info("项目 [{}]: 使用有效Token发起取码请求: {}", project.getProjectId(), url);

            // 根据配置构建请求体
            // 文档中查询任务状态的字段是 taskId
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put(project.getGetCodeField(), Integer.parseInt(identifier));


            Consumer<WebClient.RequestHeadersSpec<?>> authApplier = spec -> {
                String tokenValue = (StringUtils.hasText(project.getAuthTokenPrefix()) ? project.getAuthTokenPrefix() : "") + token;
                spec.header(project.getAuthTokenField(), tokenValue);
            };

            return buildAndExecuteRequest(webClient,
                    project.getGetCodeMethod(),
                    url,
                    project.getGetCodeRequestType(),
                    requestBody,
                    authApplier);
        });
    }

    /**
     * 获取有效的Token。如果当前Token不存在或已过期，则自动登录获取新Token。
     * @param webClient WebClient 实例
     * @param project 项目配置
     * @return 包含有效Token的 Mono
     */
    private Mono<String> getValidToken(WebClient webClient, Project project) {
        // 如果Token存在且未过期（我们预留1分钟的缓冲时间），则直接返回
        if (StringUtils.hasText(project.getAuthTokenValue()) &&
                project.getTokenExpirationTime() != null &&
                LocalDateTime.now().plusMinutes(1).isBefore(project.getTokenExpirationTime())) {
            log.debug("项目 [{}]: 使用缓存的有效Token", project.getProjectId());
            return Mono.just(project.getAuthTokenValue());
        }

        // 否则，执行登录逻辑
        log.info("项目 [{}]: Token无效或不存在，执行登录操作", project.getProjectId());
        return login(webClient, project);
    }

    /**
     * 执行登录操作，获取新的Token
     * @param webClient WebClient 实例
     * @param project 项目配置
     * @return 包含新Token的 Mono
     */
    private Mono<String> login(WebClient webClient, Project project) {
        String loginUrl = normalizeDomain(project.getDomain()) + project.getLoginRoute();

        // 构建登录请求体，字段名和值都来自数据库配置
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(project.getAuthUsernameField(), project.getAuthUsername());

        log.info("项目 [{}]: 发起登录请求到 {}", project.getProjectId(), loginUrl);

        return buildAndExecuteRequest(webClient,
                project.getLoginMethod(),
                loginUrl,
                project.getLoginRequestType(),
                requestBody,
                spec -> {}) // 登录请求通常不需要额外的认证头
                .flatMap(responseBody -> {
                    // 解析响应获取Token
                    Map<String, String> tokenInfo = responseParser.parseTokenInfo(project, responseBody);

                    String token = tokenInfo.get("token");
                    if (!StringUtils.hasText(token)) {
                        return Mono.error(new BusinessException("登录失败：无法从响应中解析Token"));
                    }

                    // 解析Token有效期
                    long expiresInSeconds = Long.parseLong(tokenInfo.getOrDefault("expiresIn", "86400"));
                    // 在内存中更新 project 对象
                    project.setAuthTokenValue(token.replace("Bearer ", "")); // 去除前缀存储
                    project.setTokenExpirationTime(LocalDateTime.now().plusSeconds(expiresInSeconds));

                    log.info("项目 [{}]: 登录成功，获取到新的Token，有效期至: {}", project.getProjectId(), project.getTokenExpirationTime());
                    projectService.updateById(project);

                    return Mono.just(project.getAuthTokenValue());
                });
    }

    @Override
    public AuthType getAuthType() {
        return AuthType.API_KEY_TOKEN_LOGIN;
    }
}