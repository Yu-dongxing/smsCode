package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.ColumnType;
import com.wzz.smscode.annotation.DefaultValue;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
import com.wzz.smscode.dto.ApiConfig.ApiConfig;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.enums.RequestType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目表实体类 (已重构增强)
 * <p>
 * 该实体类用于统一配置和管理所有对接的第三方短信验证码平台项目。
 * 它通过丰富的配置项，实现了对不同平台API的灵活适配，
 * 包括认证方式、接口路径、请求方法、参数类型、响应解析等。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "project", autoResultMap = true)
@TableComment("项目表")
public class Project extends BaseEntity {

    // --- 基础信息 ---

    /**
     * 项目ID，例如 'id0001'，用于唯一标识一个平台
     */
    @ColumnComment("项目ID，例如 id0001，用于唯一标识一个平台")
    @TableField("project_id")
    private String projectId;

    /**
     * 项目名称
     */
    @ColumnComment("项目名称")
    @TableField("project_name")
    private String projectName;

    /**
     * 线路名称
     */
    @ColumnComment("线路名称")
    @TableField("line_name")
    @DefaultValue("'默认线路'")
    private String lineName;

    /**
     * 项目线路ID，用于区分同一项目下不同的API线路
     */
    @ColumnComment("项目线路ID，用于区分同一项目下不同的API线路")
    @TableField("line_id")
    private String lineId;

    /**
     * 服务域名或基础URL(仅用于项目区分)
     */
    @ColumnComment("服务域名或基础URL")
    @TableField("domain")
    private String domain;


    /**
     * 筛选号码的api的接口
     */
    @ColumnComment("筛选号码的api的接口")
    @TableField("select_number_api_route")
    private String selectNumberApiRoute;

    /**
     * 筛选号码的api的接口请求方法
     */
    @ColumnComment("筛选号码的api的接口请求方法")
    @TableField("select_number_api_route_method")
    @DefaultValue("'GET'")
    private String selectNumberApiRouteMethod;


    /**
     * 筛选号码的api的接口请求参数类型
     */
    @ColumnComment("筛选号码的api的接口请求参数类型")
    @TableField("select_number_api_reauest_type")
    @DefaultValue("'PARAM'")
    private RequestType selectNumberApiReauestType;


    // --- 认证方式与凭证 ---

    /**
     * 认证类型（NO_AUTH-无认证, BASIC_AUTH_PARAM-用户名密码（地址栏）, BASIC_AUTH_JSON-用户名密码（JSON）, TOKEN_HEADER-Token（请求头）, TOKEN_PARAM-Token（地址栏））
     */
    @ColumnComment("认证类型（NO_AUTH-无认证, BASIC_AUTH_PARAM-用户名密码（地址栏）, BASIC_AUTH_JSON-用户名密码（JSON）, TOKEN_HEADER-Token（请求头）, TOKEN_PARAM-Token（地址栏））")
    @TableField("auth_type")
    private AuthType authType;


    /**
     * 筛选号码的api的接口请求字段
     */
    @ColumnComment("筛选号码的api的接口请求字段")
    @TableField("select_number_api_request_value")
    private String selectNumberApiRequestValue;


    /**
     * 筛选号码的api的接口数据返回字段
     */
    @ColumnComment("筛选号码的api的接口请求字段")
    @TableField("response_select_number_api_field")
    private String responseSelectNumberApiField;


    /**
     * 获取验证码超时时长（秒）
     */
    @ColumnComment("获取验证码超时时长（秒）")
    @TableField("code_timeout")
    private Integer codeTimeout;

    /**
     * 获取验证码的最大尝试次数
     */
    @ColumnComment("获取验证码的最大尝试次数")
    @TableField("code_max_attempts")
    private Integer codeMaxAttempts;

    /**
     * 项目成本价（平台获取号码的成本）
     */
    @ColumnComment("项目成本价（平台获取号码的成本）")
    @TableField("cost_price")
    @DefaultValue("0.00")
    private BigDecimal costPrice;

    /**
     * 项目允许设置的最高售价
     */
    @ColumnComment("项目允许设置的最高售价")
    @TableField("price_max")
    @DefaultValue("0.00")
    private BigDecimal priceMax;

    /**
     * 项目允许设置的最低售价
     */
    @ColumnComment("项目允许设置的最低售价")
    @TableField("price_min")
    @DefaultValue("0.00")
    private BigDecimal priceMin;

    /**
     * 状态 (0-禁用, 1-启用)
     */
    @ColumnComment("状态 (0-禁用, 1-启用)")
    @TableField("status")
    @DefaultValue("1")
    private boolean status;


    // --- 号码筛选配置 ---

