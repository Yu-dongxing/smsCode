package com.wzz.smscode.dto.RequestDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxyRequestConfig {
    private String url;
    private String method; // GET, POST, PUT, DELETE
    private String bodyType; // JSON, FORM_DATA, X_WWW_FORM, NONE
    private String jsonBody;

    // 对应前端的 key-value-editor 数组
    private List<KeyValue> params;
    private List<KeyValue> headers;
    private List<KeyValue> formBody;
}
