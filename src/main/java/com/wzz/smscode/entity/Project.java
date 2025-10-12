package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.DefaultValue;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 项目表实体类
 * <p>
 * 每个项目代表一个可提供手机号码/验证码服务的来源。
 * 注意：(project_id, line_id) 构成复合主键，确保项目下线路的唯一性。
 * </p>
 * 对应数据库表：project
 */
@Data
@EqualsAndHashCode(callSuper = true) // 继承自BaseEntity，建议保留
@TableName("project")
@TableComment("项目表")
public class Project extends BaseEntity {

    /**
     * 项目ID，例如 "id0001" 表示某平台
     */
    @ColumnComment("项目ID")
    @TableField("project_id")
    private String projectId;

    /**
     * 项目线路ID，用于区分同一项目下不同的API线路
     */
    @ColumnComment("项目线路ID")
    @TableField("line_id")
    private Integer lineId;

    /**
     * 提供服务的域名或基础URL
     */
    @ColumnComment("服务域名")
    @TableField("domain")
    private String domain;

    /**
     * 获取手机号的接口路径
     */
    @ColumnComment("获取手机号的接口路径")
    @TableField("get_number_route")
    private String getNumberRoute;

    /**
     * 获取验证码的接口路径
     */
    @ColumnComment("获取验证码的接口路径")
    @TableField("get_code_route")
    private String getCodeRoute;

    /**
     * 获取验证码超时时长，单位为秒
     */
    @ColumnComment("获取验证码超时时长（秒）")
    @TableField("code_timeout")
    private Integer codeTimeout;

    /**
     * 项目成本价（平台获取号码的成本）
     */
    @ColumnComment("项目成本价")
    @TableField("cost_price")
    private BigDecimal costPrice;

    /**
     * 项目允许设置的最高售价
     */
    @ColumnComment("项目允许设置的最高售价")
    @TableField("price_max")
    private BigDecimal priceMax;

    /**
     * 项目允许设置的最低售价
     */
    @ColumnComment("项目允许设置的最低售价")
    @TableField("price_min")
    private BigDecimal priceMin;

    /**
     * 是否开启号码筛选功能。0表示关闭，1表示开启。
     */
    @ColumnComment("是否开启号码筛选（0-关闭, 1-开启）")
    @TableField("enable_filter")
    @DefaultValue("0")
    private Integer enableFilter; // 使用 Integer 对应 TINYINT，便于处理 0 和 1

    /**
     * 筛选API所需的ID或密钥
     */
    @ColumnComment("筛选API的ID或密钥")
    @TableField("filter_id")
    private String filterId;
}