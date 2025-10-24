package com.wzz.smscode.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class StringToDateConverter implements Converter<String, Date> {

    // 定义一个列表，包含所有你希望支持的日期格式
    private static final List<String> DATE_FORMATS = new ArrayList<>();
    static {
        DATE_FORMATS.add("yyyy-MM-dd HH:mm:ss");
        DATE_FORMATS.add("yyyy-MM-dd HH:mm");
        DATE_FORMATS.add("yyyy-MM-dd");
        DATE_FORMATS.add("yyyy-M-d"); // 对应 "2025-11-4" 这种格式
        // 如果需要，可以继续添加更多格式
    }

    @Override
    public Date convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null; // 如果字符串为空，则返回 null
        }

        // 循环尝试所有支持的格式
        for (String format : DATE_FORMATS) {
            try {
                // SimpleDateFormat 不是线程安全的，所以每次都在方法内部创建新的实例
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false); // 设置为 false，可进行更严格的格式校验
                return sdf.parse(source);
            } catch (ParseException e) {
                // 如果解析失败，则继续尝试下一个格式
            }
        }

        // 如果所有格式都尝试失败，可以抛出异常
        throw new IllegalArgumentException("无效的日期格式: '" + source + "'");
    }
}