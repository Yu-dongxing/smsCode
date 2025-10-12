package com.wzz.smscode.annotation;


import com.wzz.smscode.enums.ForeignKeyAction;

import java.lang.annotation.*;

/**
 * 数据库外键约束注解
 * // ▼▼▼ 定义外键 ▼▼▼
 * @ForeignKey(
 *     name = "fk_account_user_id",               // 约束名称
 *     columns = {"user_id"},                     // 当前表的列
 *     referenceEntity = User.class,              // 引用 User 实体
 *     referencedColumns = {"id"},                // 引用 User 表的 id 列
 *     onDelete = ForeignKeyAction.CASCADE,       // 当 User 被删除时，该用户的账户也一并删除
 *     onUpdate = ForeignKeyAction.RESTRICT       // 不允许更新 User 的 ID
 * )
 * 可在实体类上重复使用，用于定义多个外键
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(ForeignKeys.class) // ▼▼▼ 允许在同一个类上定义多个@ForeignKey注解 ▼▼▼
public @interface ForeignKey {

    /**
     * 外键约束的名称，必须在数据库中唯一
     */
    String name();

    /**
     * 当前实体对应表中的列名
     */
    String[] columns();

    /**
     * 引用的目标实体类
     */
    Class<?> referenceEntity();

    /**
     * 引用目标实体对应表中的列名
     */
    String[] referencedColumns();

    /**
     * ON DELETE 触发的操作，默认为 RESTRICT
     */
    ForeignKeyAction onDelete() default ForeignKeyAction.RESTRICT;

    /**
     * ON UPDATE 触发的操作，默认为 RESTRICT
     */
    ForeignKeyAction onUpdate() default ForeignKeyAction.RESTRICT;
}
