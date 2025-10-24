package com.wzz.smscode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.entity.UserProjectLine;

import java.util.List;

public interface UserProjectLineService extends IService<UserProjectLine> {
    List<UserProjectLine> getLinesByUserId(Long userId);

    List<UserProjectLine> getLinesByUserIds(List<Long> userIds);

    UserProjectLine getByProjectIdLineID(String projectId, Integer lineId,Long userId);
}
