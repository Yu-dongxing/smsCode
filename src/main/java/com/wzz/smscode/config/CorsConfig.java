package com.wzz.smscode.config;

import cn.dev33.satoken.fun.strategy.SaCorsHandleFunction;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    /**
     * CORS 跨域处理策略
     */
    @Bean
    public SaCorsHandleFunction corsHandle() {
        return (req, res, sto) -> {
            res.setHeader("Access-Control-Allow-Origin", "*")                               // 允许指定域访问跨域资源
                    .setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE,PUT")// 允许所有请求方式
                    .setHeader("Access-Control-Max-Age", "3600")                            // 有效时间
                    .setHeader("Access-Control-Allow-Headers", "*")                        // 允许的header参数
                    .setHeader("Access-Control-Expose-Headers", "Content-Disposition"); // 暴露 Content-Disposition 响应头
            SaRouter.match(SaHttpMethod.OPTIONS)
                    .back();
        };
    }
}
