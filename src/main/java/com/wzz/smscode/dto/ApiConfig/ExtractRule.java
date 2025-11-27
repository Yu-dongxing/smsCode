package com.wzz.smscode.dto.ApiConfig;

import lombok.Data;

@Data
public class ExtractRule {
    private String targetVariable; // 存入的变量名，如 token, phone
    private String source; // BODY, HEADER
    private String jsonPath; // $.data.token
}