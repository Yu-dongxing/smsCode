package com.wzz.smscode.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PriceTemplateSyncTaskStatusDTO {
    private String taskId;
    private Long templateId;
    private Integer templateSyncStatus;
    private String templateSyncMessage;
    private String status;
    private String message;
    private String errorMessage;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
