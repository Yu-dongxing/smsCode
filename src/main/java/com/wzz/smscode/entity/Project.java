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

    @ColumnComment("Template sync status: 0 syncing, 1 success, 2 failed")
    @TableField("template_sync_status")
    @DefaultValue("1")
    private Integer templateSyncStatus;

    @ColumnComment("Template sync message, empty on success")
    @TableField("template_sync_message")
    @ColumnType("TEXT")
    private String templateSyncMessage;


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

    /**
     * 特殊api请求host
     */
    @ColumnComment("特殊api请求host")
    @TableField("special_api_host")
    private String specialApiHost;

    @ColumnComment("是否启用外部抢单渠道API配置")
    @TableField("outside_order_api_status")
    @DefaultValue("0")
    private Boolean outsideOrderApiStatus;

    @ColumnComment("外部抢单渠道请求host")
    @TableField("outside_order_api_host")
    private String outsideOrderApiHost;

    @ColumnComment("外部抢单渠道userId")
    @TableField("outside_order_api_user_id")
    private String outsideOrderApiUserId;

    @ColumnComment("外部抢单渠道取码轮询间隔毫秒")
    @TableField("outside_order_poll_interval_ms")
    @DefaultValue("3000")
    private Integer outsideOrderPollIntervalMs;

    @ColumnComment("外部抢单渠道反馈重试间隔毫秒")
    @TableField("outside_order_feedback_retry_interval_ms")
    @DefaultValue("2500")
    private Integer outsideOrderFeedbackRetryIntervalMs;

    @ColumnComment("外部抢单渠道反馈最大重试次数")
    @TableField("outside_order_feedback_max_attempts")
    @DefaultValue("5")
    private Integer outsideOrderFeedbackMaxAttempts;

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

    /**
     * 是否启用独立回码率封禁(0-关闭, 1-开启)
     * 这是一个风控功能总开关。
     * 当设置为 0 (关闭) 时，系统不会对该通道下的用户进行回码率监控，用户可以无限制使用，不会触发自动封禁。
     * 当设置为 1 (开启) 时，系统才会激活该线路的 Redis 滑动窗口监控，根据下面三个参数对用户进行检测和控制。
     * 作用：方便您根据不同通道的质量灵活决定是否开启风控。例如，某些不稳定的便宜通道可以保持关闭，而一些优质且成本高的关键通道则可以开启。
     */
    @ColumnComment("是否启用独立回码率封禁(0-关闭, 1-开启)")
    @TableField("enable_rate_ban")
    @DefaultValue("0")
    private Boolean enableRateBan;

    /**
     * 最低回码率阈值
     * 这是一个及格线标尺。它代表在该独立线路上，用户获取到验证码的次数（成功数）占已完结取号次数（总数）的最低可容忍比例。
     * 如果系统在 2 小时窗口内计算出该用户的回码率低于这个设置值，则判定其表现不及格，并立刻执行封禁。
     * 举例：如果设置为 0.2000 (20%)，用户在2小时内完成了20次取号（排除正在进行中的订单），但最后只有 3 次成功接到了码（回码率为 3/20 = 15% < 20%），则触发封禁。
     */
    @ColumnComment("最低回码率阈值")
    @TableField("min_rate_threshold")
    @DefaultValue("0.1500")
    private java.math.BigDecimal minRateThreshold;

    /**
     * 起查阈值
     * 这是一个触发风控计算的最低取号基数。它规定了用户在 2 小时的滑动窗口内，已完结的取号量必须达到或超过这个数值，系统才会去计算他的回码率并考虑封禁。
     * 作用：防止误判，保护小量取号或刚开始取号的用户。
     * 为什么需要这个字段：如果没有这个限制，用户刚开始取号，第一次取号因超时未出码退款了（此时回码率为 0 / 1 = 0%），如果直接去跟 minRateThreshold 比较，用户在只取了一次号且失败的情况下就会被瞬间封禁，这显然是不合理的。只有当用户在 2 小时内已结算的订单达到一定数量（如 10 次以上）时，得出的回码率百分比才具有统计学上的参考价值。
     */
    @ColumnComment("起查阈值")
    @TableField("min_attempts_threshold")
    @DefaultValue("10")
    private Integer minAttemptsThreshold;

    /**
     * 自动封禁时长(小时)
     * 这是一个惩罚时间控制器。当用户被系统判定为“回码率不及格”并触发封禁时，该字段决定了用户被禁止使用当前这条线路的时间长度。
     * 技术实现：当检测到不及格时，系统会在 Redis 中写入封禁键，并将该键的过期时间（TTL）直接设置为这个小时数。
     * 自动释放：在封禁期间内，用户请求当前线路取号会收到“已被临时限制”的提示。时间一到，Redis 键自动失效，用户无需任何人手动操作即可自动解除限制、重新开始取号。
     * 作用：让您能够针对不同项目的重要程度，设置不同的惩罚时长（例如轻微劣质的封禁 2 小时给其恢复机会，恶意刷号或极低质量的直接封禁 24 小时）。
     */
    @ColumnComment("自动封禁时长(小时)")
    @TableField("ban_duration_hours")
    @DefaultValue("2")
    private Integer banDurationHours;

}
