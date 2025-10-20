package com.wzz.smscode.module;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzz.smscode.dto.api.PhoneInfo;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.module.factory.AuthFactory;
import com.wzz.smscode.module.strategy.auth.AuthStrategy;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.util.ResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class ApiRequestService {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestService.class);

    // 魔法值常量化
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILURE = 2;
    private static final int POLLING_INTERVAL_SECONDS = 5; // 轮询间隔可以考虑也放入Project配置中

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AuthFactory authFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectService projectService;

    // 用于登录锁的Map
    private final Map<String, Object> projectLoginLocks = new ConcurrentHashMap<>();

    /**
     * 1. 获取手机号
     */
    public PhoneInfo getPhoneNumber(Project project, Map<String, Object> pathVariables) {
        ensureTokenAvailable(project);
        String url = project.getDomain() + project.getGetNumberRoute();
        String responseBody = executeRequest(project, url, HttpMethod.GET, null, pathVariables, null, project.getAuthType());

        String phoneField = project.getResponsePhoneField();
        String idField = project.getResponseIdField();

        String phone = ResponseParser.parseJsonFieldValue(responseBody, phoneField)
                .orElseThrow(() -> new BusinessException("响应中未解析到手机号 (字段: " + phoneField + ")"));
        String uuid = ResponseParser.parseJsonFieldValue(responseBody, idField)
                .orElseThrow(() -> new BusinessException("响应中未解析到ID (字段: " + idField + ")"));

        return new PhoneInfo(phone, uuid);
    }

    /**
     * 2. 获取验证码 (带轮询)
     */
    public String getVerificationCode(Project project, String uuid) throws InterruptedException {
        ensureTokenAvailable(project);
        String url = project.getDomain() + project.getGetCodeRoute();

        long timeoutMillis = System.currentTimeMillis() + (long) project.getCodeTimeout() * 1000;

        while (System.currentTimeMillis() < timeoutMillis) {
            Map<String, Object> body = new HashMap<>();
            body.put("uuidList", Collections.singletonList(uuid));
            String responseBody = executeRequest(project, url, HttpMethod.POST, body, null, null, project.getAuthType());

            String statusField = project.getResponseStatusField();
            String codeField = project.getResponseCodeField();

            // 增加健壮性，防止status不是数字
            int status = ResponseParser.parseJsonFieldValue(responseBody, statusField)
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            log.warn("无法解析状态字段 '{}' 为数字. Response: {}", s, responseBody);
                            return -1; // 返回一个无效状态
                        }
                    })
                    .orElse(-1);

            if (status == STATUS_SUCCESS) {
                String code = ResponseParser.parseJsonFieldValue(responseBody, codeField)
                        .orElseThrow(() -> new BusinessException("状态为已获取，但未解析到验证码 (字段: " + codeField + ")"));

                if (ResponseParser.isVerificationCode(code)) {
                    return code;
                } else {
                    throw new BusinessException("解析到验证码字段值 '" + code + "'，但格式不符合4位或6位数字规则");
                }

            } else if (status == STATUS_FAILURE) {
                throw new BusinessException("获取验证码失败，平台返回失败状态 (状态字段: " + statusField + ")");
            }

            log.debug("验证码尚未到达，将在 {} 秒后重试... (UUID: {})", POLLING_INTERVAL_SECONDS, uuid);
            TimeUnit.SECONDS.sleep(POLLING_INTERVAL_SECONDS);
        }

        throw new BusinessException("获取验证码超时 (UUID: " + uuid + ")");
    }

    /**
     * 确保Token可用。如果需要登录但Token为空，则自动执行登录。
     * 增加了同步锁和双重检查锁定来处理并发问题。
     * @param project 项目配置
     */
    private void ensureTokenAvailable(Project project) {
        boolean needsToken = project.getAuthType() == AuthType.TOKEN_HEADER || project.getAuthType() == AuthType.TOKEN_PARAM;
        boolean tokenMissing = project.getAuthTokenValue() == null || project.getAuthTokenValue().isEmpty();
        boolean hasLoginRoute = project.getLoginRoute() != null && !project.getLoginRoute().isEmpty();

        if (needsToken && tokenMissing && hasLoginRoute) {
            // 为每个项目（由ID标识）获取一个锁对象
            Object lock = projectLoginLocks.computeIfAbsent(project.getId().toString(), k -> new Object());
            synchronized (lock) {
                // 双重检查锁定: 在进入同步块后，再次检查Token是否已被其他线程获取
                Project latestProject = projectService.getById(project.getId());
                boolean stillMissing = latestProject.getAuthTokenValue() == null || latestProject.getAuthTokenValue().isEmpty();
                if (stillMissing) {
                    log.info("Token缺失，项目 [projectId={}] 正在执行登录操作...", project.getProjectId());
                    String newToken = performLogin(latestProject);
                    latestProject.setAuthTokenValue(newToken);
                    projectService.updateProject(latestProject); // 【重要】持久化Token
                    project.setAuthTokenValue(newToken); // 更新当前会话持有的对象
                    log.info("项目 [projectId={}] 获取并持久化了新的Token。", project.getProjectId());
                } else {
                    log.info("Token已被其他线程获取，项目 [projectId={}] 直接使用新Token。", project.getProjectId());
                    project.setAuthTokenValue(latestProject.getAuthTokenValue());
                }
            }
        }
    }

    /**
     * 执行登录并返回Token的私有方法
     */
    private String performLogin(Project project) {
        String loginUrl = project.getDomain() + project.getLoginRoute();
        Map<String, Object> loginBody = new HashMap<>();
        loginBody.put(project.getAuthUsernameField(), project.getAuthUsername());
        loginBody.put(project.getAuthPasswordField(), project.getAuthPassword());

        // 登录请求通常使用独立的认证方式，这里覆盖为BASIC_AUTH_JSON
        String loginResponse = executeRequest(project, loginUrl, HttpMethod.POST, loginBody, null, null, AuthType.BASIC_AUTH_JSON);

        String tokenField = project.getResponseTokenField();
        return ResponseParser.parseJsonFieldValue(loginResponse, tokenField)
                .orElseThrow(() -> new BusinessException("登录成功，但解析Token失败 (字段: " + tokenField + ")"));
    }

    /**
     * 通用请求执行方法 (所有请求的统一出口)
     */
    private String executeRequest(Project project, String url, HttpMethod method, Map<String, Object> body,
                                  Map<String, Object> pathVariables, Map<String, String> queryParams, AuthType authType) {
        AuthStrategy authStrategy = authFactory.getStrategy(authType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);



        // 添加查询参数 (如果存在)
        if (queryParams != null) {
            queryParams.forEach(uriBuilder::queryParam);
        }

        authStrategy.applyAuth(project, headers, uriBuilder.build(false).getQueryParams(), body);

        HttpEntity<?> requestEntity;
        try {
            if (body != null && !body.isEmpty()) {
                requestEntity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            } else {
                requestEntity = new HttpEntity<>(headers);
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(0,"构建JSON请求体失败", e);
        }

        String finalUrl = uriBuilder.buildAndExpand(pathVariables != null ? pathVariables : Collections.emptyMap()).toUriString();

        try {
            log.debug("Executing API Request. URL: {}, Method: {}, Headers: {}, Body: {}", finalUrl, method, headers, body);
            ResponseEntity<String> response = restTemplate.exchange(
                    finalUrl, method, requestEntity, String.class);
            log.debug("API Response. Status: {}, Body: {}", response.getStatusCode(), response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new BusinessException("API请求失败，状态码: " + response.getStatusCode() + ", 响应: " + response.getBody());
            }
        } catch (RestClientException e) {
            throw new BusinessException(0,"执行API请求时发生网络或客户端错误: " + e.getMessage(), e);
        }
    }
}