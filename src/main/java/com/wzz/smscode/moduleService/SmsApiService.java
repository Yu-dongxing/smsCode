package com.wzz.smscode.moduleService;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.moduleService.strategy.AuthStrategy;
import com.wzz.smscode.moduleService.strategy.AuthStrategyFactory;
import com.wzz.smscode.util.ResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsApiService {

    private final AuthStrategyFactory authStrategyFactory;
    private final ResponseParser responseParser;
    private final WebClient webClient; // 从WebClientConfig注入

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
                    // TODO: 在这里添加对 phoneId 的处理逻辑，比如异步保存
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
}