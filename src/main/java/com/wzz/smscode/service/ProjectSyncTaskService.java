package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wzz.smscode.dto.project.ProjectSyncTaskStatusDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.mapper.ProjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class ProjectSyncTaskService {
    private static final int MAX_RETAINED_TASKS = 1000;

    private final AsyncTaskExecutor taskExecutor;
    private final PriceSyncService priceSyncService;
    private final ProjectMapper projectMapper;
    private final ConcurrentMap<String, ProjectSyncTaskStatusDTO> tasks = new ConcurrentHashMap<>();

    public ProjectSyncTaskService(@Qualifier("taskExecutor") AsyncTaskExecutor taskExecutor,
                                  PriceSyncService priceSyncService,
                                  ProjectMapper projectMapper) {
        this.taskExecutor = taskExecutor;
        this.priceSyncService = priceSyncService;
        this.projectMapper = projectMapper;
    }

    public ProjectSyncTaskStatusDTO submitProjectSync(Project project) {
        ProjectSyncTaskStatusDTO status = new ProjectSyncTaskStatusDTO();
        status.setTaskId(UUID.randomUUID().toString());
        status.setProjectTableId(project.getId());
        status.setProjectId(project.getProjectId());
        status.setLineId(project.getLineId());
        status.setStatus("QUEUED");
        status.setMessage("Project created, price template sync queued");
        status.setQueuedAt(LocalDateTime.now());
        tasks.put(status.getTaskId(), status);
        trimOldTasksIfNeeded();

        Runnable submitTask = () -> {
            try {
                taskExecutor.execute(() -> runProjectSync(status.getTaskId(), project));
            } catch (Exception e) {
                markFailedToSubmit(status, project, e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTask.run();
                }
            });
        } else {
            submitTask.run();
        }
        return status;
    }

    public ProjectSyncTaskStatusDTO getStatus(String taskId) {
        return tasks.get(taskId);
    }

    private void runProjectSync(String taskId, Project project) {
        ProjectSyncTaskStatusDTO status = tasks.get(taskId);
        if (status == null) {
            return;
        }
        status.setStatus("RUNNING");
        status.setMessage("Price template sync running");
        status.setStartedAt(LocalDateTime.now());
        try {
            priceSyncService.syncByProjectChanged(project);
            status.setStatus("SUCCESS");
            status.setMessage("Price template sync completed");
            status.setErrorMessage(null);
            updateProjectTemplateSyncResult(project.getId(), 1, null);
            log.info("Project price sync task completed, taskId={}, projectId={}, lineId={}",
                    taskId, project.getProjectId(), project.getLineId());
        } catch (Exception e) {
            status.setStatus("FAILED");
            status.setMessage("Price template sync failed");
            status.setErrorMessage(e.getMessage());
            updateProjectTemplateSyncResult(project.getId(), 2, e.getMessage());
            log.error("Project price sync task failed, taskId={}, projectId={}, lineId={}",
                    taskId, project.getProjectId(), project.getLineId(), e);
        } finally {
            status.setFinishedAt(LocalDateTime.now());
        }
    }

    private void markFailedToSubmit(ProjectSyncTaskStatusDTO status, Project project, Exception e) {
        status.setStatus("FAILED");
        status.setMessage("Price template sync failed to submit");
        status.setErrorMessage(e.getMessage());
        status.setFinishedAt(LocalDateTime.now());
        updateProjectTemplateSyncResult(project.getId(), 2, e.getMessage());
        log.error("Project price sync task submit failed, taskId={}, projectId={}, lineId={}",
                status.getTaskId(), project.getProjectId(), project.getLineId(), e);
    }

    private void updateProjectTemplateSyncResult(Long projectId, Integer syncStatus, String message) {
        if (projectId == null) {
            return;
        }
        LambdaUpdateWrapper<Project> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Project::getId, projectId)
                .set(Project::getTemplateSyncStatus, syncStatus)
                .set(Project::getTemplateSyncMessage, syncStatus != null && syncStatus == 1 ? null : message);
        projectMapper.update(null, wrapper);
    }

    private void trimOldTasksIfNeeded() {
        if (tasks.size() <= MAX_RETAINED_TASKS) {
            return;
        }
        Optional<ProjectSyncTaskStatusDTO> oldest = tasks.values().stream()
                .min(Comparator.comparing(ProjectSyncTaskStatusDTO::getQueuedAt));
        oldest.map(ProjectSyncTaskStatusDTO::getTaskId).ifPresent(tasks::remove);
    }
}
