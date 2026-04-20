package com.wzz.smscode.enums;

import lombok.Getter;

@Getter
public enum OperatorType {
    ADMIN("ADMIN", "admin"),
    AGENT("AGENT", "agent");

    private final String code;
    private final String description;

    OperatorType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
