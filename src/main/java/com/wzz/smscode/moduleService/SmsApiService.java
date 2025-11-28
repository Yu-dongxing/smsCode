package com.wzz.smscode.moduleService;

import com.wzz.smscode.dto.ApiConfig.ApiConfig;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.SystemConfig;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.jayway.jsonpath.JsonPath;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsApiService {
    private final WebClient webClient; // 从WebClientConfig注入
    private final SystemConfigService systemConfigService;
    private final ModuleUtil moduleUtil;
    // 变量替换正则：匹配 {{variable}}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    /**
     * 变量替换工具
     * 将字符串中的 {{key}} 替换为 context 中的 value
     */
    private String replaceVariables(String input, Map<String, String> context) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim(); // 获取 {{ key }} 中的 key
            String value = context.getOrDefault(key, ""); // 如果找不到，替换为空字符串，或者保留原样视需求而定
            // 对 value 进行转义，防止 value 中包含 $ 导致报错
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 第一步：获取手机号
     * 流程：
     * 1. 检查是否需要登录（如果有Token且未过期可跳过，这里简化为每次检查或由配置决定）
     * 2. 执行获取手机号请求
     * 3. 返回包含手机号、ID等信息的 Map
     */
    public Map<String, String> getPhoneNumber(Project project, String... dynamicParams) {
        log.info("开始为项目 [{} - {}] 获取手机号", project.getProjectName(), project.getLineId());
        // 1. 初始化上下文变量 (Context)
        Map<String, String> context =null;
        try {
            context =moduleUtil.getApiToken(project, dynamicParams);
        }catch (Exception e) {
            log.error("项目 [{} - {}] 获取Token出现错误, 错误：{}", project.getProjectName(), project.getLineId(), e);
            throw new BusinessException("接口登录失败，无法执行后续操作");
        }

        // 3. 执行获取手机号接口
        if (project.getGetNumberConfig() == null) {
            throw new BusinessException("项目未配置获取手机号接口");
        }
        // 执行请求，executeApi 会将提取到的变量放入 context
        moduleUtil.executeApi(project.getGetNumberConfig(), context);
        // 4. 检查结果
        // 前端配置提取规则时，必须将手机号提取为 "phone"，ID提取为 "id" (约定优于配置)
        if (!context.containsKey("phone")) {
            log.error("获取手机号接口执行成功，但未提取到 phone 变量。当前Context: {}", context);
            throw new BusinessException("未获取到手机号，请检查提取规则配置");
        }

        return context;
    }


    /**
     * 获取api的余额
     */
    public String getApiBalance(Project project, String... dynamicParams){
        // 1. 初始化上下文变量 (Context)
        Map<String, String> context =null;
        try {
            context =moduleUtil.getApiToken(project, dynamicParams);
        }catch (Exception e) {
            log.error("项目 [{} - {}] 获取Token出现错误, 错误：{}", project.getProjectName(), project.getLineId(), e);
            throw new BusinessException("接口登录失败，无法执行后续操作");
        }
        // 3. 执行获取手机号接口
        if (project.getGetBalanceConfig() == null) {
            throw new BusinessException("项目未配置获取余额接口");
        }
        // 执行请求，executeApi 会将提取到的变量放入 context
        moduleUtil.executeApi(project.getGetBalanceConfig(), context);
        if (!context.containsKey("balance")) {
            log.error("获取余额接口执行成功，但未提取到 balance 变量。当前Context: {}", context);
            throw new BusinessException("未获取到余额，请检查提取规则配置");
        }
        return context.get("balance");
    }

    /**
     * 第二步：获取验证码
     * @param project 项目配置
     * @param identifierParams 上一步获取到的所有变量 (包含 phone, id, token 等)
     */
    public String getVerificationCode(Project project, Map<String, String> identifierParams) {
        log.info("开始获取验证码，参数: {}", identifierParams);

        // 1. 准备上下文
        Map<String, String> context = new HashMap<>(identifierParams);
        // 确保 Token 存在 (如果上一步没传，从库里读)
        if (!context.containsKey("token") && StringUtils.hasText(project.getAuthTokenValue())) {
            context.put("token", project.getAuthTokenValue());
        }
        ApiConfig config = project.getGetCodeConfig();
        if (config == null) {
            throw new BusinessException("项目未配置获取验证码接口");
        }
        // 2. 轮询逻辑
        int attempts = 0;
        int maxAttempts = (project.getCodeMaxAttempts() != null && project.getCodeMaxAttempts() > 0)
                ? project.getCodeMaxAttempts() : 10;

        while (attempts < maxAttempts) {
            try {
                moduleUtil.executeApi(config, context);
                String code = context.get("code"); // 约定提取变量名为 code
                if (StringUtils.hasText(code) && !"null".equalsIgnoreCase(code.trim())) {
                    log.info("成功获取验证码: {}", code);
                    return code;
                }
                log.info("未获取到验证码，第 {} 次重试...", attempts + 1);
                attempts++;
                Thread.sleep(3000); // 3秒轮询一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("获取验证码被中断");
            } catch (Exception e) {
                log.warn("轮询中发生异常: {}", e.getMessage());
                attempts++;
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
        throw new BusinessException("获取验证码超时");
    }

    /**
     * 【新增】单次尝试获取验证码 (无轮询)
     * 用于用户手动刷新或状态检查
     *
     * @param project 项目配置
     * @param identifierParams 上下文参数
     * @return Optional<String>
     */
    public Optional<String> fetchVerificationCodeOnce(Project project, Map<String, String> identifierParams) {
         log.info("执行单次验证码获取, 参数: {}", identifierParams);

        Map<String, String> context = new HashMap<>(identifierParams);
        if (!context.containsKey("token") && StringUtils.hasText(project.getAuthTokenValue())) {
            context.put("token", project.getAuthTokenValue());
        }

        ApiConfig config = project.getGetCodeConfig();
        if (config == null) {
            log.warn("项目未配置获取验证码接口");
            return Optional.empty();
        }

        try {
            log.info("进入单次验证码获取，{}，{}",config,context);
            moduleUtil.executeApi(config, context);

            String code = context.get("code");
            if (StringUtils.hasText(code) && !"null".equalsIgnoreCase(code.trim())) {
                log.info("单次获取成功: {}", code);
                return Optional.of(code);
            }
        } catch (Exception e) {
            log.warn("单次获取验证码API执行异常: {}", e.getMessage());
        }

        return Optional.empty();
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
            log.info("系统配置：{},项目 [{}] 未开启号码筛选功能，默认号码 [{}] 可用。",systemConfig.getEnableNumberFiltering(), project.getProjectName(), phoneNumber);
            return Mono.just(true); // 功能未开启，默认可用
        }

        // 2. 从系统配置中获取服务器地址列表
        List<String> servers = getServerList();
        if (servers.isEmpty()) {
            log.info("号码筛选功能已开启，但系统未配置任何有效的筛选服务器地址。号码 [{}] 将被视为不可用。", phoneNumber);
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
                                log.info("服务器 [{}] 响应: {}", serverIp, responseBody);
                                boolean isAvailable = parseAvailabilityResponse(responseBody, cpid);
                                log.info("号码 [{}] 在服务器 [{}] 的筛选结果: {}", phoneNumber, serverIp, isAvailable ? "可用" : "不可用");
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