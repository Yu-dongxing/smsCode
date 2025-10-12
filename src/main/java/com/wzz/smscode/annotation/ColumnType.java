package com.wzz.smscode.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于在实体类字段上直接指定其数据库列类型。
 * 当此注解存在时，它的优先级将高于默认的类型映射规则。
 */
@Retention(RetentionPolicy.RUNTIME) // 确保注解在运行时可见，以便通过反射读取
@Target(ElementType.FIELD)          // 限定此注解只能用于类的字段上
public @interface ColumnType {
    /**
     * 定义数据库的列类型，例如 "MEDIUMTEXT", "VARCHAR(1000)", "TEXT" 等。
     */
    String value();
}