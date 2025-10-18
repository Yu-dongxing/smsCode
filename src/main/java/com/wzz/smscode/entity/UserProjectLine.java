package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.DefaultValue;
import com.wzz.smscode.annotation.ForeignKey;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
import com.wzz.smscode.enums.ForeignKeyAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户项目线路对应表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_project_line")
@TableComment("用户项目线路对应表")
@ForeignKey(
        name = "fk_project_line_user_id",
        columns = {"user_id"},
        referenceEntity = User.class,       // 引用 User 实体
        referencedColumns = {"id"},         // 引用 User 表的 id 列 (根据您的最新说明)
        onDelete = ForeignKeyAction.CASCADE,  // 用户删除时，其用户项目对应记录也一并删除，保证数据清理
        onUpdate = ForeignKeyAction.RESTRICT
)
public class UserProjectLine extends BaseEntity {

    /**
     * 用户ID
     */
    @ColumnComment("用户ID")
    @TableField("user_id")
    private Long userId;


    /**
     * 项目ID
     */
    @ColumnComment("项目名称")
    @TableField("project_name")
    private String projectName;

    /**
     * 项目ID
     */
    @ColumnComment("项目ID")
    @TableField("project_id")
    private String projectId;

    /**
     * 项目线路ID
     */
    @ColumnComment("项目线路ID")
    @TableField("line_id")
    private String lineId;

    /**
     * 用户自定义售价，为空时走项目默认售价
     */
    @ColumnComment("代理自定义售价")
    @TableField("agent_price")
    @DefaultValue("0.00")
    private BigDecimal AgentPrice;

    /**
     * 项目成本价（平台获取号码的成本）
     */
    @ColumnComment("项目成本价")
    @TableField("cost_price")
    @DefaultValue("0.00")
    private BigDecimal costPrice;


    /**
     * 备注
     */
    @ColumnComment("备注")
    @TableField("remark")
    private String remark;
}