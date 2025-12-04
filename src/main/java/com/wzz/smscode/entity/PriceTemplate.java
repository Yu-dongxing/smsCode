package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.ColumnType;
import com.wzz.smscode.annotation.DefaultValue;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目线路价格配置模板
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("price_template")
@TableComment("项目线路价格配置模板表")
public class PriceTemplate extends BaseEntity {

    /**
     * 模板名称
     */
    @ColumnComment("模板名称")
    @TableField("name")
    private String name;
    /**
     * 模板创建者id(管理员为0)
     */
    @DefaultValue("0")
    @TableField("creat_id")
    private Long creatId;

    /**
     * 使用该模板的用户ID列表
     * 格式: "1001,1002,1005"
     */
    @ColumnComment("关联用户ID列表")
    @TableField("user_ids")
    @ColumnType("TEXT")
    private String userIds;
}