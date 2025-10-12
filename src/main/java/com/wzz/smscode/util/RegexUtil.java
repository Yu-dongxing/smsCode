package com.wzz.smscode.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则表达式工具类
 */
public final class RegexUtil {

    // 预编译 Pattern 以提升性能
    // 匹配以 1 开头的 11 位数字
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("1\\d{10}");

    // 提取被引号或冒号包围的 4 位或 6 位数字验证码 (使用了正向后行断言)
    private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile("(?<=[:\"'])\\d{4,6}(?=[\"'])");

    private RegexUtil() {
        // 防止实例化
    }

    /**
     * 从给定文本中提取第一个匹配的手机号码
     *
     * @param text 待匹配的文本
     * @return 找到的手机号码字符串，未找到则返回 null
     */
    public static String extractPhoneNumber(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = PHONE_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    /**
     * 从给定文本中提取第一个匹配的验证码 (4-6位数字)
     *
     * @param text 待匹配的文本，通常是短信内容
     * @return 找到的验证码字符串，未找到则返回 null
     */
    public static String extractVerificationCode(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = VERIFICATION_CODE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    /**
     * (可选) 判断 API 响应是否表明号码为黑名单老号
     *
     * @param response 筛选 API 的响应字符串
     * @return 如果是老号则返回 true，否则返回 false
     */
    public static boolean isOldNumber(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        // 具体实现强依赖于 API 返回格式，这里仅为示例
        // 例如，如果返回 {"status":"blocked"}
        return response.contains("\"status\":\"blocked\"");
    }
}