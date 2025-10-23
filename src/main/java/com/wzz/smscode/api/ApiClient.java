package com.wzz.smscode.api;

import com.alibaba.fastjson.JSONObject; // 引入 Fastjson
import com.wzz.smscode.util.HttpUtil; // 假设 HttpUtil 在这个包下
import okhttp3.Headers;

import java.util.Collections;
import java.util.List;

/**
 * API 请求服务封装类 (使用 Fastjson)
 */
public class ApiClient {

    private static final String BASE_URL = "http://168.119.194.190:8080";

    /**
     * 1. 用户登录接口
     *
     * @param username 用户名
     * @param password 密码
     * @return 响应的JSON字符串，可从中提取token
     */
    public static String login(String username, String password) {
        String url = BASE_URL + "/login";

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username", username);
        jsonObject.put("password", password);
        String jsonBody = jsonObject.toJSONString(); // 使用 toJSONString()

        return HttpUtil.post(url, jsonBody, null);
    }

    /**
     * 2. 查询余额接口
     *
     * @param token 认证令牌
     * @return 响应的JSON字符串
     */
    public static String queryBalance(String token) {
        String url = BASE_URL + "/trans/user/qryBalance";
        Headers headers = createAuthHeaders(token);
        return HttpUtil.get(url, headers);
    }

    /**
     * 3. 获取手机号接口
     *
     * @param token     认证令牌
     * @param projectId 项目ID
     * @param num       获取数量（必须为1）
     * @return 响应的JSON字符串
     */
    public static String getPhone(String token, int projectId, int num) {
        String url = String.format("%s/trans/user/getPhone/%d/%d", BASE_URL, projectId, num);
        Headers headers = createAuthHeaders(token);
        return HttpUtil.get(url, headers);
    }

    /**
     * 4. 查询手机号记录接口
     *
     * @param token    认证令牌
     * @param uuidList UUID列表
     * @return 响应的JSON字符串
     */
    public static String queryPhoneRecords(String token, List<String> uuidList) {
        String url = BASE_URL + "/system/phoneRecord/qryByUuid";
        Headers headers = createAuthHeaders(token);

        // 构建请求体
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("uuidList", uuidList);
        String jsonBody = jsonObject.toJSONString(); // 使用 toJSONString()

        return HttpUtil.post(url, jsonBody, headers);
    }

    /**
     * 5. 释放手机号接口
     *
     * @param token 认证令牌
     * @param phone 要释放的手机号
     * @return 响应的JSON字符串
     */
    public static String releasePhone(String token, String phone) {
        String url = String.format("%s/trans/user/releasePhone?phone=%s", BASE_URL, phone);
        Headers headers = createAuthHeaders(token);
        return HttpUtil.get(url, headers);
    }

    /**
     * 创建包含认证信息的请求头
     *
     * @param token 认证令牌
     * @return Headers 对象
     */
    private static Headers createAuthHeaders(String token) {
        return new Headers.Builder()
                .add("Authorization", "Bearer " + token)
                .build();
    }

    // --- main 方法：调用示例 ---
    public static void main(String[] args) {
        // 示例：登录并获取Token pjh23  pjh234
        String loginResponse = login("pjh23", "pjh234");
        System.out.println("登录响应: " + loginResponse);

        if (loginResponse == null) {
            System.out.println("登录请求失败，请检查网络或服务地址。");
            return;
        }
        // 使用 Fastjson 解析响应
        JSONObject loginJson = JSONObject.parseObject(loginResponse);
        String token = loginJson.getString("token");

        if (token != null && !token.isEmpty()) {
            System.out.println("获取到Token: " + token);

            // 示例：使用Token查询余额
            String balanceResponse = queryBalance(token);
            System.out.println("查询余额响应: " + balanceResponse);

            //获取手机号 (项目ID为6)
//            String phoneResponse = getPhone(token, 6, 1);
//            System.out.println("获取手机号响应: " + phoneResponse);
//
//            // 示例：查询手机号记录
//            // 假设从上一步获取到了 extraParams
//            String uuid = "07405534-2e4c-4e84-b0b3-a88a5442a454"; // 此处应为动态获取
//            String recordResponse = queryPhoneRecords(token, Collections.singletonList(uuid));
//            System.out.println("查询手机号记录响应: " + recordResponse);

        } else {
            System.out.println("登录失败或未能获取Token: " + loginJson.getString("msg"));
        }
    }
}