package com.wzz.smscode.enums;

/**
 * 索引类型枚举
 */
public enum IndexType {
    /**
     * 普通索引
     */
    NORMAL,
    /**
     * 唯一索引
     */
    UNIQUE,
    /**
     * 全文索引 (仅适用于 CHAR, VARCHAR, TEXT 类型字段)
     */
    FULLTEXT
}