package com.wzz.smscode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentDailyStatsRequestDTO {

    private Long page = 1L;

    private Long size = 10L;

    /**
     * 代理id
     */
    private Long agentId;

    /**
     * 代理用户名
     */
    private String agentName;

    /**
     * 项目id
     */
    private String projectId;

    /**
     * 线路id
     */
    private Integer lineId;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
}
