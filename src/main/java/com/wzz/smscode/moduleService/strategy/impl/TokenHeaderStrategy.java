package com.wzz.smscode.moduleService.strategy.impl;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.moduleService.strategy.AuthStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class TokenHeaderStrategy implements AuthStrategy {

    private static final Logger log = LogManager.getLogger(TokenHeaderStrategy.class);

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project) {
        return null;
    }

    @Override
    public Mono<String> buildGetNumberRequest(WebClient webClient, Project project, String... params) {
        log.info("进入 TokenHeaderStrategy.buildGetNumberRequest");
        String domain = project.getDomain();
        // 1. 检查 domain 是否已经包含了 http:// 或 https://
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            // 2. 如果没有，则默认添加 http://
            domain = "http://" + domain;
        }

        // 3. 拼接完整的 URL 模板
        String urlTemplate = domain + project.getGetNumberRoute();

        String projectId = String.valueOf(project.getProjectId());

        // 从 params 中获取 num 的值
        String num = (params != null && params.length > 0) ? params[0] : "1"; // 如果没传，给个默认值 "1"

        String tokenValue = (StringUtils.hasText(project.getAuthTokenPrefix()) ? project.getAuthTokenPrefix() : "") + project.getAuthTokenValue();

        return webClient.get()
                .uri(urlTemplate, projectId, num) //
                .header(project.getAuthTokenField(), tokenValue)
                .retrieve()
                .bodyToMono(String.class);
    }

   /**
     * 【核心实现】构建获取验证码的 POST 请求
     * @param webClient WebClient 实例
     * @param project 项目配置
     * @param identifier 就是上一步获取到的 UUID
     * @return 包含API响应的 Mono
     */
    @Override
    public Mono<String> buildGetCodeRequest(WebClient webClient, Project project, String identifier) {
        log.info("进入：TokenHeaderStrategy.buildGetCodeRequest，传入ids：{}",identifier);
        // 1. 准备 URL 和认证 Token
        String domain = project.getDomain();
        if (domain != null && !domain.startsWith("http")) {
            domain = "http://" + domain;
        }
        // getCodeRoute 应该配置为 "/system/phoneRecord/qryByUuid"
        String url = domain + project.getGetCodeRoute();
        String tokenValue = (StringUtils.hasText(project.getAuthTokenPrefix()) ? project.getAuthTokenPrefix() : "") + project.getAuthTokenValue();

        // 2. 构建请求体 (Request Body)
        Map<String, Object> requestBody = new HashMap<>();
        // 根据接口文档，需要一个 key 为 "uuidList" 的数组
        requestBody.put("uuidList", Collections.singletonList(identifier));

        // 3. 发起 POST 请求
        return webClient.post()
                .uri(url)
                .header(project.getAuthTokenField(), tokenValue)   // 设置认证头
                .contentType(MediaType.APPLICATION_JSON)           // 声明请求体是 JSON 格式
                .bodyValue(requestBody)                            // 设置请求体内容
                .retrieve()
                .bodyToMono(String.class);                         // 获取响应体
    }

    @Override
    public AuthType getAuthType() {
        return AuthType.TOKEN_HEADER;
    }
}