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
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
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
//        log.info("开始为项目 [{} - {}] 获取手机号...", project.getProjectId(), project.getLineId());
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
//        log.info("开始为项目 [{} - {}], 标识符 [{}] 获取验证码 (最大尝试次数: {})...",
//                project.getProjectId(), project.getLineId(), identifier, project.getCodeMaxAttempts());
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
//                log.info("轮询获取验证码响应: {}", responseBody);

                //todo 上线需要删除
                if (responseBody != null && (responseBody.contains("错误") || responseBody.contains("失败"))) {
//                    log.info("响应体中检测到错误信息，将立即停止轮询。响应内容: {}", responseBody);
                    // 抛出业务异常，并将完整的响应体作为错误信息返回给调用方
                    throw new BusinessException("获取验证码失败:" + responseBody);
                }

                Optional<String> codeOpt = responseParser.parseVerificationCodeByTypeByJson(project, responseBody);

                // 增加对字符串 "null" (忽略大小写) 的判断
                if (codeOpt.isPresent() &&
                        !codeOpt.get().trim().isEmpty() &&
                        !"null".equalsIgnoreCase(codeOpt.get().trim())) {

                    String code = codeOpt.get().trim();
//                    log.info("成功获取到验证码: {}", code);
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
//        log.info("为项目 [{}], 标识符 [{}] 执行单次验证码获取...", project.getProjectId(), identifier);
        AuthStrategy strategy = authStrategyFactory.getStrategy(String.valueOf(project.getAuthType()));
        try {
            String responseBody = strategy.buildGetCodeRequest(webClient, project, identifier).block();
//            log.info("单次获取验证码响应: {}", responseBody);

            if (responseBody != null && (responseBody.contains("错误") || responseBody.contains("失败"))) {
//                log.warn("上游API在单次获取中返回明确错误: {}", responseBody);
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
     * 此实现现在完全依赖于系统配置中的筛选API信息，并增加了服务器轮询和动态参数构建。
     *
     * @param project     项目配置。
     * @param phoneNumber 需要检查的手机号码。
     * @param countryCode 国家区号，如果为null或空，则默认为 "86"。
     * @return 返回一个 Mono<Boolean>。如果号码可用，返回 true；否则返回 false。
     */
    public Mono<Boolean> checkPhoneNumberAvailability(Project project, String phoneNumber, String countryCode) {
        // 1. 获取系统配置并检查功能总开关
        SystemConfig systemConfig = systemConfigService.getConfig();
        if (project.getEnableFilter() == null || !project.getEnableFilter() || !systemConfig.getEnableNumberFiltering()) {
//            log.info("系统配置：{},项目 [{}] 未开启号码筛选功能，默认号码 [{}] 可用。",systemConfig.getEnableNumberFiltering(), project.getProjectName(), phoneNumber);
            return Mono.just(true); // 功能未开启，默认可用
        }

        // 2. 从系统配置中获取服务器地址列表
        List<String> servers = getServerList();
        if (servers.isEmpty()) {
//            log.info("号码筛选功能已开启，但系统未配置任何有效的筛选服务器地址。号码 [{}] 将被视为不可用。", phoneNumber);
            return Mono.just(false);
        }

        String token = systemConfig.getFilterApiKey();
        String cpid = project.getSelectNumberApiRequestValue();

        if (!StringUtils.hasText(token) || !StringUtils.hasText(cpid)) {
            log.info("项目 [{}] 配置错误：缺少必要的API Token或CPID。", project.getProjectName());
            return Mono.error(new BusinessException("项目配置不完整，无法进行号码筛选"));
        }

        final String finalCountryCode = StringUtils.hasText(countryCode) ? countryCode : "86";
        // 4. 【核心逻辑】响应式服务器轮询与请求
        return Flux.fromIterable(servers)
                .concatMapDelayError(serverIp -> {
                    // 为当前服务器构建请求
                    URI requestUri = buildRequestUri(serverIp, phoneNumber, token, cpid, finalCountryCode);
//                    log.info("正在尝试服务器 [{}] 筛选号码 [{}]...", serverIp, phoneNumber);
                    return webClient.get()
                            .uri(requestUri)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(50)) // 根据建议设置45-60秒超时
                            .flatMap(responseBody -> {
                                // 请求成功，解析响应体
//                                log.info("服务器 [{}] 响应: {}", serverIp, responseBody);
                                boolean isAvailable = parseAvailabilityResponse(responseBody, cpid);
//                                log.info("号码 [{}] 在服务器 [{}] 的筛选结果: {}", phoneNumber, serverIp, isAvailable ? "可用" : "不可用");
                                return Mono.just(isAvailable);
                            })
                            .doOnError(e -> log.warn("访问服务器 [{}] 失败: {}. 正在尝试下一个...", serverIp, e.getMessage()))
                            .onErrorResume(e -> Mono.empty()); // 如果当前服务器请求失败，返回空Mono，以便concatMap继续处理下一个服务器
                })
                .next() // 只取第一个成功的结果
                .defaultIfEmpty(false); // 如果所有服务器都尝试失败，则默认返回 false (不可用)
    }

    /**
     * 根据文档构建请求URI
     */
    private URI buildRequestUri(String serverIp, String phone, String token, String cpid, String cnty) {
        // 确保服务器地址以 http:// 开头
        String baseUrl = serverIp.startsWith("http") ? serverIp : "http://" + serverIp;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/check")
                .queryParam("phone", phone)
                .queryParam("token", token)
                .queryParam("cpid", cpid);
        if (StringUtils.hasText(cnty) && !"86".equals(cnty)) {
            builder.queryParam("cnty", cnty);
        }

        return builder.build().toUri();
    }


    /**
     * 解析API响应，判断号码是否可用。
     *
     * @param responseBody API返回的JSON字符串。
     * @param cpid         项目ID，用于特殊逻辑判断（如快手）。
     * @return 如果可用，返回 true，否则返回 false。
     */
    private boolean parseAvailabilityResponse(String responseBody, String cpid) {
        if (!StringUtils.hasText(responseBody)) {
//            log.info("API响应体为空，判定为不可用。");
            return false;
        }

        try {
            // 使用 Jayway JsonPath 解析JSON
            Map<String, Object> responseJson = JsonPath.parse(responseBody).read("$");
            // 1. 检查 'code' 字段
            Object codeObj = responseJson.get("code");
            if (!(codeObj instanceof Number) || ((Number) codeObj).intValue() != 0) {
//                log.info("API返回的code不为0 (value: {}), 判定为不可用。响应: {}", codeObj, responseBody);
                return false;
            }
            // 2. 检查 'state' 字段
            Object stateObj = responseJson.get("state");

            if (!(stateObj instanceof String)) {
//                log.info("API响应中缺少有效的 'state' 字符串, 判定为不可用。响应: {}", responseBody);
                return false;
            }
            String encodedState = (String) stateObj;
            String state = URLDecoder.decode(encodedState, StandardCharsets.UTF_8.name());

            log.info("解码后的state值为: '{}'", state); // 增加一条日志，方便调试
            if ("ks".equalsIgnoreCase(cpid)) {
                if (state.contains("封禁")) {
                    return false;
                }
                return true;
            }
            return "新号".equals(state);
        } catch (Exception e) {
            log.error("解析API响应JSON失败，响应体: '{}'。错误: {}", responseBody, e.getMessage());
            return false; // 解析失败，安全起见，视为不可用
        }
    }

    /**
     * 从系统配置中获取服务器列表
     * @return 服务器地址列表
     */
    private List<String> getServerList() {
        //后：根据系统配置中是否使用系统内置服务器列表-》否；在系统配置
        return Arrays.asList(
                "api.rqddjc.com:1030",
                "27.25.156.161:1030",
                "103.7.141.114:1030",
                "api.ddrqjc.com:1031",
                "103.7.141.111:1031",
                "nb.rqjiance.com:1032",
                "103.7.141.39:1032"
        );
    }
}