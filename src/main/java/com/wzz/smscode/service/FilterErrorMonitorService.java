package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wzz.smscode.cacheManager.NumberRecordCacheManager;
import com.wzz.smscode.dto.FilterErrorNoticeDTO;
import com.wzz.smscode.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterErrorMonitorService {

    private static final String ERROR_COUNT_KEY_PREFIX = "sms:filter:error:count:";
    private static final String NOTICE_KEY_PREFIX = "sms:filter:error:notice:";
    private static final long EXPIRE_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SystemConfigService systemConfigService;
    private final ProjectService projectService;
    private final NumberRecordCacheManager cacheManager;

    public void recordFilterError(Project project, String reason) {
        if (project == null || project.getProjectId() == null || project.getLineId() == null) {
            log.warn("筛选错误计数失败，项目或线路信息为空: {}", project);
            return;
        }

        String countKey = buildCountKey(project.getProjectId(), project.getLineId());
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(countKey, EXPIRE_HOURS, TimeUnit.HOURS);
        }

        int limit = systemConfigService.getFilterErrorLimit();
        log.warn("项目 {} 线路 {} 筛选错误计数: {}/{}, reason={}",
                project.getProjectId(), project.getLineId(), count, limit, reason);

        if (count != null && count > limit) {
            closeProjectFilterAndNotify(project, count, limit);
        }
    }

    public void clearFilterError(Project project) {
        if (project == null || project.getProjectId() == null || project.getLineId() == null) {
            log.warn("清理筛选错误计数失败，项目或线路信息为空: {}", project);
            return;
        }

        String countKey = buildCountKey(project.getProjectId(), project.getLineId());
        Boolean deleted = redisTemplate.delete(countKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("项目 {} 线路 {} 筛选成功，已清理筛选错误计数",
                    project.getProjectId(), project.getLineId());
        }
    }

    public void clearFilterErrorAndNotice(Project project) {
        if (project == null || project.getProjectId() == null || project.getLineId() == null) {
            log.warn("清理筛选错误计数和通知失败，项目或线路信息为空: {}", project);
            return;
        }

        String countKey = buildCountKey(project.getProjectId(), project.getLineId());
        String noticeKey = buildNoticeKey(project.getProjectId(), project.getLineId());
        Boolean countDeleted = redisTemplate.delete(countKey);
        Boolean noticeDeleted = redisTemplate.delete(noticeKey);
        log.info("项目 {} 线路 {} 重新开启筛选，清理筛选错误计数: {}, 清理通知: {}",
                project.getProjectId(), project.getLineId(),
                Boolean.TRUE.equals(countDeleted), Boolean.TRUE.equals(noticeDeleted));
    }

    public List<FilterErrorNoticeDTO> listNotices() {
        Set<String> keys = redisTemplate.keys(NOTICE_KEY_PREFIX + "*");
        if (CollectionUtils.isEmpty(keys)) {
            return List.of();
        }

        List<FilterErrorNoticeDTO> notices = new ArrayList<>();
        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof FilterErrorNoticeDTO notice) {
                notices.add(notice);
            }
        }
        notices.sort(Comparator.comparing(FilterErrorNoticeDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return notices;
    }

    private void closeProjectFilterAndNotify(Project project, Long count, int limit) {
        LambdaUpdateWrapper<Project> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Project::getProjectId, project.getProjectId())
                .eq(Project::getLineId, project.getLineId())
                .eq(Project::getEnableFilter, true)
                .set(Project::getEnableFilter, false);

        boolean updated = projectService.update(wrapper);
        if (updated) {
            project.setEnableFilter(false);
            try {
                cacheManager.evictProject(project.getProjectId(), Integer.valueOf(project.getLineId()));
            } catch (NumberFormatException e) {
                log.warn("清理项目缓存失败，线路ID不是数字: projectId={}, lineId={}",
                        project.getProjectId(), project.getLineId());
            }
            log.warn("项目 {} 线路 {} 筛选错误次数超过阈值，已关闭项目筛选开关",
                    project.getProjectId(), project.getLineId());
        }

        String message = String.format("%s项目%s线路 在筛选环节中超过系统最大筛选次数，已自动关闭该项目的筛选配置",
                project.getProjectName(), project.getLineName());
        FilterErrorNoticeDTO notice = new FilterErrorNoticeDTO(
                project.getProjectId(),
                project.getLineId(),
                project.getProjectName(),
                project.getLineName(),
                count,
                limit,
                message,
                LocalDateTime.now()
        );
        redisTemplate.opsForValue().set(buildNoticeKey(project.getProjectId(), project.getLineId()),
                notice, EXPIRE_HOURS, TimeUnit.HOURS);
    }

    private String buildCountKey(String projectId, String lineId) {
        return ERROR_COUNT_KEY_PREFIX + projectId + ":" + lineId;
    }

    private String buildNoticeKey(String projectId, String lineId) {
        return NOTICE_KEY_PREFIX + projectId + ":" + lineId;
    }
}
