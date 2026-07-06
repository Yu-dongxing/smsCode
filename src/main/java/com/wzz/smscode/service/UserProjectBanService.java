package com.wzz.smscode.service;

import com.wzz.smscode.entity.Project;

public interface UserProjectBanService {
    /**
     * 判断用户当前在指定项目线路上是否处于被封禁状态 (Redis O(1) 极速拦截)
     */
    boolean isUserBanned(Long userId, String projectId, Integer lineId);

    /**
     * 在 Redis ZSET 中滑动维护该号码生命周期的结算状态，并在达到封禁条件时写入限流 Key
     */
    void recordAttemptAndCheckBan(Long userId, String projectId, Integer lineId, Long recordId, boolean isSuccess, Project project);
}