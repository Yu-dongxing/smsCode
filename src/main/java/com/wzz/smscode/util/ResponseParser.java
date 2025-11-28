package com.wzz.smscode.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.exception.BusinessException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils; // 确保已导入

import java.util.HashMap; // 新增导入
import java.util.Map;     // 新增导入
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResponseParser {

    // ObjectMapper是线程安全的，可以作为单例使用
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 匹配中国大陆手机号
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("(1[3-9]\\d{9})");

    private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile("^\\s*[\"']?\\d{4}(?:\\d{2})?[\"']?\\s*$");


    // 【新增】匹配常见时间戳格式，例如: "2023-10-27 15:30:00", "2023/10/27 15:30:00" 或 "2023-10-27T15:30:00"
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2}[ T]\\d{2}:\\d{2}:\\d{2})");
    private static final Logger log = LogManager.getLogger(ResponseParser.class);

    public Optional<String> parsePhoneNumber(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = PHONE_NUMBER_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }


    /**
     * 【新增】从响应体中解析时间戳字符串。
     *
     * @param responseBody API 响应体
     * @return 包含时间戳字符串的 Optional，例如 "2023-10-27 10:00:00"
     */
    public Optional<String> parseTimestamp(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = TIMESTAMP_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            // .trim() 确保没有多余的空格
            return Optional.of(matcher.group(1).trim());
        }
        return Optional.empty();
    }




    /**
     * 【回退方法】使用正则表达式从任意文本中提取可能是验证码的数字。
     * 注意：这个正则表达式比原来的更通用，不再强制要求整个字符串就是验证码。
     *
     * @param responseBody 响应体
     * @return 验证码
     */
    public Optional<String> parseVerificationCodeWithRegex(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return Optional.empty();
        }
        // 这个正则会寻找4到6位的连续数字，更适合在复杂字符串中查找
        Matcher matcher = VERIFICATION_CODE_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            // 这里可以添加更多校验，例如检查匹配到的数字周围的上下文
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    /**
     * 【已升级】使用标准的JSON Pointer按路径解析字段值。
     * 支持嵌套对象 (如 "result.code") 和数组索引 (如 "data[0].verificationCode")。
     *
     * @param jsonBody  JSON响应体
     * @param fieldPath 字段路径，例如 "data[0].verificationCode"
     * @return 提取到的值的字符串形式
     */
    public Optional<String> parseJsonFieldValueByPath(String jsonBody, String fieldPath) {
        try {
            // 兼容处理 "$.data.path" 和 "data.path" 两种格式
            String cleanPath = fieldPath.startsWith("$.") ? fieldPath.substring(2) : fieldPath;

            String jsonPointerPath = "/" + cleanPath.replace(".", "/").replaceAll("\\[(\\d+)\\]", "/$1");

            // 2. 读取JSON树
            JsonNode rootNode = objectMapper.readTree(jsonBody);

            // 3. 使用 at() 方法和JSON Pointer路径来查找节点
            JsonNode targetNode = rootNode.at(jsonPointerPath);

            // 4. 检查节点是否存在且有值
            if (targetNode.isMissingNode() || targetNode.isNull()) {
                return Optional.empty();
            }

            // 5. 返回节点的值的文本表示
            return Optional.of(targetNode.asText());

        } catch (JsonProcessingException e) {
            log.error("解析JSON响应体失败: {}", jsonBody, e);
            return Optional.empty();
        }
    }


    /**
     * 【强化核心解析能力】使用JSON Path从响应体中提取字段值。
     * 这是本类中进行JSON解析的唯一推荐方法，支持嵌套路径。
     *
     * @param jsonBody  JSON响应体
     * @param jsonPath  字段的JSON路径 (例如 "data.token" 或 "result[0].id")
     * @return 包含字段值的Optional<String>
     */
    public Optional<String> parseJsonPath(String jsonBody, String jsonPath) {
        if (!StringUtils.hasText(jsonBody) || !StringUtils.hasText(jsonPath)) {
            return Optional.empty();
        }
        try {
            // 将点分隔路径转换为JSON Pointer路径 (e.g., "data.token" -> "/data/token")
            String jsonPointerPath = "/" + jsonPath.replace(".", "/").replaceAll("\\[(\\d+)\\]", "/$1");
            JsonNode rootNode = objectMapper.readTree(jsonBody);
            JsonNode targetNode = rootNode.at(jsonPointerPath);

            if (targetNode.isMissingNode() || targetNode.isNull()) {
                return Optional.empty();
            }
            return Optional.of(targetNode.asText());
        } catch (JsonProcessingException e) {
            log.error("使用JSON Path '{}' 解析JSON响应体失败: {}", jsonPath, jsonBody, e);
            return Optional.empty();
        }
    }

    public Optional<String> parseVerificationCode(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = VERIFICATION_CODE_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }



    /**
     * 从JSON格式的响应体中，根据字段名提取其值。
     * 这个方法非常通用，可以处理值为字符串、数字或布尔值的情况。
     *
     * @param responseBody JSON响应体字符串
     * @param fieldName    要提取的字段名
     * @return 提取到的值
     */
    public static Optional<String> parseJsonFieldValue(String responseBody, String fieldName) {
        if (responseBody == null || fieldName == null || fieldName.isEmpty()) {
            return Optional.empty();
        }

        // 正则表达式用于匹配 "fieldName":"value" 或 "fieldName":value (无引号的值)
        String regex = String.format("\"%s\"\\s*:\\s*(?:\"([^\"]+)\"|([\\w.-]+))", Pattern.quote(fieldName));
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(responseBody);

        if (matcher.find()) {
            // group(1) 对应带引号的值，group(2) 对应不带引号的值
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            return Optional.ofNullable(value);
        }

        return Optional.empty();
    }

    /**
     * 验证一个字符串是否为4位或6位数字。
     *
     * @param codeString 待验证的字符串
     * @return 如果是，则返回true
     */
    public static boolean isVerificationCode(String codeString) {
        if (codeString == null) {
            return false;
        }
        return codeString.matches("\\d{4}|\\d{6}");
    }
}