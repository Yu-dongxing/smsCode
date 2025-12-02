package com.wzz.smscode.enums;

import lombok.Getter;

/**
 * 资金交易类型枚举
 */
@Getter
public enum FundType {
    /**
     * 代表业务扣费的资金交易类型。
     * 此枚举值用于标识与业务相关的费用扣除操作。
     */
    BUSINESS_DEDUCTION(0, "业务扣费"),
    /**
     * 代表代理充值的资金交易类型。
     * 此枚举值用于标识与代理相关的资金充值操作。
     */
    AGENT_RECHARGE(1, "代理充值"),
    /**
     * 代表代理扣款的资金交易类型。
     * 此枚举值用于标识与代理相关的资金扣除操作。
     */
    AGENT_DEDUCTION(2, "代理扣款"),

    /**
     * 代理回款
     */
    ADMIN_REBATE(4,"代理回款"),


    /**
     * 超时退款
     */
    ADMIN_OUT_TIME_REBATE(5,"超时退款"),
    /**
     * 代表管理员操作的资金交易类型。
     * 此枚举值用于标识与管理员相关的资金操作，如调整余额、退款等。
     */
    ADMIN_OPERATION(3, "管理员操作");




    private final int code;
    private final String description;

    /**
     * 构造函数，用于初始化资金交易类型的枚举常量。
     *
     * @param code        资金交易类型的唯一标识码
     * @param description 对应的资金交易类型的描述信息
     */
    FundType(int code, String description) {
        this.code = code;
        this.description = description;
    }
}