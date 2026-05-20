package com.wzz.smscode.dto.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectSyncTaskStatusDTO {
    private String taskId;
    private Long projectTableId;
    private String projectId;
    private String lineId;
    private String status;
    private String message;
    private String errorMessage;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
