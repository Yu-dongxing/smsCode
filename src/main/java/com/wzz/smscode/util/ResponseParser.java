package com.wzz.smscode.util;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResponseParser {



    // 匹配中国大陆手机号
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("(1[3-9]\\d{9})");

    // 匹配4位或6位验证码，兼容有无引号的情况
    // 正则解释: 匹配一个冒号(:)，后面可能跟0到多个空格(\s*)，然后可能有一个双引号或单引号(["']?)
    // 捕获组(\d{4,6})匹配4到6位数字，最后再匹配一个可选的引号。
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
     * 解析手机号和返回的唯一id
     * @param project
     * @param responseBody
     * @return
     */
    public Map<String,String> parsePhoneNumberByType(Project project,String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            throw new BusinessException(0,"响应为空");
        }
        Matcher matcher = PHONE_NUMBER_PATTERN.matcher(responseBody);
//        if (matcher.find()) {
//            return Optional.of(matcher.group(1));
//        }
//        return Optional.empty();
        return null;
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
     * @param responseBody JSON响应体字符串
     * @param fieldName 要提取的字段名
     * @return 提取到的值
     */
    public static Optional<String> parseJsonFieldValue(String responseBody, String fieldName) {
        if (responseBody == null || fieldName == null || fieldName.isEmpty()) {
            return Optional.empty();
        }

        // 动态构建正则表达式来匹配 "fieldName":"value" 或 "fieldName":value
        // 解释:
        // "%s"                     - 字段名 (会被fieldName替换)
        // \s*:\s*                  - 冒号，前后允许有空格
        // (?: ... )                - 非捕获组，用于逻辑或
        //   "([^"]+)"              - 情况一: 匹配带引号的值，并捕获引号内的内容 (捕获组1)
        //   |                      - 或者
        //   ([\w.-]+)              - 情况二: 匹配不带引号的值 (数字, true/false, 或简单字符串) (捕获组2)
        String regex = String.format("\"%s\"\\s*:\\s*(?:\"([^\"]+)\"|([\\w.-]+))", Pattern.quote(fieldName));

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(responseBody);

        if (matcher.find()) {
            // 检查哪个捕获组匹配到了
            // group(1) 对应带引号的情况
            // group(2) 对应不带引号的情况
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            return Optional.ofNullable(value);
        }

        return Optional.empty();
    }

    /**
     * 验证一个字符串是否为4位或6位数字。
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