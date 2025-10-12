package com.wzz.smscode.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类，基于 Java 8 time API
 */
public final class DateUtil {

    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateUtil() {}

    /**
     * 将 LocalDateTime 格式化为 "yyyy-MM-dd HH:mm:ss" 字符串
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime.format(DATETIME_FORMATTER);
    }

    /**
     * 获取 24 小时之前的时间点
     */
    public static LocalDateTime twentyFourHoursAgo() {
        return LocalDateTime.now().minusHours(24);
    }
}