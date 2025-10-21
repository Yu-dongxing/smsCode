package com.wzz.smscode.moduleService.strategy;

import com.wzz.smscode.enums.AuthType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AuthStrategyFactory {

    private final Map<AuthType, AuthStrategy> strategyMap;

    @Autowired
    public AuthStrategyFactory(List<AuthStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(AuthStrategy::getAuthType, Function.identity()));
    }

    public AuthStrategy getStrategy(String authTypeCode) {
        AuthType authType = AuthType.fromCode(authTypeCode);
        AuthStrategy strategy = strategyMap.get(authType);
        if (strategy == null) {
            throw new UnsupportedOperationException("不支持的认证类型: " + authTypeCode);
        }
        return strategy;
    }
}