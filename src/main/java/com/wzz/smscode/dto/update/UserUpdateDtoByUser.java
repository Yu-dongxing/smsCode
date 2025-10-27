package com.wzz.smscode.dto.update;

import lombok.Data;

@Data
public class UserUpdateDtoByUser {
    /*
    id
     */
    private Long id;
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String  password;
    /**
     * 否具有代理权限。0表示普通用户，1表示代理用户。
     */
    private boolean isAgent;
    /**、
     * 用户状态（0=正常，1=冻结/禁用等）
     */
    private Integer status;
}
