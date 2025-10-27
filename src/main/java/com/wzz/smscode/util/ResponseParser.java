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
     * 【已优化】根据项目配置，智能解析手机号和唯一ID。
     * <p>
     * 业务逻辑:
     * 1.  如果 project 中配置了 `responsePhoneNumberField`，则根据该字段名解析手机号。
     * 2.  如果 project 中配置了 `responsePhoneIdField`，则根据该字段名解析唯一ID。
     * 3.  如果 `responsePhoneNumberField` 未配置，则回退到使用通用正则表达式解析响应体中的手机号。
     * 4.  `responsePhoneIdField` 的解析与手机号解析相互独立。
     * 5.  【优化点】确保不会将 null 或空字符串存入返回的 Map 中，增强代码健壮性。
     *
     * @param project      项目配置实体
     * @param responseBody API接口返回的响应体字符串
     * @return 一个Map，可能包含 "phone" 和 "id" 两个键，其值保证为非空字符串。如果解析不到任何信息，则返回空Map。
     */
    public Map<String, String> parsePhoneNumberByType(Project project, String responseBody) {
        // 1. 入参校验：为空的响应体直接返回空Map，便于调用方处理
        if (!StringUtils.hasText(responseBody)) {
            return new HashMap<>();
        }

        Map<String, String> result = new HashMap<>();
        String phoneNumberField = project.getResponsePhoneField();
        String phoneIdField = project.getResponsePhoneIdField();

        boolean isPhoneNumberFieldDefined = StringUtils.hasText(phoneNumberField);

        // 2. 优先策略：如果定义了手机号字段，则根据字段解析
        if (isPhoneNumberFieldDefined) {
            parseJsonFieldValue(responseBody, phoneNumberField)
                    // 【关键优化点】: 使用 filter 过滤掉 null 和空字符串
                    .filter(StringUtils::hasText)
                    .ifPresent(phone -> result.put("phone", phone));
        }

        // 3. 独立解析ID字段：无论手机号是否解析成功，都尝试解析ID
        if (StringUtils.hasText(phoneIdField)) {
            parseJsonFieldValue(responseBody, phoneIdField)
                    // 【关键优化点】: 同样增加 filter 保证ID的有效性
                    .filter(StringUtils::hasText)
                    .ifPresent(id -> result.put("id", id));
        }

        // 4. 回退策略：仅当未定义手机号字段 且 Map中尚无手机号时，才使用通用正则进行解析
        //    增加 !result.containsKey("phone") 是为了避免在某些边缘情况下覆盖已解析到的值
        if (!isPhoneNumberFieldDefined && !result.containsKey("phone")) {
            Matcher matcher = PHONE_NUMBER_PATTERN.matcher(responseBody);
            if (matcher.find()) {
                // matcher.group(1) 在 find() 成功后保证不为 null
                result.put("phone", matcher.group(1));
            }
        }

        return result;
    }



    /**
     * 【已重构】根据项目配置，智能解析验证码。
     * 优先使用JSON路径解析，如果失败或未配置，则回退到正则表达式。
     *
     * @param project      项目配置实体
     * @param responseBody API接口返回的响应体字符串
     * @return 包含验证码字符串的 Optional。
     */
    public Optional<String> parseVerificationCodeByTypeByJson(Project project, String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return Optional.empty();
        }

        String codeField = project.getResponseCodeField();

        // 优先策略：如果定义了验证码字段，则根据JSON路径进行解析
        if (StringUtils.hasText(codeField)) {
            Optional<String> parsedCode = parseJsonFieldValueByPath(responseBody, codeField);
            // 如果JSON解析成功，直接返回结果
            if (parsedCode.isPresent()) {
                return parsedCode;
            }
            // 如果JSON解析失败，可以根据业务需求决定是否要继续尝试正则（这里选择继续）
            log.warn("使用JSON路径 '{}' 解析验证码失败，将尝试使用通用正则进行回退解析。", codeField);
        }

        // 回退策略：如果未定义字段或JSON解析失败，则使用通用正则进行解析
        return parseVerificationCodeWithRegex(responseBody);
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
            // 1. 将用户友好的路径转换为标准的JSON Pointer路径
            //    - "result.code" -> "/result/code"
            //    - "data[0].verificationCode" -> "/data/0/verificationCode"
            String jsonPointerPath = "/" + fieldPath.replace(".", "/").replaceAll("\\[(\\d+)\\]", "/$1");

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
     * 【新增核心方法】从登录响应中解析出Token和有效期。
     * 这是一个高级封装方法，供策略类直接调用。
     *
     * @param project 项目配置，包含token和有效期的字段路径
     * @param loginResponseBody 登录接口的JSON响应体
     * @return 一个Map，可能包含 "token" 和 "expiresIn" 两个键。
     */
    public Map<String, String> parseTokenInfo(Project project, String loginResponseBody) {
        Map<String, String> tokenInfo = new HashMap<>();

        if (!StringUtils.hasText(loginResponseBody)) {
            return tokenInfo;
        }

        // 使用核心解析方法解析Token
        if (StringUtils.hasText(project.getResponseTokenField())) {
            parseJsonPath(loginResponseBody, project.getResponseTokenField())
                    .ifPresent(token -> tokenInfo.put("token", token));
        }

        // 使用核心解析方法解析有效期
        if (StringUtils.hasText(project.getResponseTokenExpirationField())) {
            parseJsonPath(loginResponseBody, project.getResponseTokenExpirationField())
                    .ifPresent(expiresIn -> tokenInfo.put("expiresIn", expiresIn));
        }

        return tokenInfo;
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
     * 【新增】根据项目配置，智能解析验证码。
     * <p>
     * 业务逻辑:
     * 1.  如果 project 中配置了 `responseCodeField`，则根据该字段名从JSON响应中解析验证码。
     * 2.  如果 `responseCodeField` 未配置，则回退到使用通用正则表达式解析整个响应体。
     *
     * @param project      项目配置实体
     * @param responseBody API接口返回的响应体字符串
     * @return 包含验证码字符串的 Optional。
     */
    public Optional<String> parseVerificationCodeByType(Project project, String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return Optional.empty();
        }

        String codeField = project.getResponseCodeField();

        // 优先策略：如果定义了验证码字段，则根据字段解析
        if (StringUtils.hasText(codeField)) {
            return parseJsonFieldValue(responseBody, codeField);
        } else {
            // 回退策略：如果未定义字段，则使用通用正则进行解析
            return parseVerificationCode(responseBody);
        }
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