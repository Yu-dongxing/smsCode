package com.wzz.smscode.dto.LoginDTO;


import lombok.Data;

/**
 * 代理登录数据传输对象
 */
@Data
public class AgentLoginDTO {
    private String username; // 这里我们假设代理使用 username 登录，也可以是手机号、邮箱等
    private String password;
}