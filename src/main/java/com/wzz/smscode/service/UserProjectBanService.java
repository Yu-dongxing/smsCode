package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.UserProjectBanQueryDTO;
import com.wzz.smscode.dto.UserProjectBanResponseDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.UserProjectBan;

public interface UserProjectBanService extends IService<UserProjectBan> {
    /**
     * 判断用户当前在指定项目线路上是否处于被封禁状态 (Redis O(1) 极速拦截)
     */
    boolean isUserBanned(Long userId, String projectId, Integer lineId);

    /**
     * 在 Redis ZSET 中滑动维护该号码生命周期的结算状态，并在达到封禁条件时写入限流 Key
     */
    void recordAttemptAndCheckBan(Long userId, String projectId, Integer lineId, Long recordId, boolean isSuccess, Project project);

    /**
     * 多条件组合查询项目封禁列表
     */
    IPage<UserProjectBanResponseDTO> getBanList(UserProjectBanQueryDTO query);

    /**
     * 手动解禁指定限制记录
     */
    void unbanUserProjectLine(Long id);

    /**
     * 自动解禁同步器：定时更新并矫正数据库中的过期状态
     */
    void syncExpiredBansToDatabase();
}