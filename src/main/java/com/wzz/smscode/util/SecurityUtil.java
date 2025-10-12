package com.wzz.smscode.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关工具类
 * 注意：在标准的 Spring Boot 应用中，我们通常直接注入 PasswordEncoder 的 Bean，
 * 而不是使用静态工具类。这里仅作为示例。
 */
public final class SecurityUtil {

    private static final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private SecurityUtil() {}

    public static String hashPassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public static boolean verifyPassword(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}