package com.wzz.smscode.enums;
import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * API认证类型枚举
 */
@Getter
public enum AuthType {

    /**
     * 表示无认证的API认证类型。
     * 该枚举值用于标识不需要进行任何认证即可访问的API。
     */
    NO_AUTH("NO_AUTH", "无认证"),
    /**
     * 表示使用基本认证方式，其中用户名和密码通过URL参数传递的API认证类型。
     * 该枚举值用于标识需要通过在地址栏中以参数形式提供用户名和密码进行认证的API访问请求。
     */
    BASIC_AUTH_PARAM("BASIC_AUTH_PARAM", "用户名密码（地址栏）"),
    /**
     * 表示使用基本认证方式，其中用户名和密码通过JSON格式传递的API认证类型。
     * 该枚举值用于标识需要通过在请求体中以JSON格式提供用户名和密码进行认证的API访问请求。
     */
    BASIC_AUTH_JSON("BASIC_AUTH_JSON", "用户名密码（JSON）"),
    /**
     * 表示使用Token认证方式，其中Token通过请求头传递的API认证类型。
     * 该枚举值用于标识需要通过在HTTP请求头中提供Token进行认证的API访问请求。
     */
    TOKEN_HEADER("TOKEN_HEADER", "Token（Header）"),
    /**
     * 表示使用Token认证方式，其中Token通过URL参数传递的API认证类型。
     * 该枚举值用于标识需要通过在地址栏中以参数形式提供Token进行认证的API访问请求。
     */
    TOKEN_PARAM("TOKEN_PARAM", "Token（地址栏）");

    /**
     * 存储到数据库的值
     */
    @EnumValue // 标记数据库存的值是code
    @JsonValue  // 标记json返回的值是code
    private final String value;

    /**
     * 描述信息
     */
    private final String description;

    AuthType(String value, String description) {
        this.value = value;
        this.description = description;
    }
}