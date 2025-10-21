package com.wzz.smscode.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeResult {
    private boolean success;
    private String code;
    private String phoneNumber; // 用于在某些场景下回填手机号

    public static CodeResult success(String code, String phoneNumber) {
        return new CodeResult(true, code, phoneNumber);
    }

    public static CodeResult failure() {
        return new CodeResult(false, null, null);
    }
}