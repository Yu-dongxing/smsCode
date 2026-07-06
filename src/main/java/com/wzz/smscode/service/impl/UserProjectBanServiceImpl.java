package com.wzz.smscode.service.impl;

import com.wzz.smscode.cacheManager.NumberRecordCacheManager;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.service.UserProjectBanService;
import com.wzz.smscode.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserProjectBanServiceImpl implements UserProjectBanService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Lazy
    private UserService userService;

    @Autowired
    @Lazy
    private NumberRecordCacheManager cacheManager;

    @Override
    public boolean isUserBanned(Long userId, String projectId, Integer lineId) {
        if (userId == null || projectId == null || lineId == null) {
            return false;
        }
        String banKey = String.format("smscode:ban:%d:%s:%d", userId, projectId, lineId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(banKey));
    }

    @Override
    public void recordAttemptAndCheckBan(Long userId, String projectId, Integer lineId, Long recordId, boolean isSuccess, Project project) {
        if (project == null || !Boolean.TRUE.equals(project.getEnableRateBan())) {
            return;
        }

        String completedKey = String.format("smscode:stats:completed:%d:%s:%d", userId, projectId, lineId);
        String successKey = String.format("smscode:stats:success:%d:%s:%d", userId, projectId, lineId);
        String banKey = String.format("smscode:ban:%d:%s:%d", userId, projectId, lineId);

        // 如果用户目前已经在 Redis 封禁列表里，不需要重复计算
        if (Boolean.TRUE.equals(redisTemplate.hasKey(banKey))) {
            return;
        }

        long now = System.currentTimeMillis();
        long twoHoursAgo = now - (2 * 60 * 60 * 1000L); // 2小时滑动窗口起点

        try {
            // 1. 滑动擦除 2 小时之前的过期统计指标，保证滑动准确性
            redisTemplate.opsForZSet().removeRangeByScore(completedKey, 0, twoHoursAgo);
            redisTemplate.opsForZSet().removeRangeByScore(successKey, 0, twoHoursAgo);

            // 2. 写入当前的结算操作，分数（Score）采用当前时间戳
            redisTemplate.opsForZSet().add(completedKey, String.valueOf(recordId), now);
            if (isSuccess) {
                redisTemplate.opsForZSet().add(successKey, String.valueOf(recordId), now);
            }

            // 3. 设置闲置失效期（2小时外加余量，确保不活动的数据不无限期在 Redis 内存堆积）
            redisTemplate.expire(completedKey, 2 * 3600 + 600, TimeUnit.SECONDS);
            redisTemplate.expire(successKey, 2 * 3600 + 600, TimeUnit.SECONDS);

            // 4. 获取 2 小时滑动窗口统计总量 (ZCARD)
            Long totalCompleted = redisTemplate.opsForZSet().zCard(completedKey);
            Long totalSuccess = redisTemplate.opsForZSet().zCard(successKey);

            if (totalCompleted == null) totalCompleted = 0L;
            if (totalSuccess == null) totalSuccess = 0L;

            // 5. 校验起算阀值是否满足
            int minAttempts = project.getMinAttemptsThreshold() != null ? project.getMinAttemptsThreshold() : 10;
            if (totalCompleted < minAttempts) {
                return;
            }

            // 6. 核心回码率计算
            double currentRate = (double) totalSuccess / totalCompleted;
            double limitRate = project.getMinRateThreshold() != null ? project.getMinRateThreshold().doubleValue() : 0.15;

            if (currentRate < limitRate) {
                int banHours = project.getBanDurationHours() != null ? project.getBanDurationHours() : 12;

                // 7. 直接在 Redis 中设置封禁标志并设定 TTL，到期自动剔除、实现无库定时器负担解禁
                redisTemplate.opsForValue().set(banKey, "1", banHours, TimeUnit.HOURS);

                // 8. 移除本地或 Redis 缓存中存储的用户信息，促使下次校验穿透实时拦截
                User userObj = userService.getById(userId);
                if (userObj != null) {
                    cacheManager.evictUser(userObj.getUserName());
                }

                log.warn("【独立线路 Redis 风控】用户 [{}] 线路 [{}-{}] 2小时滑动回码率 {}/{} = {}%（阀值 {}%），触发自动限制 {} 小时。",
                        userId, projectId, lineId, totalSuccess, totalCompleted, String.format("%.2f", currentRate * 100), String.format("%.2f", limitRate * 100), banHours);
            }

        } catch (Exception e) {
            log.error("Redis 滑动窗口风控机制计算异常", e);
        }
    }
}