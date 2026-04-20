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
import java.time.Duration;
import java.util.List;


/**
 * 专用的号码筛选服务类。
 * 该服务不依赖于系统或项目配置，直接通过传入的参数执行API请求，并返回原始的状态信息。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneNumberFilterService {

    private final WebClient webClient; // 从WebClientConfig注入

    public List<String> getServerList() {
        return List.of(
                "https://h6-haha.bubble89.shop"
        );
    }

    /**
     * 根据提供的参数检查手机号码的状态。
     * 此方法会轮询所有预设的服务器，直到获得一个成功的响应，并返回解码后的'state'字段内容。
     *
     * @param card API所需的认证令牌。
     * @param type  项目或渠道的ID。
     * @param phone 需要检查的手机号码。
     * @return 返回一个 Mono<String>，其中包含API响应中解码后的'state'字段值。
     *         如果所有服务器都请求失败或响应格式不正确，则返回一个空的 Mono。
     */
    public Mono<Integer> checkPhoneNumberState(String card, String type, String phone, String countryCode) {
        if (!StringUtils.hasText(card) || !StringUtils.hasText(type) || !StringUtils.hasText(phone)) {
            log.error("号码筛选请求缺少必要参数：card, type, 或 phone。");
            return Mono.error(new IllegalArgumentException("card, type, and Phone must not be empty."));
        }

        List<String> servers = getServerList();
        if (servers.isEmpty()) {
            log.warn("号码筛选服务器列表为空，无法执行请求。");
            return Mono.empty();
        }

        // 使用响应式编程风格轮询服务器
        return Flux.fromIterable(servers)
                .concatMapDelayError(serverIp -> {
                    // 为当前服务器构建请求URI
                    URI requestUri = buildRequestUri(serverIp, phone, card, type, countryCode);
                    log.info("正在向服务器 [{}] 发送号码筛选请求，号码: [{}]...", serverIp, phone);

                    // 发起异步请求
                    return webClient.get()
                            .uri(requestUri)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(50)) // 设置超时
                            .flatMap(responseBody -> {
                                log.info("服务器 [{}] 响应: {}", serverIp, responseBody);
                                return parseStateFromResponse(responseBody);
                            })
                            .doOnError(e -> log.warn("访问服务器 [{}] 失败: {}. 正在尝试下一个...", serverIp, e.getMessage()))
                            .onErrorResume(e -> Mono.empty()); // 如果当前服务器请求失败，切换到下一个
                })
                .next(); // 只取第一个成功解析出的 'state' 结果
    }

    public Mono<String> checkPhoneNumberStateText(String card, String type, String phone, String countryCode) {
        if (!StringUtils.hasText(card) || !StringUtils.hasText(type) || !StringUtils.hasText(phone)) {
            log.error("号码筛选请求缺少必要参数：card, type, 或 phone。");
            return Mono.error(new IllegalArgumentException("card, type, and Phone must not be empty."));
        }

        List<String> servers = getServerList();
        if (servers.isEmpty()) {
            log.warn("号码筛选服务器列表为空，无法执行请求。");
            return Mono.empty();
        }

        return Flux.fromIterable(servers)
                .concatMapDelayError(serverIp -> {
                    URI requestUri = buildRequestUri(serverIp, phone, card, type, countryCode);
                    log.info("正在向服务器 [{}] 发送号码筛选请求，号码: [{}]...", serverIp, phone);
                    return webClient.get()
                            .uri(requestUri)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(50))
                            .flatMap(responseBody -> {
                                log.info("服务器 [{}] 响应: {}", serverIp, responseBody);
                                return parseStateTextFromResponse(responseBody);
                            })
                            .doOnError(e -> log.warn("访问服务器 [{}] 失败: {}. 正在尝试下一个...", serverIp, e.getMessage()))
                            .onErrorResume(e -> Mono.empty());
                })
                .next();
    }

    /**
     * 构建号码筛选的请求URI。
     */
    public URI buildRequestUri(String serverIp, String phone, String card, String type, String countryCode) {
        String finalCountryCode = StringUtils.hasText(countryCode) ? countryCode : "86";
        String baseUrl = serverIp.startsWith("http") ? serverIp : "http://" + serverIp;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/app-api/filter/send") // 新API路径
                .queryParam("phone", phone)
                .queryParam("code", finalCountryCode)
                .queryParam("card", card)
                .queryParam("type", type);

        return builder.build().toUri();
    }

    /**
     * 从新版API响应JSON中解析出'state'字段的值。
     * 因为新版可能返回多个账号状态，此处做拼接返回。
     *
     * @param responseBody API返回的JSON字符串。
     * @return 返回包含解码后'states'值的 int。
     */
    private Mono<Integer> parseStateFromResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            log.warn("API响应体为空，无法解析。");
            return Mono.error(new IllegalArgumentException("API响应体为空，无法解析"));
        }

        try {
            Object codeObj = JsonPath.parse(responseBody).read("$.code");
            if (!isSuccessCode(codeObj)) {
                return Mono.error(new IllegalArgumentException("API返回的code不是成功码(0/200), 响应无效。响应: " + responseBody));
            }
            Integer status = extractStatus(responseBody);
            if (status == null) {
                return Mono.error(new IllegalArgumentException("API返回的status响应无效。响应: "+ responseBody));
            }

            return Mono.just(status);

        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("解析API响应JSON失败，响应体: "+ responseBody+"错误:"+ e.getMessage()));
        }
    }

    public Mono<String> parseStateTextFromResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            log.warn("API响应体为空，无法解析。");
            return Mono.error(new IllegalArgumentException("API响应体为空，无法解析"));
        }

        try {
            Object codeObj = JsonPath.parse(responseBody).read("$.code");
            if (!isSuccessCode(codeObj)) {
                return Mono.error(new IllegalArgumentException("API返回的code不是成功码(0/200), 响应无效。响应: " + responseBody));
            }

            String rawState = extractStatusText(responseBody);
            if (StringUtils.hasText(rawState)) {
                return Mono.just(rawState);
            }

            Integer status = extractStatus(responseBody);
            if (status == null) {
                return Mono.error(new IllegalArgumentException("API返回的status响应无效。响应: " + responseBody));
            }

            return Mono.just(convertStatusToLegacyText(status));
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("解析API响应JSON失败，响应体: " + responseBody + "错误:" + e.getMessage()));
        }
    }

    public int parseStateFromResponseInt(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            log.warn("API响应体为空，无法解析。");
            return -4;
        }

        try {
            Object codeObj = JsonPath.parse(responseBody).read("$.code");
            if (!isSuccessCode(codeObj)) {
                log.warn("API返回的code不是成功码(0/200), 响应无效。响应: {}", responseBody);
                return -4;
            }
            Integer status = extractStatus(responseBody);
            if (status == null) {
                log.warn("API返回的status响应无效。响应: "+ responseBody);
                return -4;
            }

            return status;

        } catch (Exception e) {
            log.warn(("解析API响应JSON失败，响应体: "+ responseBody+"错误:"+ e.getMessage()));
            return -4;
        }
    }

    private boolean isSuccessCode(Object codeObj) {
        if (!(codeObj instanceof Number number)) {
            return false;
        }
        int code = number.intValue();
        return code == 0 || code == 200;
    }

    private Integer extractFirstStatus(Object statusObj) {
        if (statusObj instanceof Number number) {
            return number.intValue();
        }
        if (statusObj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Number number) {
                return number.intValue();
            }
            if (first instanceof String text && StringUtils.hasText(text)) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Integer extractStatus(String responseBody) {
        Integer status = extractByPath(responseBody, "$.data.status", this::extractFirstStatus);
        if (status != null) {
            return status;
        }
        return extractByPath(responseBody, "$.data[*].status", this::extractFirstStatus);
    }

    private String extractFirstTextState(Object stateObj) {
        if (stateObj instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        if (stateObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String text && StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String extractStatusText(String responseBody) {
        String statusText = extractByPath(responseBody, "$.data.statusStr", this::extractFirstTextState);
        if (StringUtils.hasText(statusText)) {
            return statusText;
        }
        statusText = extractByPath(responseBody, "$.data.state", this::extractFirstTextState);
        if (StringUtils.hasText(statusText)) {
            return statusText;
        }
        return extractByPath(responseBody, "$.data[*].state", this::extractFirstTextState);
    }

    private <T> T extractByPath(String responseBody, String path, java.util.function.Function<Object, T> extractor) {
        try {
            return extractor.apply(JsonPath.parse(responseBody).read(path));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String convertStatusToLegacyText(int status) {
        return switch (status) {
            case 0 -> "未注册";
            case -1 -> "已注册";
            case -2 -> "封禁";
            case -3 -> "未知";
            default -> String.valueOf(status);
        };
    }
}
