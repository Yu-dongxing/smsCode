package com.wzz.smscode.module.factory;

import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.module.strategy.auth.AuthStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class AuthFactory {

    @Autowired
    private ApplicationContext context;

    /**
     * 根据认证类型获取对应的认证策略实例
     *
     * @param authType 认证类型
     * @return 认证策略实例
     */
    public AuthStrategy getStrategy(AuthType authType) {
        if (authType == null) {
            // 默认返回无认证策略
            return context.getBean(AuthType.NO_AUTH.getValue(), AuthStrategy.class);
        }
        // 从Spring容器中根据Bean名称获取策略实例
        AuthStrategy strategy = context.getBean(authType.getValue(), AuthStrategy.class);
        if (strategy == null) {
            throw new IllegalArgumentException("未找到对应的认证策略: " + authType.getValue());
        }
        return strategy;
    }
}