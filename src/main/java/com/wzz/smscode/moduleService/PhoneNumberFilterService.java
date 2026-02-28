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

    private List<String> getServerList() {
        return Arrays.asList(
                "api.rqddjc.com:8000",
                "api.ddrqjc.com:8000"
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
    /**
     * 构建号码筛选的请求URI。
     */
    private URI buildRequestUri(String serverIp, String phone, String token, String cpid, String cnty) {
        String baseUrl = serverIp.startsWith("http") ? serverIp : "http://" + serverIp;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/charge/check") // 新API路径
                .queryParam("phone", phone)
                .queryParam("token", token)
                .queryParam("cpid", cpid);

        // 新API要求cnty必填
        String finalCnty = StringUtils.hasText(cnty) ? cnty : "86";
        builder.queryParam("cnty", finalCnty);

        return builder.build().toUri();
    }

    /**
     * 从新版API响应JSON中解析出'state'字段的值。
     * 因为新版可能返回多个账号状态，此处做拼接返回。
     *
     * @param responseBody API返回的JSON字符串。
     * @return 返回包含解码后'state'值的 Mono<String>。
     */
    private Mono<String> parseStateFromResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            log.warn("API响应体为空，无法解析。");
            return Mono.empty();
        }

        try {
            // 1. 检查 'code' 字段是否为 200 (新版成功状态码为 200)
            Object codeObj = JsonPath.parse(responseBody).read("$.code");
            if (!(codeObj instanceof Number) || ((Number) codeObj).intValue() != 200) {
                log.warn("API返回的code不为200, 响应无效。响应: {}", responseBody);
                return Mono.empty();
            }

            // 2. 读取账号数量，如果是 0，则说明是新号
            Integer accountCount = null;
            try {
                accountCount = JsonPath.parse(responseBody).read("$.userinfo.account");
            } catch (Exception ignored) {}

            if (accountCount != null && accountCount == 0) {
                log.info("检测到账号数量为0，返回状态: '新号'");
                return Mono.just("新号");
            }

            // 3. 读取所有的账号状态数组
            List<String> states = null;
            try {
                states = JsonPath.parse(responseBody).read("$.userinfo.data[*].state");
            } catch (Exception ignored) {}

            if (states != null && !states.isEmpty()) {
                // 如果有多个状态，将它们解码后用逗号拼接在一起
                List<String> decodedStates = new java.util.ArrayList<>();
                for (String state : states) {
                    try {
                        decodedStates.add(URLDecoder.decode(state, StandardCharsets.UTF_8.name()));
                    } catch (Exception e) {
                        decodedStates.add(state);
                    }
                }
                String joinedStates = String.join(",", decodedStates);
                log.info("成功从响应中解析并合并 'state' 值: '{}'", joinedStates);
                return Mono.just(joinedStates);
            }

            log.warn("API响应中缺少有效的 'state' 结构, 无法解析。响应: {}", responseBody);
            return Mono.empty();

        } catch (Exception e) {
            log.error("解析API响应JSON失败，响应体: '{}'。错误: {}", responseBody, e.getMessage());
            return Mono.empty(); // 解析异常，返回空结果
        }
    }
}