package com.wzz.smscode.dto.update;

import lombok.Data;

@Data
public class UpdateUserDto {
    private String userName;
    private Long userId;
    private String userPassword;
}
