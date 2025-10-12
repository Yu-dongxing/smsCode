package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.DefaultValue;
import com.wzz.smscode.annotation.TableComment;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 系统配置表实体类
 * <p>
 * 存储全局配置和控制开关，将所有配置作为单行记录保存。
 * 注意：此类未继承BaseEntity，因为它采用固定主键(config_id=1)，而非自增ID。
 * </p>
 * 对应数据库表：system_config
 */
@Data
@TableName("system_config")
@TableComment("系统配置表")
public class SystemConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，固定为 1（全局唯一配置项）
     */
    @ColumnComment("主键ID (固定为1)")
    @TableId(value = "config_id", type = IdType.INPUT) // INPUT类型表示该ID由用户手动设置，非自增
    private Integer configId;

    /**
     * 筛选号码的外部API接口 URL
     */
    @ColumnComment("号码筛选API的URL")
    @TableField("filter_api_url")
    private String filterApiUrl;

    /**
     * 筛选API所需的鉴权密钥或卡密
     */
    @ColumnComment("号码筛选API的密钥")
    @TableField("filter_api_key")
    private String filterApiKey;

    /**
     * 是否开启封禁模式标志。1 表示开启，0 表示关闭。
     */
    @ColumnComment("是否开启封禁模式(0=关闭, 1=开启)")
    @TableField("enable_ban_mode")
    @DefaultValue("0")
    private Integer enableBanMode;

    /**
     * 24小时最低回码率限制值
     */
    @ColumnComment("24小时最低回码率")
    @TableField("min_24h_code_rate")
    @DefaultValue("0.0")
    private Double min24hCodeRate;

    /**
     * 余额封控下限值
     */
    @ColumnComment("余额封控下限值")
    @TableField("balance_threshold")
    @DefaultValue("0.00")
    private BigDecimal balanceThreshold;
}