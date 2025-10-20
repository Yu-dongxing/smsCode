package com.wzz.smscode;

import com.wzz.smscode.dto.api.PhoneInfo;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.module.ApiRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest
class SmsCodeApplicationTests {


    @Autowired
    private ApiRequestService apiRequestService;

    @Test
    void contextLoads() {
        System.out.println("----------- API 客户端测试开始 -----------");

        // 1. 手动构建一个Project对象，模拟从数据库读取
        Project project = new Project();
        project.setId(1L);
        project.setProjectId("example_platform_01");
        project.setLineId("default_line");
        project.setDomain("http://212.224.125.197:8080");
        project.setLoginRoute("/login");
        project.setGetNumberRoute("/trans/user/getPhone/{projectId}/{num}");
        project.setGetCodeRoute("/system/phoneRecord/qryByUuid");
        project.setCodeTimeout(180); // 180秒超时
        project.setAuthType(AuthType.TOKEN_HEADER);
        project.setAuthUsernameField("username");
        project.setAuthPasswordField("password");
        project.setAuthUsername("pjh23");
        project.setAuthPassword("pjh234");
        project.setAuthTokenPrefix("Bearer ");
        // project.setAuthTokenValue(null); // 初始Token为空，会自动登录



        try {
            // --- 完整业务流程测试 ---

            // 步骤 A: 查询余额
//            System.out.println("\n[步骤A] 正在查询余额...");
//            String balanceResponse = apiRequestService.queryBalance(project);
//            System.out.println("查询余额成功, 响应: " + balanceResponse);

            // 步骤 B: 获取手机号
            System.out.println("\n[步骤B] 正在获取手机号...");
            Map<String, Object> pathVariables = new HashMap<>();
            pathVariables.put("projectId", 24); // 根据文档，项目ID为24
            pathVariables.put("num", 1);       // 数量为1
            PhoneInfo phoneInfo = apiRequestService.getPhoneNumber(project, pathVariables);
            System.out.println("获取手机号成功! 手机号: " + phoneInfo.getPhone() + ", UUID: " + phoneInfo.getUuid());

            // 步骤 C: 获取验证码 (将开始轮询)
//            System.out.println("\n[步骤C] 正在获取验证码 (将开始轮询，请在 " + project.getCodeTimeout() + " 秒内让该号码接收到验证码)...");
//            String code = apiRequestService.getVerificationCode(project, phoneInfo.getUuid());
//            System.out.println("获取验证码成功! 验证码: " + code);

//            // 步骤 D: 释放手机号
//            System.out.println("\n[步骤D] 正在释放手机号...");
//            apiRequestService.releasePhone(project, phoneInfo.getPhone());

        } catch (Exception e) {
            System.err.println("\n[错误] API 调用流程中断: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("----------- API 客户端测试结束 -----------");
    }
}
