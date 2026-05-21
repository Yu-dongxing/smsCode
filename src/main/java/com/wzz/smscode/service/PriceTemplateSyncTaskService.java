package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wzz.smscode.dto.PriceTemplateSyncTaskStatusDTO;
import com.wzz.smscode.entity.PriceTemplate;
import com.wzz.smscode.mapper.PriceTemplateMapper;
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
public class PriceTemplateSyncTaskService {
    private static final int MAX_RETAINED_TASKS = 1000;

    private final AsyncTaskExecutor taskExecutor;
    private final PriceSyncService priceSyncService;
    private final PriceTemplateMapper priceTemplateMapper;
    private final ConcurrentMap<String, PriceTemplateSyncTaskStatusDTO> tasks = new ConcurrentHashMap<>();

    public PriceTemplateSyncTaskService(@Qualifier("taskExecutor") AsyncTaskExecutor taskExecutor,
                                        PriceSyncService priceSyncService,
                                        PriceTemplateMapper priceTemplateMapper) {
        this.taskExecutor = taskExecutor;
        this.priceSyncService = priceSyncService;
        this.priceTemplateMapper = priceTemplateMapper;
    }

    public PriceTemplateSyncTaskStatusDTO submitTemplateSync(Long templateId) {
        PriceTemplateSyncTaskStatusDTO status = new PriceTemplateSyncTaskStatusDTO();
        status.setTaskId(UUID.randomUUID().toString());
        status.setTemplateId(templateId);
        status.setStatus("QUEUED");
        status.setMessage("Price template sync queued");
        status.setTemplateSyncStatus(0);
        status.setTemplateSyncMessage(null);
        status.setQueuedAt(LocalDateTime.now());
        tasks.put(status.getTaskId(), status);
        trimOldTasksIfNeeded();

        updateTemplateSyncResult(templateId, 0, null);

        Runnable submitTask = () -> {
            try {
                taskExecutor.execute(() -> runTemplateSync(status.getTaskId(), templateId));
            } catch (Exception e) {
                markFailedToSubmit(status, e);
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

    private void runTemplateSync(String taskId, Long templateId) {
        PriceTemplateSyncTaskStatusDTO status = tasks.get(taskId);
        if (status == null) {
            return;
        }
        status.setStatus("RUNNING");
        status.setMessage("Price template sync running");
        status.setStartedAt(LocalDateTime.now());
        try {
            priceSyncService.syncByTemplateChanged(templateId);
            status.setStatus("SUCCESS");
            status.setMessage("Price template sync completed");
            status.setErrorMessage(null);
            status.setTemplateSyncStatus(1);
            status.setTemplateSyncMessage(null);
            updateTemplateSyncResult(templateId, 1, null);
            log.info("Price template sync task completed, taskId={}, templateId={}", taskId, templateId);
        } catch (Exception e) {
            status.setStatus("FAILED");
            status.setMessage("Price template sync failed");
            status.setErrorMessage(e.getMessage());
            status.setTemplateSyncStatus(2);
            status.setTemplateSyncMessage(e.getMessage());
            updateTemplateSyncResult(templateId, 2, e.getMessage());
            log.error("Price template sync task failed, taskId={}, templateId={}", taskId, templateId, e);
        } finally {
            status.setFinishedAt(LocalDateTime.now());
        }
    }

    private void markFailedToSubmit(PriceTemplateSyncTaskStatusDTO status, Exception e) {
        status.setStatus("FAILED");
        status.setMessage("Price template sync failed to submit");
        status.setErrorMessage(e.getMessage());
        status.setTemplateSyncStatus(2);
        status.setTemplateSyncMessage(e.getMessage());
        status.setFinishedAt(LocalDateTime.now());
        updateTemplateSyncResult(status.getTemplateId(), 2, e.getMessage());
        log.error("Price template sync task submit failed, taskId={}, templateId={}",
                status.getTaskId(), status.getTemplateId(), e);
    }

    private void updateTemplateSyncResult(Long templateId, Integer syncStatus, String message) {
        if (templateId == null) {
            return;
        }
        LambdaUpdateWrapper<PriceTemplate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PriceTemplate::getId, templateId)
                .set(PriceTemplate::getTemplateSyncStatus, syncStatus)
                .set(PriceTemplate::getTemplateSyncMessage, syncStatus != null && syncStatus == 1 ? null : message);
        priceTemplateMapper.update(null, wrapper);
    }

    private void trimOldTasksIfNeeded() {
        if (tasks.size() <= MAX_RETAINED_TASKS) {
            return;
        }
        Optional<PriceTemplateSyncTaskStatusDTO> oldest = tasks.values().stream()
                .min(Comparator.comparing(PriceTemplateSyncTaskStatusDTO::getQueuedAt));
        oldest.map(PriceTemplateSyncTaskStatusDTO::getTaskId).ifPresent(tasks::remove);
    }
}
