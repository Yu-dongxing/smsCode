package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.DefaultValue;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
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
@TableName("project")
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
     * 服务域名或基础URL
     */
    @ColumnComment("服务域名或基础URL")
    @TableField("domain")
    private String domain;


    // --- 核心API路由和方法配置 ---

    /**
     * 登录接口的路径 (如果需要登录获取Token)
     */
    @ColumnComment("登录接口的路径 (如果需要登录获取Token)")
    @TableField("login_route")
    private String loginRoute;

    /**
     * 登录接口的请求方法 (GET, POST)
     */
    @ColumnComment("登录接口的请求方法 (GET, POST)")
    @TableField("login_method")
    @DefaultValue("'POST'")
    private String loginMethod;

    /**
     * 获取手机号的接口路径
     */
    @ColumnComment("获取手机号的接口路径")
    @TableField("get_number_route")
    private String getNumberRoute;

    /**
     * 获取手机号接口的请求方法 (GET, POST)
     */
    @ColumnComment("获取手机号接口的请求方法 (GET, POST)")
    @TableField("get_number_method")
    @DefaultValue("'GET'")
    private String getNumberMethod;

    /**
     * 获取验证码的接口路径
     */
    @ColumnComment("获取验证码的接口路径")
    @TableField("get_code_route")
    private String getCodeRoute;

    /**
     * 获取验证码接口的存入参数字段名
     */
    @TableField("get_code_field")
    @ColumnComment("获取验证码的请求参数的字段名称")
    private String getCodeField;

    /**
     * 获取验证码接口的请求方法 (GET, POST)
     */
    @ColumnComment("获取验证码接口的请求方法 (GET, POST)")
    @TableField("get_code_method")
    @DefaultValue("'GET'")
    private String getCodeMethod;

    /**
     * 刷新Token的接口路径（预留）
     */
    @ColumnComment("刷新Token的接口路径（预留）")
    @TableField("refresh_token_route")
    private String refreshTokenRoute;

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


    // --- 核心API请求类型配置 ---

    /**
     * 登录接口的请求参数类型 (JSON, FORM, PARAM)
     */
    @ColumnComment("登录接口的请求参数类型 (JSON, FORM, PARAM)")
    @TableField("login_request_type")
    @DefaultValue("'JSON'")
    private RequestType loginRequestType;

    /**
     * 获取手机号接口的请求参数类型 (JSON, FORM, PARAM)
     */
    @ColumnComment("获取手机号接口的请求参数类型 (JSON, FORM, PARAM)")
    @TableField("get_number_request_type")
    @DefaultValue("'PARAM'")
    private RequestType getNumberRequestType;

    /**
     * 获取验证码接口的请求参数类型 (JSON, FORM, PARAM)
     */
    @ColumnComment("获取验证码接口的请求参数类型 (JSON, FORM, PARAM)")
    @TableField("get_code_request_type")
    @DefaultValue("'PARAM'")
    private RequestType getCodeRequestType;

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
     * 用户名字段名，用于构建请求，例如：'username', 'account'
     */
    @ColumnComment("用户名字段名，用于构建请求，例如：username, account")
    @TableField("auth_username_field")
    private String authUsernameField;

    /**
     * 认证用户名 (例如API Key)
     */
    @ColumnComment("认证用户名 (例如API Key)")
    @TableField("auth_username")
    private String authUsername;

    /**
     * 密码字段名，用于构建请求，例如：'password', 'secret'
     */
    @ColumnComment("密码字段名，用于构建请求，例如：password', secret")
    @TableField("auth_password_field")
    private String authPasswordField;

    /**
     * 认证密码 (例如API Secret)
     */
    @ColumnComment("认证密码 (例如API Secret)")
    @TableField("auth_password")
    private String authPassword;

    /**
     * Token字段名，用于构建请求，例如：'token', 'access_token'
     */
    @ColumnComment("Token字段名，用于构建请求，例如：token, access_token")
    @TableField("auth_token_field")
    private String authTokenField;

    /**
     * Token前缀，通常用于Header，例如：'Bearer ' (注意保留末尾空格)
     */
    @ColumnComment("Token前缀，通常用于Header，例如：Bearer  (注意保留末尾空格)")
    @TableField("auth_token_prefix")
    private String authTokenPrefix;

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
     * 筛选号码的api的接口请求字段
     */
    @ColumnComment("筛选号码的api的接口请求字段")
    @TableField("select_number_api_request_value")
    private String selectNumberApiRequestValue;


    // --- API响应解析配置 ---

    /**
     * 登录响应中Token字段名 (支持JSONPath)，例如：'token', 'data.access_token'
     */
    @ColumnComment("登录响应中Token字段名 (支持JSONPath)，例如：token, data.access_token")
    @TableField("response_token_field")
    private String responseTokenField;

    /**
     * 登录响应中Token有效期字段名 (例如 'expires_in')
     */
    @ColumnComment("登录响应中Token有效期字段名 (例如 expires_in)")
    @TableField("response_token_expiration_field")
    private String responseTokenExpirationField;

    /**
     * 登录响应中Token有效期单位 (SECONDS, MINUTES, HOURS, MILLISECONDS)
     */
    @ColumnComment("登录响应中Token有效期单位 (SECONDS, MINUTES, HOURS, MILLISECONDS)")
    @TableField("response_token_expiration_unit")
    @DefaultValue("'SECONDS'")
    private String responseTokenExpirationUnit;

    /**
     * 取号响应中手机号字段名 (支持JSONPath)，例如：'phone', 'data.mobile'
     */
    @ColumnComment("取号响应中手机号字段名 (支持JSONPath)，例如：'phone', data.mobile")
    @TableField("response_phone_field")
    private String responsePhoneField;

    /**
     * 取号响应中用于获取验证码的会话ID字段名 (支持JSONPath)，例如：'id', 'data.sessionId'
     */
    @ColumnComment("取号响应中用于获取验证码的会话ID字段名 (支持JSONPath)，例如：id, data.sessionId")
    @TableField("response_id_field")
    private String responseIdField;

    /**
     * 取号响应中手机号的唯一ID字段名，用于释放、拉黑等操作（区别于会话ID）
     */
    @ColumnComment("取号响应中手机号的唯一ID字段名，用于释放、拉黑等操作（区别于会话ID）")
    @TableField("response_phone_id_field")
    private String responsePhoneIdField;

    /**
     * 取码响应中验证码状态字段名 (支持JSONPath)，例如：'status'
     */
    @ColumnComment("取码响应中验证码状态字段名 (支持JSONPath)，例如：status")
    @TableField("response_status_field")
    private String responseStatusField;

    /**
     * 取码响应中验证码字段名 (支持JSONPath)，例如：'code', 'data.smsCode'
     */
    @ColumnComment("取码响应中验证码字段名 (支持JSONPath)，例如：code, data.smsCode")
    @TableField("response_code_field")
    private String responseCodeField;


    /**
     * 用于获取验证码的标识符Key。
     * 从获取号码接口返回的 Map 中，根据此 Key 来选择哪个值用于后续的取码操作。
     * 通常应配置为 'id' 或 'phone'。
     */
    @ColumnComment("用于获取验证码的标识符Key (例如 id 或 phone)")
    @TableField("code_retrieval_identifier_key")
    @DefaultValue("'phone'") // 默认使用手机号，以兼容老接口
    private String codeRetrievalIdentifierKey;

    /**
     * 筛选号码的api的接口数据返回字段
     */
    @ColumnComment("筛选号码的api的接口请求字段")
    @TableField("response_select_number_api_field")
    private String responseSelectNumberApiField;


    // --- 业务逻辑与定价配置 ---

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

}