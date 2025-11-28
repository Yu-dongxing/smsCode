package com.wzz.smscode.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@Data
public class UserLineStatsRequestDTO {
    private Long page = 1L;
    private Long size = 10L;

    // 筛选条件
    private String userName; // 用户名模糊查询
    private String projectId; // 项目ID

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime; // 开始时间

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime; // 结束时间
}