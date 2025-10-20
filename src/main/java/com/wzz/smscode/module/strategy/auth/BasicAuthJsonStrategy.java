package com.wzz.smscode.module.strategy.auth;

import com.wzz.smscode.entity.Project;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.Map;

@Component("BASIC_AUTH_JSON")
public class BasicAuthJsonStrategy implements AuthStrategy {
    @Override
    public void applyAuth(Project project, HttpHeaders headers, MultiValueMap<String, String> params, Map<String, Object> body) {
        if (body != null) {
            body.put(project.getAuthUsernameField(), project.getAuthUsername());
            body.put(project.getAuthPasswordField(), project.getAuthPassword());
        }
    }
}