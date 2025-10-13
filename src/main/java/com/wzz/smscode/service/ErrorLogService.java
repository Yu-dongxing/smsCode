package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.entity.ErrorLog;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;

public interface ErrorLogService extends IService<ErrorLog> {
    @Async
        // 推荐指定一个线程池，如果没有则使用默认的
    void logError(Exception e, String module, String errorFunction, Long userId);

    IPage<ErrorLog> listErrors(LocalDateTime startTime, LocalDateTime endTime, IPage<ErrorLog> page);

    ErrorLog getErrorDetail(Long errorId);
}
