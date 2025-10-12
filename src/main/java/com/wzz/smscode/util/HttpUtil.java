package com.wzz.smscode.util;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 请求工具类，基于 OkHttp 封装
 */
@Slf4j
public final class HttpUtil {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // 连接超时
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时
            .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时
            .build();

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private HttpUtil() {
        // 防止实例化
    }

    /**
     * 发送 HTTP GET 请求
     *
     * @param url 请求的 URL
     * @return 响应内容字符串，请求失败则返回 null
     */
    public static String get(String url) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return Objects.requireNonNull(response.body()).string();
            } else {
                log.error("HTTP GET 请求失败: URL={}, Code={}, Message={}", url, response.code(), response.message());
                return null;
            }
        } catch (IOException e) {
            log.error("HTTP GET 请求异常: URL={}", url, e);
            return null;
        }
    }

    /**
     * 发送 HTTP POST 请求 (JSON body)
     *
     * @param url  请求的 URL
     * @param json 请求体 JSON 字符串
     * @return 响应内容字符串，请求失败则返回 null
     */
    public static String post(String url, String json) {
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return Objects.requireNonNull(response.body()).string();
            } else {
                log.error("HTTP POST 请求失败: URL={}, Code={}, Message={}", url, response.code(), response.message());
                return null;
            }
        } catch (IOException e) {
            log.error("HTTP POST 请求异常: URL={}", url, e);
            return null;
        }
    }
}