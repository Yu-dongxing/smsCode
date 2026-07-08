package com.wzz.smscode.util;

import com.wzz.smscode.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * URL 安全校验工具。
 */
public final class UrlSecurityUtil {
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\{\\{[^{}]+}}");

    private UrlSecurityUtil() {
    }

    /**
     * 校验 HTTP URL，禁止访问 RFC1918 IPv4 内网地址。
     */
    public static URI requireNonPrivateHttpUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("接口 URL 不能为空");
        }
        URI uri = parseHttpUrl(url.trim());
        rejectPrivateIpv4(uri.getHost());
        return uri;
    }

    /**
     * 保存接口配置时允许 {{变量}} 占位；真实请求前仍要校验变量替换后的 URL。
     */
    public static void requireNonPrivateHttpTemplateUrl(String url) {
        requireNonPrivateHttpUrl(TEMPLATE_VARIABLE.matcher(url).replaceAll("example.com"));
    }

    private static URI parseHttpUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BusinessException("接口 URL 只支持 http/https");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new BusinessException("接口 URL 缺少域名或 IP");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new BusinessException("接口 URL 格式错误");
        }
    }

    private static void rejectPrivateIpv4(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                    throw new BusinessException("禁止访问内网地址");
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException("接口 URL 域名无法解析");
        }
    }
}
