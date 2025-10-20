package com.wzz.smscode.module.strategy.auth;

import com.wzz.smscode.entity.Project;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import java.util.Map;
@Component("TOKEN_HEADER") // Bean名称与AuthType的value对应
public class TokenHeaderAuthStrategy implements AuthStrategy {
    @Override
    public void applyAuth(Project project, HttpHeaders headers, MultiValueMap<String, String> params, Map<String, Object> body) {
        String tokenValue = project.getAuthTokenValue();
        if (tokenValue != null && !tokenValue.isEmpty()) {
            String prefix = project.getAuthTokenPrefix() != null ? project.getAuthTokenPrefix() : "";
            headers.add(project.getAuthTokenField(), prefix + tokenValue);
        }
    }
}