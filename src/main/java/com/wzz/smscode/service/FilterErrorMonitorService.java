package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wzz.smscode.cacheManager.NumberRecordCacheManager;
import com.wzz.smscode.dto.FilterErrorDetailDTO;
import com.wzz.smscode.dto.FilterErrorNoticeDTO;
import com.wzz.smscode.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterErrorMonitorService {

    public static final String ERROR_TYPE_RESPONSE_NOT_NEW = "RESPONSE_NOT_NEW";
    public static final String ERROR_TYPE_REQUEST_ERROR = "REQUEST_ERROR";

    private static final String ERROR_COUNT_KEY_PREFIX = "sms:filter:error:count:";
    private static final String ERROR_DETAIL_KEY_PREFIX = "sms:filter:error:details:";
    private static final String NOTICE_KEY_PREFIX = "sms:filter:error:notice:";
    private static final String NOTICE_INDEX_KEY_PREFIX = "sms:filter:error:notice-index:";
    private static final long EXPIRE_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SystemConfigService systemConfigService;
    private final ProjectService projectService;
    private final NumberRecordCacheManager cacheManager;

    public void recordFilterError(Project project, String reason) {
        recordFilterError(project, null, null, ERROR_TYPE_RESPONSE_NOT_NEW, reason);
    }

    public void recordFilterError(Project project, String phone, String responseBody, String errorType, String errorMessage) {
        if (project == null || project.getProjectId() == null || project.getLineId() == null) {
            log.warn("筛选错误计数失败，项目或线路信息为空: {}", project);
            return;
        }

        String projectId = project.getProjectId();
        String lineId = project.getLineId();
        String countKey = buildCountKey(projectId, lineId);
        String detailKey = buildDetailKey(projectId, lineId);

        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(countKey, EXPIRE_HOURS, TimeUnit.HOURS);
        }

        FilterErrorDetailDTO detail = new FilterErrorDetailDTO(
                phone,
                responseBody,
                errorType,
                errorMessage,
                LocalDateTime.now()
        );
        redisTemplate.opsForList().rightPush(detailKey, detail);
        redisTemplate.expire(detailKey, EXPIRE_HOURS, TimeUnit.HOURS);

        int limit = systemConfigService.getFilterErrorLimit();
        log.warn("项目 {} 线路 {} 筛选错误计数: {}/{}, phone={}, type={}, message={}",
                projectId, lineId, count, limit, phone, errorType, errorMessage);

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
        String detailKey = buildDetailKey(project.getProjectId(), project.getLineId());
        Boolean countDeleted = redisTemplate.delete(countKey);
        Boolean detailDeleted = redisTemplate.delete(detailKey);
        log.info("项目 {} 线路 {} 筛选次数置0，清理计数: {}, 清理错误列表: {}",
                project.getProjectId(), project.getLineId(),
                Boolean.TRUE.equals(countDeleted), Boolean.TRUE.equals(detailDeleted));
    }

    public void clearFilterErrorAndNotice(Project project) {
        if (project == null || project.getProjectId() == null || project.getLineId() == null) {
            log.warn("清理筛选错误计数和通知失败，项目或线路信息为空: {}", project);
            return;
        }

        String projectId = project.getProjectId();
        String lineId = project.getLineId();
        String indexKey = buildNoticeIndexKey(projectId, lineId);
        String noticeId = (String) redisTemplate.opsForValue().get(indexKey);

        Boolean countDeleted = redisTemplate.delete(buildCountKey(projectId, lineId));
        Boolean detailDeleted = redisTemplate.delete(buildDetailKey(projectId, lineId));
        Boolean indexDeleted = redisTemplate.delete(indexKey);
        Boolean noticeDeleted = false;
        if (StringUtils.hasText(noticeId)) {
            noticeDeleted = redisTemplate.delete(buildNoticeKey(noticeId));
        }

        // 兼容旧版本以项目线路为 key 的通知。
        redisTemplate.delete(buildLegacyNoticeKey(projectId, lineId));

        log.info("项目 {} 线路 {} 重新开启筛选，清理计数: {}, 清理错误列表: {}, 清理通知索引: {}, 清理通知: {}",
                projectId, lineId,
                Boolean.TRUE.equals(countDeleted), Boolean.TRUE.equals(detailDeleted),
                Boolean.TRUE.equals(indexDeleted), Boolean.TRUE.equals(noticeDeleted));
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
                if (!StringUtils.hasText(notice.getId())) {
                    notice.setId(key.substring(NOTICE_KEY_PREFIX.length()));
                }
                notices.add(notice);
            }
        }
        notices.sort(Comparator.comparing(FilterErrorNoticeDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return notices;
    }

    public List<FilterErrorDetailDTO> listNoticeDetails(String noticeId) {
        if (!StringUtils.hasText(noticeId)) {
            return List.of();
        }

        Object noticeObj = redisTemplate.opsForValue().get(buildNoticeKey(noticeId));
        if (!(noticeObj instanceof FilterErrorNoticeDTO notice)) {
            return List.of();
        }

        List<Object> values = redisTemplate.opsForList().range(
                buildDetailKey(notice.getProjectId(), notice.getLineId()), 0, -1);
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }

        List<FilterErrorDetailDTO> details = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof FilterErrorDetailDTO detail) {
                details.add(detail);
            }
        }
        return details;
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

        createOrRefreshNotice(project);
    }

    private void createOrRefreshNotice(Project project) {
        String indexKey = buildNoticeIndexKey(project.getProjectId(), project.getLineId());
        String noticeId = (String) redisTemplate.opsForValue().get(indexKey);
        if (!StringUtils.hasText(noticeId)) {
            noticeId = UUID.randomUUID().toString().replace("-", "");
        }

        String message = String.format("%s项目%s线路 在筛选环节中超过系统最大筛选次数，已自动关闭该项目的筛选配置",
                project.getProjectName(), project.getLineName());
        FilterErrorNoticeDTO notice = new FilterErrorNoticeDTO(
                noticeId,
                project.getProjectId(),
                project.getLineId(),
                project.getProjectName(),
                project.getLineName(),
                message,
                LocalDateTime.now()
        );

        redisTemplate.opsForValue().set(buildNoticeKey(noticeId), notice, EXPIRE_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(indexKey, noticeId, EXPIRE_HOURS, TimeUnit.HOURS);
    }

    private String buildCountKey(String projectId, String lineId) {
        return ERROR_COUNT_KEY_PREFIX + projectId + ":" + lineId;
    }

    private String buildDetailKey(String projectId, String lineId) {
        return ERROR_DETAIL_KEY_PREFIX + projectId + ":" + lineId;
    }

    private String buildNoticeKey(String noticeId) {
        return NOTICE_KEY_PREFIX + noticeId;
    }

    private String buildNoticeIndexKey(String projectId, String lineId) {
        return NOTICE_INDEX_KEY_PREFIX + projectId + ":" + lineId;
    }

    private String buildLegacyNoticeKey(String projectId, String lineId) {
        return NOTICE_KEY_PREFIX + projectId + ":" + lineId;
    }
}
