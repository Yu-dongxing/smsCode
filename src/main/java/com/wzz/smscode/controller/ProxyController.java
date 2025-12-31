package com.wzz.smscode.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzz.smscode.dto.RequestDTO.KeyValue;
import com.wzz.smscode.dto.RequestDTO.ProxyRequestConfig;
import com.wzz.smscode.dto.RequestDTO.RequestUrlDTO;
import com.wzz.smscode.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class ProxyController {

    @Resource
    private RestTemplate restTemplate;

    // Jackson 用于解析 JSON 字符串
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 统一反向代理接口
     * 接收前端的完整配置 JSON 字符串，解析后发起请求并返回结果
     */
    @PostMapping("/request/url")
    public ResponseEntity<?> requestUrl(@RequestBody RequestUrlDTO requestUrlDTO) {
        try{
            String configJson = requestUrlDTO.getData();

            if (configJson == null || configJson.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("请求配置不能为空");
            }

            try {
                // 1. 将前端传来的 JSON 字符串解析为配置对象
                ProxyRequestConfig config = objectMapper.readValue(configJson, ProxyRequestConfig.class);

                // 2. 构建 URL (处理 Query Params)
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(config.getUrl());
                if (config.getParams() != null) {
                    for (KeyValue entry : config.getParams()) {
                        if (entry.getKey() != null && !entry.getKey().isEmpty()) {
                            uriBuilder.queryParam(entry.getKey(), entry.getValue());
                        }
                    }
                }
                URI targetUrl = uriBuilder.build().encode().toUri();

                // 3. 构建 Headers
                HttpHeaders headers = new HttpHeaders();
                if (config.getHeaders() != null) {
                    for (KeyValue entry : config.getHeaders()) {
                        if (entry.getKey() != null && !entry.getKey().isEmpty()) {
                            headers.add(entry.getKey(), entry.getValue());
                        }
                    }
                }

                // 4. 构建 Body (根据 BodyType 处理)
                Object requestBody = null;
                String bodyType = config.getBodyType() != null ? config.getBodyType() : "NONE";

                switch (bodyType) {
                    case "JSON":
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        requestBody = config.getJsonBody();
                        break;
                    case "X_WWW_FORM":
                        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                        requestBody = convertToMultiValueMap(config.getFormBody());
                        break;
                    case "FORM_DATA":
                        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                        requestBody = convertToMultiValueMap(config.getFormBody());
                        break;
                    case "NONE":
                    default:
                        break;
                }
                // 5. 组装 HttpEntity
                HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
                // 6. 发起请求
                HttpMethod method = HttpMethod.valueOf(config.getMethod().toUpperCase());
                // 使用 String.class 接收响应，方便前端展示
                ResponseEntity<String> response = restTemplate.exchange(
                        targetUrl,
                        method,
                        entity,
                        String.class
                );
                log.info("反向代理接口调用返回：{}",response);

                // 7. 原样返回第三方接口的响应（包含状态码、Header、Body）
                return ResponseEntity.status(response.getStatusCode())

                        .body(response.getBody());
            } catch (Exception e) {
                log.info(e.getMessage());
                // 捕获异常，返回 500 和错误信息
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("代理请求执行失败: " + e.getMessage());
            }
        }catch (Exception e){
            throw  new BusinessException(0,"请求失败："+e.getMessage());
        }

    }

    // 辅助方法：将 List<KeyValue> 转为 MultiValueMap
    private MultiValueMap<String, Object> convertToMultiValueMap(List<KeyValue> list) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        if (list != null) {
            for (KeyValue entry : list) {
                if (entry.getKey() != null && !entry.getKey().isEmpty()) {
                    map.add(entry.getKey(), entry.getValue());
                }
            }
        }
        return map;
    }
}