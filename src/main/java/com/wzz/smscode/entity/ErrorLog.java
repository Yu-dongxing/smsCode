package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.wzz.smscode.annotation.*;
import com.wzz.smscode.common.BaseEntity;
import com.wzz.smscode.enums.ForeignKeyAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 异常错误日志表实体类
 * <p>
 * 记录系统运行时出现的异常错误信息，供开发人员排查问题。
 * </p>
 * 对应数据库表：error_log
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("error_log")
@TableComment("异常错误日志表")
@ForeignKey(
        name = "fk_log_user_id",
        columns = {"user_id"},
        referenceEntity = User.class,
        referencedColumns = {"id"},
        onDelete = ForeignKeyAction.SET_NULL, // 用户删除后，日志中的user_id设为NULL，以保留日志记录
        onUpdate = ForeignKeyAction.RESTRICT
)
@Index(name = "idx_error_time", columns = {"error_time"}, comment = "错误时间索引，便于按时间范围查询")
@Index(name = "idx_error_module", columns = {"error_module"}, comment = "出错模块索引，便于分类查询")
public class ErrorLog extends BaseEntity {

    // 主键 id (对应 error_id) 从 BaseEntity 继承

    /**
     * 出错方法或功能名称，指明异常发生的代码位置
     */
    @ColumnComment("出错方法或功能名称")
    @TableField("error_function")
    private String errorFunction;

    /**
     * 出错模块/包名，指明异常来源的大类
     */
    @ColumnComment("出错模块/包名")
    @TableField("error_module")
    private String errorModule;

    /**
     * 异常堆栈信息
     */
    @ColumnComment("异常堆栈信息")
    @TableField("stack_trace")
    @ColumnType("TEXT")
    private String stackTrace; // 数据库中建议使用 TEXT 或 LONGTEXT 类型

    /**
     * 错误内容简述
     */
    @ColumnComment("错误内容简述")
    @TableField("error_message")
    @ColumnType("TEXT")
    private String errorMessage; // 数据库中建议使用 TEXT 类型

    /**
     * 错误发生时间
     */
    @ColumnComment("错误发生时间")
    @TableField("error_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime errorTime;

    /**
     * 受影响的用户ID（如果适用），否则为空
     */
    @ColumnComment("受影响的用户ID")
    @TableField("user_id")
    private Long userId;
}