package com.wzz.smscode.dto.project;

import com.wzz.smscode.entity.Project;
import lombok.Data;

@Data
public class ProjectAddResponseDTO {
    private Long projectTableId;
    private String projectId;
    private String lineId;
    private String projectName;
    private String syncTaskId;
    private String syncStatus;
    private String syncMessage;

    public static ProjectAddResponseDTO of(Project project, ProjectSyncTaskStatusDTO taskStatus) {
        ProjectAddResponseDTO response = new ProjectAddResponseDTO();
        response.setProjectTableId(project.getId());
        response.setProjectId(project.getProjectId());
        response.setLineId(project.getLineId());
        response.setProjectName(project.getProjectName());
        if (taskStatus != null) {
            response.setSyncTaskId(taskStatus.getTaskId());
            response.setSyncStatus(taskStatus.getStatus());
            response.setSyncMessage(taskStatus.getMessage());
        }
        return response;
    }
}
