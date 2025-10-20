package com.wzz.smscode.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseParser {

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