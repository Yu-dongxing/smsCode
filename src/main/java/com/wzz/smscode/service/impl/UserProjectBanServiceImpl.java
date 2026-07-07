package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.cacheManager.NumberRecordCacheManager;
import com.wzz.smscode.dto.UserProjectBanQueryDTO;
import com.wzz.smscode.dto.UserProjectBanResponseDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserProjectBan;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.UserProjectBanMapper;
import com.wzz.smscode.service.UserProjectBanService;
import com.wzz.smscode.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserProjectBanServiceImpl extends ServiceImpl<UserProjectBanMapper, UserProjectBan> implements UserProjectBanService {

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

    @Transactional(rollbackFor = Exception.class)
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
                // 1. 获取封禁小时数（支持小数），若未配置则默认 12 小时
                double banHours = project.getBanDurationHours() != null
                        ? project.getBanDurationHours().doubleValue()
                        : 12.0;

                // 2. 将小时转换为秒数 (例如 0.1 小时 * 3600 = 360 秒)
                long banSeconds = (long) (banHours * 3600);
                LocalDateTime banTime = LocalDateTime.now();
                LocalDateTime unbanTime = banTime.plusSeconds(banSeconds);

                // 1. 写入 Redis 用于极速业务拦截 [3]
                redisTemplate.opsForValue().set(banKey, "1", banSeconds, TimeUnit.SECONDS);

                // 2. 写入 MySQL 用于后台数据看板及解封管理
                UserProjectBan banRecord = new UserProjectBan();
                banRecord.setUserId(userId);
                banRecord.setProjectId(projectId);
                banRecord.setLineId(lineId);
                banRecord.setBanTime(banTime);
                banRecord.setUnbanTime(unbanTime);
                banRecord.setStatus(0); // 0-封禁中
                banRecord.setTriggerAttempts(totalCompleted.intValue());
                banRecord.setTriggerSuccesses(totalSuccess.intValue());
                banRecord.setTriggerRate(java.math.BigDecimal.valueOf(currentRate));
                this.save(banRecord);

                User userObj = userService.getById(userId);
                if (userObj != null) {
                    cacheManager.evictUser(userObj.getUserName());
                }

                log.warn("【风控系统】用户 [{}] 线路 [{}-{}] 2小时滑动回码率 {}/{} = {}%（阀值 {}%），自动触发封禁并持久化入库。",
                        userId, projectId, lineId, totalSuccess, totalCompleted, String.format("%.2f", currentRate * 100), String.format("%.2f", limitRate * 100));
            }

        } catch (Exception e) {
            log.error("计算滑动风控异常", e);
        }
    }

    @Override
    public IPage<UserProjectBanResponseDTO> getBanList(UserProjectBanQueryDTO query) {
        Page<UserProjectBanResponseDTO> pageRequest = new Page<>(query.getPage(), query.getSize());
        IPage<UserProjectBanResponseDTO> resultPage = this.baseMapper.selectBanPage(pageRequest, query);

        LocalDateTime now = LocalDateTime.now();
        // 计算每一条在封明细的剩余秒数，支撑前端实时倒计时组件
        for (UserProjectBanResponseDTO dto : resultPage.getRecords()) {
            if (dto.getUnbanTime() != null && dto.getUnbanTime().isAfter(now)) {
                dto.setRemainingSeconds(Duration.between(now, dto.getUnbanTime()).getSeconds());
            } else {
                dto.setRemainingSeconds(0L);
            }
        }
        return resultPage;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void unbanUserProjectLine(Long id) {
        UserProjectBan banRecord = this.getById(id);
        if (banRecord == null || banRecord.getStatus() != 0) {
            throw new BusinessException("该封禁记录已被手动解除或已过期自动解禁");
        }

        // 1. 同步物理纠正数据库状态
        banRecord.setStatus(1);
        this.updateById(banRecord);

        // 2. 清理 Redis 中的阻断 Key
        String banKey = String.format("smscode:ban:%d:%s:%d", banRecord.getUserId(), banRecord.getProjectId(), banRecord.getLineId());
        redisTemplate.delete(banKey);

        // 3. 擦除该用户此线路上 Redis 的 ZSET 统计滑块（给予用户一次全新的取号取码统计周期）
        String completedKey = String.format("smscode:stats:completed:%d:%s:%d", banRecord.getUserId(), banRecord.getProjectId(), banRecord.getLineId());
        String successKey = String.format("smscode:stats:success:%d:%s:%d", banRecord.getUserId(), banRecord.getProjectId(), banRecord.getLineId());
        redisTemplate.delete(completedKey);
        redisTemplate.delete(successKey);

        // 4. 失效主用户缓存
        User user = userService.getById(banRecord.getUserId());
        if (user != null) {
            cacheManager.evictUser(user.getUserName());
        }
    }

    /**
     * 【重要修复】：每1分钟扫描一次 MySQL，将物理时间上已经过期的零余额封禁明细更新为“状态已解禁（1）”，实现无缝双向状态同步。
     */
    @Scheduled(cron = "0 */1 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void syncExpiredBansToDatabase() {
        LocalDateTime now = LocalDateTime.now();

        boolean updated = this.update(new LambdaUpdateWrapper<UserProjectBan>()
                .set(UserProjectBan::getStatus, 1)      // 状态变更为：已解禁
                .eq(UserProjectBan::getStatus, 0)       // 锁死当前仍处于状态：0(封禁中)
                .le(UserProjectBan::getUnbanTime, now)  // 自动解禁临界点时间达到
        );

        if (updated) {
            log.info("【自动解禁同步调度】检测到过期封禁，已同步将 MySQL 库中对应记录状态置为 [已解禁]。");
        }
    }
}