package com.wzz.smscode.dto.update;

import lombok.Data;
@Data
public class UserUpdatePasswardDTO {
    private String userName;
    private String newPassword;
    private String oldPassword;
}