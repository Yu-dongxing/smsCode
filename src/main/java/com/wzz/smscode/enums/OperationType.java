package com.wzz.smscode.enums;

import lombok.Getter;

@Getter
public enum OperationType {
    CREATE_USER("CREATE_USER", "create user"),
    RECHARGE_USER("RECHARGE_USER", "recharge user"),
    DELETE_USER("DELETE_USER", "delete user");

    private final String code;
    private final String description;

    OperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
