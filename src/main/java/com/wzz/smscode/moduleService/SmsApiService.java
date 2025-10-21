package com.wzz.smscode.moduleService;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.moduleService.strategy.AuthStrategy;
import com.wzz.smscode.moduleService.strategy.AuthStrategyFactory;
import com.wzz.smscode.util.ResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
     * @param project 项目配置
     * @param params  用于替换URL中占位符的动态参数
     * @return 手机号或操作ID
     */
    public String getPhoneNumber(Project project, String... params) { // 1. 签名增加 varargs
        log.info("开始为项目 [{} - {}] 获取手机号...", project.getProjectId(), project.getLineId());
        AuthStrategy strategy = authStrategyFactory.getStrategy(String.valueOf(project.getAuthType()));
        try {
            // 2. 将接收到的 params 原封不动地传递给策略
            String responseBody = strategy.buildGetNumberRequest(webClient, project, params).block();
            log.info("项目 [{} - {}] 获取手机号API响应: {}", project.getProjectId(), project.getLineId(), responseBody);

            return responseParser.parsePhoneNumber(responseBody)
                    .orElseGet(() -> {
                        log.warn("在响应中未解析到标准手机号，将返回原始响应体作为操作ID。响应: {}", responseBody);
                        return responseBody;
                    });
        } catch (Exception e) {
            log.error("为项目 [{} - {}] 获取手机号失败", project.getProjectId(), project.getLineId(), e);
            // 3. 将原始异常信息包装起来，而不是只返回 getMessage()
            throw new BusinessException(0,"获取手机号失败", e);
        }
    }

    /**
     * 统一获取验证码（带轮询）
     * @param project 项目配置
     * @param identifier 手机号或上一步获取的操作ID
     * @return 验证码
     */
    public String getVerificationCode(Project project, String identifier) {
        log.info("开始为项目 [{} - {}], 标识符 [{}] 获取验证码...", project.getProjectId(), project.getLineId(), identifier);
        AuthStrategy strategy = authStrategyFactory.getStrategy(String.valueOf(project.getAuthType()));

        long timeoutMillis = project.getCodeTimeout() * 1000L;
        long startTime = System.currentTimeMillis();
        final long pollIntervalMillis = 3000; // 每3秒轮询一次

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                String responseBody = strategy.buildGetCodeRequest(webClient, project, identifier).block();
                log.debug("轮询获取验证码响应: {}", responseBody);

                Optional<String> code = responseParser.parseVerificationCode(responseBody);
                if (code.isPresent()) {
                    log.info("成功获取到验证码: {}", code.get());
                    return code.get();
                }

                log.debug("未获取到验证码，将在 {}ms 后重试...", pollIntervalMillis);
                Thread.sleep(pollIntervalMillis);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                throw new BusinessException("获取验证码线程被中断");
            } catch (Exception e) {
                log.error("轮询获取验证码时发生错误", e);
                // 如果是API调用失败，可以考虑重试或直接抛出异常
                throw new BusinessException("获取验证码API调用失败: " + e.getMessage());
            }
        }

        throw new BusinessException("获取验证码超时 (" + project.getCodeTimeout() + "秒)");
    }
}