package com.wzz.smscode.enums;

import lombok.Getter;

@Getter
public enum FundType {
    BUSINESS_DEDUCTION(0, "业务扣费"),
    AGENT_RECHARGE(1, "代理充值"),
    AGENT_DEDUCTION(2, "代理扣款"),
    ADMIN_OPERATION(3, "管理员操作");

    private final int code;
    private final String description;

    FundType(int code, String description) {
        this.code = code;
        this.description = description;
    }
}