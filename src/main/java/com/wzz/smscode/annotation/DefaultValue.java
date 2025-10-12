package com.wzz.smscode.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于在实体类字段上指定其数据库列的默认值。
 * 当此注解存在时，会在生成 CREATE TABLE 或 ALTER TABLE 语句时添加 "DEFAULT 'value'" 子句。
 */
@Retention(RetentionPolicy.RUNTIME) // 确保注解在运行时可见，以便通过反射读取
@Target(ElementType.FIELD)          // 限定此注解只能用于类的字段上
public @interface DefaultValue {
    /**
     * 定义数据库列的默认值。
     * 注意：如果默认值是字符串类型，需要在这里包含单引号，例如 "'default_text'"。
     * 如果是数字，则直接写数字，例如 "0"。
     * 如果是SQL函数，则直接写函数名，例如 "CURRENT_TIMESTAMP"。
     */
    String value();
}