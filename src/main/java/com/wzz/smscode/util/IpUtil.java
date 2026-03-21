package com.wzz.smscode.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;


public final class IpUtil {

    private static final String UNKNOWN = "unknown";

    private IpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            String[] ipList = xForwardedFor.split(",");
            for (String ip : ipList) {
                String candidate = ip == null ? null : ip.trim();
                if (StringUtils.hasText(candidate) && !UNKNOWN.equalsIgnoreCase(candidate)) {
                    return candidate;
                }
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp) && !UNKNOWN.equalsIgnoreCase(realIp.trim())) {
            return realIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr.trim() : null;
    }
}
