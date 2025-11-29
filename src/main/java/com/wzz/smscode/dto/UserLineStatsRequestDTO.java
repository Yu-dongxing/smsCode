package com.wzz.smscode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime; // 开始时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime; // 结束时间
}