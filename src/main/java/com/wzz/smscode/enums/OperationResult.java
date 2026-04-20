package com.wzz.smscode.enums;

import lombok.Getter;

@Getter
public enum OperationResult {
    SUCCESS("SUCCESS", "success"),
    FAIL("FAIL", "fail");

    private final String code;
    private final String description;

    OperationResult(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
