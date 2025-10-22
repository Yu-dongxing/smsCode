package com.wzz.smscode.util;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils; // 确保已导入

import java.util.HashMap; // 新增导入
import java.util.Map;     // 新增导入
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResponseParser {

    // 匹配中国大陆手机号
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("(1[3-9]\\d{9})");

    // 匹配4位或6位验证码
    private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile(":\\s*[\"']?(\\d{4,6})[\"']?");


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
     * 【已实现】根据项目配置，智能解析手机号和唯一ID。
     * <p>
     * 业务逻辑:
     * 1.  如果 project 中配置了 `responsePhoneNumberField`，则根据该字段名解析手机号。
     * 2.  如果 project 中配置了 `responsePhoneIdField`，则根据该字段名解析唯一ID。
     * 3.  如果 `responsePhoneNumberField` 未配置，则回退到使用通用正则表达式解析响应体中的手机号。
     * 4.  `responsePhoneIdField` 的解析与手机号解析相互独立。
     *
     * @param project      项目配置实体
     * @param responseBody API接口返回的响应体字符串
     * @return 一个Map，可能包含 "phone" 和 "id" 两个键。如果解析不到任何信息，则返回空Map。
     */
    public Map<String, String> parsePhoneNumberByType(Project project, String responseBody) {
        // 为空校验，返回一个空Map比抛出异常更便于调用方处理
        if (!StringUtils.hasText(responseBody)) {
            return new HashMap<>();
        }

        Map<String, String> result = new HashMap<>();
        String phoneNumberField = project.getResponsePhoneNumberField();
        String phoneIdField = project.getResponsePhoneIdField();

        boolean isPhoneNumberFieldDefined = StringUtils.hasText(phoneNumberField);

        // 优先策略：如果定义了手机号字段，则根据字段解析
        if (isPhoneNumberFieldDefined) {
            parseJsonFieldValue(responseBody, phoneNumberField)
                    .ifPresent(phone -> result.put("phone", phone));
        }

        // 无论是否定义了手机号字段，都尝试解析ID字段（如果已定义）
        if (StringUtils.hasText(phoneIdField)) {
            parseJsonFieldValue(responseBody, phoneIdField)
                    .ifPresent(id -> result.put("id", id));
        }

        // 回退策略：仅当未定义手机号字段时，才使用通用正则进行解析
        if (!isPhoneNumberFieldDefined) {
            Matcher matcher = PHONE_NUMBER_PATTERN.matcher(responseBody);
            if (matcher.find()) {
                result.put("phone", matcher.group(1));
            }
        }

        return result;
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