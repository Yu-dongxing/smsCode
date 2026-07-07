package com.wzz.smscode.dto;

import lombok.Data;

@Data
public class UserProjectBanQueryDTO {
    private String userName;
    private String projectName;
    private String lineName;
    private String projectId;
    private Integer lineId;
    private long page = 1;
    private long size = 10;
}