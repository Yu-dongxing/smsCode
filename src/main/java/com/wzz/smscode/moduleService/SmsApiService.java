package com.wzz.smscode.moduleService;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.SystemConfig;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.moduleService.strategy.AuthStrategy;
import com.wzz.smscode.moduleService.strategy.AuthStrategyFactory;
import com.wzz.smscode.service.SystemConfigService;
import com.wzz.smscode.util.ResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import com.jayway.jsonpath.JsonPath;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsApiService {

    private final AuthStrategyFactory authStrategyFactory;
    private final ResponseParser responseParser;
    private final WebClient webClient; // 从WebClientConfig注入
    private final SystemConfigService systemConfigService;

    /**
     * 【核心修改点】 增加 String... params 参数
     * 统一获取手机号或操作ID
     *
     * @param project 项目配置
     * @param params  用于替换URL中占位符的动态参数
     * @return 手机号或操作ID
     */
    public Map<String, String> getPhoneNumber(Project project, String... params) { // 1. 签名增加 varargs
        log.info("开始为项目 [{} - {}] 获取手机号...", project.getProjectId(), project.getLineId());
        AuthStrategy strategy = authStrategyFactory.getStrategy(String.valueOf(project.getAuthType()));
        try {
            String responseBody = strategy.buildGetNumberRequest(webClient, project, params).block();
            log.info("项目 [{} - {}] 获取手机号API响应: {}", project.getProjectId(), project.getLineId(), responseBody);
            // 【核心修改】调用新的解析方法
            Map<String, String> parsedResult = responseParser.parsePhoneNumberByType(project, responseBody);
            String phoneNumber = parsedResult.get("phone");
            // 如果成功解析出手机号，就返回手机号
            if (StringUtils.hasText(phoneNumber)) {
                log.info("为项目 [{} - {}] 解析到手机号: {}", project.getProjectId(), project.getLineId(), phoneNumber);

                // 你可以在这里处理解析出的ID，例如存入数据库或缓存，用于后续的释放、拉黑等操作
                String phoneId = parsedResult.get("id");
                if (StringUtils.hasText(phoneId)) {
                    log.info("为项目 [{} - {}] 解析到手机号唯一ID: {}", project.getProjectId(), project.getLineId(), phoneId);
                }
                return parsedResult;
            }
            // 如果没有解析到手机号，则按原逻辑返回整个响应体作为操作ID
            log.warn("在响应中未解析到手机号，将返回原始响应体作为操作ID。响应: {}", responseBody);
            parsedResult.put("responseBody", responseBody);

            return parsedResult;

        } catch (Exception e) {
            log.error("为项目 [{} - {}] 获取手机号失败", project.getProjectId(), project.getLineId(), e);
            throw new BusinessException(0, "获取手机号失败", e);
        }
    }

    /**
     * 统一获取验证码（带轮询）
     *
     * @param project    项目配置
     * @param identifier 手机号或上一步获取的操作ID
     * @return 最新的验证码
     */
    public String getVerificationCode(Project project, String identifier) {
        log.info("开始为项目 [{} - {}], 标识符 [{}] 获取验证码 (最大尝试次数: {})...",
                project.getProjectId(), project.getLineId(), identifier, project.getCodeMaxAttempts());
        AuthStrategy strategy = authStrategyFactory.getStrategy(String.valueOf(project.getAuthType()));

        // 定义计数器和最大尝试次数
        int attempts = 0;
        final int maxAttempts = (project.getCodeMaxAttempts() != null && project.getCodeMaxAttempts() > 0)
                ? project.getCodeMaxAttempts()
                : 20;
        final long pollIntervalMillis = 3000; // 轮询间隔

        while (attempts < maxAttempts) {
            try {
                String responseBody = strategy.buildGetCodeRequest(webClient, project, identifier).block();
                log.info("轮询获取验证码响应: {}", responseBody);

                if (responseBody != null && (responseBody.contains("错误") || responseBody.contains("失败"))) {
                    log.info("响应体中检测到错误信息，将立即停止轮询。响应内容: {}", responseBody);
                    // 抛出业务异常，并将完整的响应体作为错误信息返回给调用方
                    throw new BusinessException("获取验证码失败，上游API返回明确的错误信息: " + responseBody);
                }

                Optional<String> codeOpt = responseParser.parseVerificationCodeByTypeByJson(project, responseBody);

                // 增加对字符串 "null" (忽略大小写) 的判断
                if (codeOpt.isPresent() &&
                        !codeOpt.get().trim().isEmpty() &&
                        !"null".equalsIgnoreCase(codeOpt.get().trim())) {

                    String code = codeOpt.get().trim();
                    log.info("成功获取到验证码: {}", code);
                    return code; // 获取到有效验证码后直接返回
                }

                // 递增计数器
                attempts++;
                if (attempts < maxAttempts) {
                    log.debug("未获取到有效验证码，将在 {}ms 后进行下一次尝试...", pollIntervalMillis);
                    Thread.sleep(pollIntervalMillis);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new BusinessException("获取验证码线程被中断");
            } catch (Exception e) {
                log.error("轮询获取验证码时发生错误", e);
                throw new BusinessException("获取验证码API调用失败: " + e.getMessage());
            }
        }
        throw new BusinessException("获取验证码失败，已达到最大尝试次数 (" + maxAttempts + "次)");
    }

    /**
     * 【新增方法】
     * 单次尝试获取验证码，无轮询。
     * 此方法主要由 getCode 接口在用户主动查询时调用，以进行一次即时检查。
     *
     * @param project    项目配置
     * @param identifier 手机号或操作ID
     * @return Optional<String> 包含验证码（如果获取到）
     */
    public Optional<String> fetchVerificationCodeOnce(Project project, String identifier) {
        log.info("为项目 [{}], 标识符 [{}] 执行单次验证码获取...", project.getProjectId(), identifier);
        AuthStrategy strategy = authStrategyFactory.getStrategy(String.valueOf(project.getAuthType()));
        try {
            String responseBody = strategy.buildGetCodeRequest(webClient, project, identifier).block();
            log.info("单次获取验证码响应: {}", responseBody);

            if (responseBody != null && (responseBody.contains("错误") || responseBody.contains("失败"))) {
                log.warn("上游API在单次获取中返回明确错误: {}", responseBody);
                return Optional.empty(); // 返回空，表示未获取到
            }

            Optional<String> codeOpt = responseParser.parseVerificationCodeByTypeByJson(project, responseBody);

            // 过滤掉空字符串或 "null" 字符串
            return codeOpt.filter(code -> StringUtils.hasText(code) && !"null".equalsIgnoreCase(code.trim()));

        } catch (Exception e) {
            log.error("单次轮询获取验证码时发生错误, identifier [{}]: {}", identifier, e.getMessage());
            // 发生任何异常都视为未获取到
            return Optional.empty();
        }
    }

    /**
     * 【新增】一个私有的辅助方法，用于解析可能包含不同分隔符的时间戳字符串。
     *
     * @param timestampStr 从响应中提取的时间戳字符串
     * @return 解析后的 LocalDateTime 对象
     */
    private Optional<LocalDateTime> parseFlexibleTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return Optional.empty();
        }
        String normalizedTimestamp = timestampStr.replace('/', '-').replace('T', ' ');
        try {
            return Optional.of(LocalDateTime.parse(normalizedTimestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (DateTimeParseException e) {
            log.error("无法使用标准格式 'yyyy-MM-dd HH:mm:ss' 解析时间戳: '{}'", timestampStr, e);
            return Optional.empty();
        }
    }

    /**
     * 调用外部API检查手机号码是否可用。
     * @param project     项目配置，包含API的认证和路由信息。
     * @param phoneNumber 需要检查的手机号码。
     * @return 返回一个 Mono<Boolean>。如果号码可用，发射 true；否则发射 false。
     *         如果项目未开启号码筛选功能，默认视为可用，直接返回 true。
     *         如果开启了筛选但项目和全局配置均无效，则视为不可用，返回 false。
     */
    public Mono<Boolean> checkPhoneNumberAvailability(Project project, String phoneNumber) {
        // 1. 检查项目是否开启了号码筛选功能
        if (project.getEnableFilter() == null || !project.getEnableFilter()) {
            log.info("项目 [{}] 未开启号码筛选功能，默认号码 [{}] 可用。", project.getProjectName(), phoneNumber);
            return Mono.just(true); // 功能未开启，默认可用
        }

        // 2. 决定使用哪个配置 (项目自身配置 或 全局系统配置)
        Project effectiveProject = resolveEffectiveProjectConfig(project);

        // 如果连有效的配置都找不到，直接判定为不可用
        if (effectiveProject == null) {
            log.warn("项目 [{}] 开启了筛选，但自身和系统全局均未提供有效的筛选API配置。号码 [{}] 将被视为不可用。", project.getProjectName(), phoneNumber);
            return Mono.just(false);
        }

        log.info("项目 [{}] 开始使用有效配置筛选号码 [{}]...", project.getProjectName(), phoneNumber);

        try {
            // 3. 从工厂获取对应的认证策略
            // 注意：认证信息总是使用原始项目(effectiveProject)的，因为全局配置不包含认证
            AuthStrategy strategy = authStrategyFactory.getStrategy(effectiveProject.getAuthType().getValue());

            // 4. 使用策略构建并执行请求 (传入的是包含正确API信息的 effectiveProject)
            return strategy.buildCheckNumberRequest(webClient, effectiveProject, phoneNumber)
                    .flatMap(responseBody -> {
                        // 5. 解析响应结果
                        log.debug("项目 [{}] 的号码筛选API响应: {}", effectiveProject.getProjectName(), responseBody);
                        try {
                            boolean isAvailable = parseAvailabilityResponse(responseBody, effectiveProject.getResponseSelectNumberApiField());
                            log.info("号码 [{}] 筛选结果: {}", phoneNumber, isAvailable ? "可用" : "不可用");
                            return Mono.just(isAvailable);
                        } catch (Exception e) {
                            log.error("解析项目 [{}] 的号码筛选响应失败: {}", effectiveProject.getProjectName(), e.getMessage());
                            return Mono.error(new BusinessException("解析API响应失败"));
                        }
                    })
                    .onErrorResume(e -> {
                        // 6. 处理请求过程中的异常
                        log.error("调用项目 [{}] 的号码筛选API失败: {}", effectiveProject.getProjectName(), e.getMessage());
                        return Mono.just(false); // 出现任何错误，都视为号码不可用
                    });

        } catch (UnsupportedOperationException e) {
            log.error("获取认证策略失败: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * 【新增辅助方法】
     * 解析并返回最终用于API请求的有效Project配置。
     *
     * @param originalProject 原始的项目配置
     * @return 如果项目自身配置有效，则返回原始对象；如果需要回退，则返回一个合并了全局配置的新对象；如果两者都无效，返回 null。
     */
    private Project resolveEffectiveProjectConfig(Project originalProject) {
        // 优先使用项目自身的API配置
        if (StringUtils.hasText(originalProject.getSelectNumberApiRoute())) {
            log.debug("项目 [{}] 使用其自身的号码筛选API配置。", originalProject.getProjectName());
            return originalProject;
        }

        // 项目自身配置为空，尝试回退到系统全局配置
        log.info("项目 [{}] 未配置筛选API，尝试使用系统全局配置进行回退。", originalProject.getProjectName());
        SystemConfig systemConfig = systemConfigService.getConfig();

        // 检查全局配置是否有效
        if (systemConfig != null && StringUtils.hasText(systemConfig.getFilterApiUrl())) {
            log.info("成功回退到系统全局筛选API: {}", systemConfig.getFilterApiUrl());

            // 创建一个新的Project对象，避免修改原始传入的project实例 (这是一个好习惯)
            Project mergedProject = new Project();
            BeanUtils.copyProperties(originalProject, mergedProject);

            // 用系统配置覆盖筛选相关的字段
            // **重要提示:** SystemConfig中的 filterApiUrl 对应 Project中的 selectNumberApiRoute
            mergedProject.setSelectNumberApiRoute(systemConfig.getFilterApiUrl());
            mergedProject.setSelectNumberApiRouteMethod(systemConfig.getSelectNumberApiRouteMethod());
            mergedProject.setSelectNumberApiReauestType(systemConfig.getSelectNumberApiReauestType());

            if (systemConfig.getSelectNumberApiReauestValue() != null) {
                mergedProject.setSelectNumberApiRequestValue(systemConfig.getSelectNumberApiReauestValue());
            }

            mergedProject.setResponseSelectNumberApiField(systemConfig.getResponseSelectNumberApiField());

            return mergedProject;
        }

        // 项目和全局配置都无效
        return null;
    }

    /**
     * 解析API响应，判断号码是否可用。
     *
     * @param responseBody API返回的JSON字符串。
     * @param jsonPathExpression 用于从JSON中提取状态字段的JSONPath表达式。
     * @return 如果可用，返回 true，否则返回 false。
     */
    private boolean parseAvailabilityResponse(String responseBody, String jsonPathExpression) {
        if (!StringUtils.hasText(responseBody) || !StringUtils.hasText(jsonPathExpression)) {
            // 如果响应体或解析路径为空，无法判断，视为不可用
            return false;
        }

        try {
            // 使用 Jayway JsonPath 来解析，因为它在你提供的 Project 实体注释中提到了
            Object status = JsonPath.read(responseBody, jsonPathExpression);
            if (status instanceof Boolean) {
                return (Boolean) status;
            }
            if (status instanceof String) {
                // 例如，API可能返回 "success", "ok", "true", "AVAILABLE" 等字符串
                String statusStr = ((String) status).toLowerCase();
                return statusStr.equals("true") || statusStr.equals("success") || statusStr.equals("ok") || statusStr.equals("available");
            }
            if (status instanceof Number) {
                // 例如，API可能返回 1 代表成功/可用, 0 代表失败/不可用
                return ((Number) status).intValue() == 1;
            }

            return false; // 其他未知类型，均视为不可用

        } catch (Exception e) {
            log.error("使用JsonPath解析响应失败，表达式: '{}'，响应体: '{}'。错误: {}", jsonPathExpression, responseBody, e.getMessage());
            return false;
        }
    }
}