package com.wzz.smscode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserProjectBanResponseDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String projectId;
    private Integer lineId;
    private String projectName;
    private String lineName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime banTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unbanTime;
    private Integer status;
    private Long remainingSeconds; // 动态剩余秒数，用于前端倒计时渲染

    private Integer triggerAttempts;   // 触发封禁时的取号总量
    private Integer triggerSuccesses;  // 触发封禁时的接码成功量
    private java.math.BigDecimal triggerRate; // 触发封禁时的来码率
}