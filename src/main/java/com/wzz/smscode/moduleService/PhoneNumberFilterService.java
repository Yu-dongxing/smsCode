package com.wzz.smscode.moduleService;

import com.jayway.jsonpath.JsonPath;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 专用的号码筛选服务类。
 * 该服务不依赖于系统或项目配置，直接通过传入的参数执行API请求，并返回原始的状态信息。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneNumberFilterService {

    private final WebClient webClient; // 从WebClientConfig注入

    /**
     * 获取硬编码的服务器地址列表。
     * @return 服务器地址列表
     */
    private List<String> getServerList() {
        // 此处保持与原逻辑一致的硬编码服务器列表
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

    /**
     * 根据提供的参数检查手机号码的状态。
     * 此方法会轮询所有预设的服务器，直到获得一个成功的响应，并返回解码后的'state'字段内容。
     *
     * @param token API所需的认证令牌。
     * @param cpid  项目或渠道的ID。
     * @param phone 需要检查的手机号码。
     * @param countryCode 国家区号，如果为null或空，则默认为 "86"。
     * @return 返回一个 Mono<String>，其中包含API响应中解码后的'state'字段值。
     *         如果所有服务器都请求失败或响应格式不正确，则返回一个空的 Mono。
     */
    public Mono<String> checkPhoneNumberState(String token, String cpid, String phone, String countryCode) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(cpid) || !StringUtils.hasText(phone)) {
            log.error("号码筛选请求缺少必要参数：token, cpid, 或 phone。");
            return Mono.error(new IllegalArgumentException("Token, CPID, and Phone must not be empty."));
        }

        List<String> servers = getServerList();
        if (servers.isEmpty()) {
            log.warn("号码筛选服务器列表为空，无法执行请求。");
            return Mono.empty();
        }

        final String finalCountryCode = StringUtils.hasText(countryCode) ? countryCode : "86";

        // 使用响应式编程风格轮询服务器
        return Flux.fromIterable(servers)
                .concatMapDelayError(serverIp -> {
                    // 为当前服务器构建请求URI
                    URI requestUri = buildRequestUri(serverIp, phone, token, cpid, finalCountryCode);
                    log.info("正在向服务器 [{}] 发送号码筛选请求，号码: [{}]...", serverIp, phone);

                    // 发起异步请求
                    return webClient.get()
                            .uri(requestUri)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(50)) // 设置超时
                            .flatMap(responseBody -> {
                                // 请求成功，解析响应体以获取 'state' 字段
                                log.info("服务器 [{}] 响应: {}", serverIp, responseBody);
                                return parseStateFromResponse(responseBody);
                            })
                            .doOnError(e -> log.warn("访问服务器 [{}] 失败: {}. 正在尝试下一个...", serverIp, e.getMessage()))
                            .onErrorResume(e -> Mono.empty()); // 如果当前服务器请求失败，切换到下一个
                })
                .next(); // 只取第一个成功解析出的 'state' 结果
    }

    /**
     * 构建号码筛选的请求URI。
     * @param serverIp    服务器IP或域名
     * @param phone       手机号
     * @param token       API Token
     * @param cpid        项目ID
     * @param cnty        国家区号
     * @return 构建好的URI对象
     */
    private URI buildRequestUri(String serverIp, String phone, String token, String cpid, String cnty) {
        String baseUrl = serverIp.startsWith("http") ? serverIp : "http://" + serverIp;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/check")
                .queryParam("phone", phone)
                .queryParam("token", token)
                .queryParam("cpid", cpid);

        // 仅当国家区号不是默认的 "86" 时才添加 'cnty' 参数
        if (StringUtils.hasText(cnty) && !"86".equals(cnty)) {
            builder.queryParam("cnty", cnty);
        }

        return builder.build().toUri();
    }

    /**
     * 从API响应JSON中解析出'state'字段的值。
     *
     * @param responseBody API返回的JSON字符串。
     * @return 返回包含解码后'state'值的 Mono<String>。如果解析失败或字段不存在，则返回 Mono.empty()。
     */
    private Mono<String> parseStateFromResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            log.warn("API响应体为空，无法解析。");
            return Mono.empty();
        }

        try {
            Map<String, Object> responseJson = JsonPath.parse(responseBody).read("$");

            // 检查 'code' 字段是否为 0
            Object codeObj = responseJson.get("code");
            if (!(codeObj instanceof Number) || ((Number) codeObj).intValue() != 0) {
                log.warn("API返回的code不为0 (value: {}), 响应无效。响应: {}", codeObj, responseBody);
                return Mono.empty(); // 响应码非0，视为无效响应
            }

            // 提取 'state' 字段
            Object stateObj = responseJson.get("state");
            if (!(stateObj instanceof String)) {
                log.warn("API响应中缺少有效的 'state' 字符串, 无法解析。响应: {}", responseBody);
                return Mono.empty();
            }

            // 解码并返回 state
            String encodedState = (String) stateObj;
            String state = URLDecoder.decode(encodedState, StandardCharsets.UTF_8.name());
            log.info("成功从响应中解析并解码 'state' 值: '{}'", state);
            return Mono.just(state);

        } catch (Exception e) {
            log.error("解析API响应JSON失败，响应体: '{}'。错误: {}", responseBody, e.getMessage());
            return Mono.empty(); // 解析异常，返回空结果
        }
    }
}