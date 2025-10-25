package com.wzz.smscode.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 接口请求参数类型枚举
 */
@Getter
public enum RequestType {

    /**
     * 以 application/json 格式提交
     */
    JSON("JSON", "JSON格式"),

    /**
     * 以 application/x-www-form-urlencoded 格式提交
     */
    FORM("FORM", "表单格式"),

    /**
     * 无参数
     */
    NONE("NONE", "无参"),

    /**
     * 以 key=value 的形式拼接在URL后面
     */
    PARAM("PARAM", "URL参数");



    @EnumValue // 标记数据库存的值是 code
    @JsonValue
    private final String code;


    private final String desc;

    RequestType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}