    /**
     * 是否开启号码筛选功能 (0-关闭, 1-开启)
     */
    @ColumnComment("是否开启号码筛选功能 (0-关闭, 1-开启)")
    @TableField("enable_filter")
    @DefaultValue("0")
    private Boolean enableFilter;

    /**
     * 筛选API所需的ID或密钥
     */
    @ColumnComment("筛选API所需的ID或密钥")
    @TableField("filter_id")
    private String filterId;


    // === 核心：使用 TypeHandler 映射 JSON 配置 ===

    /**
     * 登录api配置
     */
    @TableField(value = "login_config", typeHandler = JacksonTypeHandler.class)
    @ColumnType("JSON")
    private ApiConfig loginConfig;

    /**
     * 获号api配置
     */
    @TableField(value = "get_number_config", typeHandler = JacksonTypeHandler.class)
    @ColumnType("JSON")
    private ApiConfig getNumberConfig;

    /**
     * 取码api配置
     */
    @TableField(value = "get_code_config", typeHandler = JacksonTypeHandler.class)
    @ColumnType("JSON")
    private ApiConfig getCodeConfig;

    /**
     * 查询余额api配置
     */
    @TableField(value = "get_balance_config", typeHandler = JacksonTypeHandler.class)
    @ColumnType("JSON")
    private ApiConfig getBalanceConfig;

    /**
     * 释放手机号配置
     */
    @TableField(value = "delete_phone_config", typeHandler = JacksonTypeHandler.class)
    @ColumnType("JSON")
    private ApiConfig deletePhoneConfig;

    //***********************释放手机号请求说明
    /**
     * 成功状态值
     */
    @TableField("release_success_status")
    @DefaultValue("1")
    private String releaseSuccessStatus;

    /**
     * 失败状态值
     */
    @TableField("release_fail_status")
    @DefaultValue("0")
    private String releaseFailStatus;

    /**
     * 成功描述文字
     */
    @TableField("release_success_msg")
    @DefaultValue("'ok'")
    private String releaseSuccessMsg;

    /**
     * 失败描述文字
     */
    @TableField("release_fail_msg")
    @DefaultValue("'error'")
    private String releaseFailMsg;

    //***********************释放手机号请求说明


    /**
     * 动态获取的Token值 (由系统自动填充和更新)
     */
    @ColumnComment("动态获取的Token值 (由系统自动填充和更新)")
    @TableField("auth_token_value")
    private String authTokenValue;

    /**
     * 动态Token的过期时间 (由系统自动填充和更新)
     */
    @ColumnComment("动态Token的过期时间 (由系统自动填充和更新)")
    @TableField("token_expiration_time")
    private LocalDateTime tokenExpirationTime;

    /**
     * 项目描述
     */
    @ColumnComment("项目描述")
    @TableField("project_info")
    @ColumnType("TEXT")
    private String projectInfo;

    //---------------对特殊API的配置

    /**
     * 是否启用MM-Api的接口配置
     */
    @ColumnComment("是否启用MM-Api的接口配置")
    @TableField("special_api_status")
    @DefaultValue("0")
    private Boolean specialApiStatus;

    /**
     * 等待多少s后请求获取验证码的接口
     */
    @ColumnComment("等待多少s后请求获取验证码的接口")
    @DefaultValue("30")
    @TableField("special_api_delay")
    private Integer specialApiDelay;

    /**
     * 取码超时时间
     */
    @ColumnComment("获取验证码请求超时时间")
    @DefaultValue("150")
    @TableField("special_api_get_code_out_time")
    private Integer specialApiGetCodeOutTime;

    /**
     * 特殊api请求token
     */
    @ColumnComment("特殊api请求token")
    @TableField("special_api_token")
    private String specialApiToken;

    //****************加解密api字段

    /**
     * 是否启用-AESAPI的接口配置
     */
    @ColumnComment("是否启用-AESAPI的接口配置")
    @TableField("aes_special_api_status")
    @DefaultValue("0")
    private Boolean aesSpecialApiStatus;

    /**
     * 请求域名
     */
    @ColumnComment("请求域名")
    @TableField("aes_special_api_gateway")
    private String aesSpecialApiGateway;

    /**
     * 客户外部数字
     */
    @ColumnComment("客户外部数字")
    @TableField("aes_special_api_out_number")
    private String aesSpecialApiOutNumber;

    /**
     * 请求key
     */
    @ColumnComment("请求key")
    @TableField("aes_special_api_key")
    private String aesSpecialApiKey;

    /**
     * api项目名称
     */
    @ColumnComment("api项目名称")
    @TableField("aes_special_api_project_name")
    private String aesSpecialApiProjectName;

}