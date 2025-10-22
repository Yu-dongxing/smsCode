package com.wzz.smscode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.entity.UserProjectLine;

import java.util.List;

public interface UserProjectLineService extends IService<UserProjectLine> {
    List<UserProjectLine> getLinesByUserId(Long userId);
}
