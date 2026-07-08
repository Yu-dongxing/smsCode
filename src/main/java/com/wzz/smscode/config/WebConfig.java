package com.wzz.smscode.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StringToDateConverter stringToDateConverter;

    // 通过构造函数注入你的 Converter
    public WebConfig(StringToDateConverter stringToDateConverter) {
        this.stringToDateConverter = stringToDateConverter;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(stringToDateConverter);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 1. 获取当前请求的路径
                    String path = cn.dev33.satoken.context.SaHolder.getRequest().getRequestPath();

                    // 2. 先执行通用的登录检查
                    StpUtil.checkLogin();

                    // 3. 针对管理员路径，进行身份精确校验
                    if (path.startsWith("/api/admin/")) {
                        // 排除登录接口本身
                        if (!path.equals("/api/admin/login")) {
                            String loginId = StpUtil.getLoginId().toString();
                            if (!"0".equals(loginId)) {
                                throw new cn.dev33.satoken.exception.NotPermissionException("非管理员账号，无权访问管理后台");
                            }
                        }
                    }

                    // 4. (可选) 针对代理商路径限制逻辑
                    // if (path.startsWith("/api/agent/")) { ... }

                }))
                .addPathPatterns("/api/admin/**", "/api/agent/**", "/api/project/**")
                .excludePathPatterns("/api/admin/login", "/api/agent/login");
    }
}
