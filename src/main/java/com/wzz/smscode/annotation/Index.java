package com.wzz.smscode.annotation;



import com.wzz.smscode.enums.IndexType;

import java.lang.annotation.*;

/**
 * 数据库索引注解
 * @TableName("user_sign")
 * @TableComment("用户签到记录表") // ▼▼▼ 表注释
 * // ▼▼▼ 联合唯一索引：确保一个用户一天只能签到一次
 * @Index(name = "uk_user_date", columns = {"user_id", "sign_date"}, type = IndexType.UNIQUE, comment = "用户ID和签到日期的联合唯一索引")
 * // ▼▼▼ 普通索引：加速按用户查询
 * @Index(name = "idx_user_id", columns = {"user_id"}, comment = "用户ID普通索引")
 * 可在实体类上重复使用，用于定义索引
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Indexes.class) // ▼▼▼ 关键点：允许在同一个类上定义多个@Index注解 ▼▼▼
public @interface Index {

    /**
     * 索引名称
     */
    String name();

    /**
     * 组成索引的字段名（对应数据库列名）
     */
    String[] columns();

    /**
     * 索引类型，默认为普通索引
     */
    IndexType type() default IndexType.NORMAL;

    /**
     * 索引的注释
     */
    String comment() default "";
}