package com.wzz.smscode.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 通用响应结果类
 *
 * @param <T> 响应数据的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private Integer code;       // 状态码
    private String message;     // 提示信息
    private T data;             // 数据

    /**
     * 成功响应，携带数据
     *
     * @param data 响应数据
     * @param <E>  数据类型
     * @return Result对象
     */
    public static <E> Result<E> success(E data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功响应，携带自定义消息和数据
     *
     * @param message 自定义消息
     * @param data    响应数据
     * @param <E>     数据类型
     * @return Result对象
     */
    public static <E> Result<E> success(String message, E data) {
        return new Result<>(200, message, data);
    }

    /**
     * 成功响应，不携带数据
     *
     * @return Result对象
     */
    public static Result success() {
        return new Result(200, "操作成功", null);
    }

    /**
     * 失败响应，携带自定义消息
     *
     * @param message 错误消息
     * @return Result对象
     */
    public static Result error(String message) {
        return new Result(400, message, null);
    }

    /**
     * 失败响应，携带自定义状态码和消息
     *
     * @param code    状态码
     * @param message 错误消息
     * @return Result对象
     */
    public static Result error(int code, String message) {
        return new Result(code, message, null);
    }

    public static Result<?> error(int value, String message, Map<String, Object> errorDetails) {
        return new Result<>(value, message, errorDetails);
    }
}