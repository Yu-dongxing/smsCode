package com.wzz.smscode.common;

/**
 * 系统常量定义类
 */
public final class Constants {

    private Constants() {}

    // --- 通用返回状态码 ---
    public static final int SUCCESS = 0;
    public static final int ERROR_NO_NUMBER = -1;
    public static final int ERROR_NO_CODE = -2;
    public static final int ERROR_AUTH_FAILED = -3;
    public static final int ERROR_INSUFFICIENT_BALANCE = -4;
    public static final int ERROR_SYSTEM_ERROR = -5;
}