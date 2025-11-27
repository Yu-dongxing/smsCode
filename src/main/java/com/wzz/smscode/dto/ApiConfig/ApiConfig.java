package com.wzz.smscode.dto.ApiConfig;

import com.wzz.smscode.dto.RequestDTO.KeyValue;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ApiConfig {
    private String url;
    private String method; // GET, POST, etc.
    private List<KeyValue> params = new ArrayList<>();
    private List<KeyValue> headers = new ArrayList<>();
    private String bodyType; // NONE, JSON, FORM_DATA, X_WWW_FORM
    private String jsonBody;
    private List<KeyValue> formBody = new ArrayList<>();
    private List<ExtractRule> extractRules = new ArrayList<>();
    private List<KeyValue> preHooks = new ArrayList<>(); // 前置变量设置
}
