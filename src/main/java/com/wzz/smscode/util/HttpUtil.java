package com.wzz.smscode.util;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 请求工具类，基于 OkHttp 封装 (支持自定义 Header)
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
        return get(url, null); // 调用下面的重载方法，传入空的headers
    }

    /**
     * 发送带自定义请求头的 HTTP GET 请求
     *
     * @param url     请求的 URL
     * @param headers 请求头
     * @return 响应内容字符串，请求失败则返回 null
     */
    public static String get(String url, Headers headers) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null) {
            requestBuilder.headers(headers);
        }
        Request request = requestBuilder.build();

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
        return post(url, json, null); // 调用下面的重载方法，传入空的headers
    }

    /**
     * 发送带自定义请求头的 HTTP POST 请求 (JSON body)
     *
     * @param url     请求的 URL
     * @param json    请求体 JSON 字符串
     * @param headers 请求头
     * @return 响应内容字符串，请求失败则返回 null
     */
    public static String post(String url, String json, Headers headers) {
        RequestBody body = RequestBody.create(json, JSON);
        Request.Builder requestBuilder = new Request.Builder().url(url).post(body);
        if (headers != null) {
            requestBuilder.headers(headers);
        }
        Request request = requestBuilder.build();

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