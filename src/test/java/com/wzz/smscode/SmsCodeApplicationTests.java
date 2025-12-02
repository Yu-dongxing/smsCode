package com.wzz.smscode;

import com.wzz.smscode.dto.api.PhoneInfo;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest
class SmsCodeApplicationTests {



    @Test
    void contextLoads() {
        System.out.println("----------- API 客户端测试开始 -----------");
        String code="暂无验证码456153";
        System.out.println(code.matches("^\\d{4,8}$"));
    }
}
