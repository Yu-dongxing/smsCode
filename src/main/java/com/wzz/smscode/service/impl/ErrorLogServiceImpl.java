package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.ErrorLog;
import com.wzz.smscode.mapper.ErrorLogMapper;
import com.wzz.smscode.service.ErrorLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Slf4j
@Service
public class ErrorLogServiceImpl extends ServiceImpl<ErrorLogMapper, ErrorLog> implements ErrorLogService {

    /**
     * 异步记录错误日志。
     * <p>
     * 为了不影响主业务流程的性能和响应时间，此方法设计为异步执行。
     * <strong>请确保在你的 Spring Boot 启动类上添加了 @EnableAsync 注解。</strong>
     *
     * @param e             异常对象
     * @param module        出错模块/包名
     * @param errorFunction 出错方法或功能名称
     * @param userId        受影响的用户ID（可为 null）
     */
    @Async
    @Override
    public void logError(Exception e, String module, String errorFunction, Long userId) {
        try {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setErrorMessage(e.getMessage());
            errorLog.setStackTrace(getStackTraceAsString(e));
            errorLog.setErrorModule(module);
            errorLog.setErrorFunction(errorFunction);
            errorLog.setUserId(userId);
            errorLog.setErrorTime(LocalDateTime.now());

            // 调用MyBatis-Plus的save方法存入数据库
            this.save(errorLog);
        } catch (Exception loggingException) {
            // 如果记录日志本身也失败了（比如数据库连接问题），则在控制台打印严重错误，
            // 这样可以避免因日志记录失败导致主业务的事务回滚或程序崩溃。
            log.error("!!! CRITICAL: Failed to save error log to database !!!");
            log.error("Original Exception: ", e);
            log.error("Logging Database Exception: ", loggingException);
        }
    }

    /**
     * 查询错误日志列表，支持按时间段筛选并分页。
     * <p>
     * 这是一个典型的后台管理功能，供开发或运维人员排查问题。
     *
     * @param startTime 查询起始时间（可为 null）
     * @param endTime   查询结束时间（可为 null）
     * @param page      MyBatis-Plus 分页对象 (包含了页码和每页大小)
     * @return 分页后的错误日志列表
     */
    @Override
    public IPage<ErrorLog> listErrors(LocalDateTime startTime, LocalDateTime endTime, IPage<ErrorLog> page) {
        LambdaQueryWrapper<ErrorLog> wrapper = new LambdaQueryWrapper<>();

        // 构建查询条件
        // 使用方法引用，更安全、更优雅
        wrapper.ge(startTime != null, ErrorLog::getErrorTime, startTime)
                .le(endTime != null, ErrorLog::getErrorTime, endTime);

        // 按错误时间降序排列，让最新的错误显示在最前面，方便排查
        wrapper.orderByDesc(ErrorLog::getErrorTime);

        return this.page(page, wrapper);
    }

    /**
     * 获取某条错误日志的详细信息，通常用于在管理界面点击“详情”时调用。
     *
     * @param errorId 错误日志的主键ID
     * @return ErrorLog 实体，或 null 如果未找到
     */
    @Override
    public ErrorLog getErrorDetail(Long errorId) {
        return this.getById(errorId);
    }

    /**
     * 内部辅助方法：将异常的堆栈轨迹转换为字符串。
     *
     * @param e 异常对象
     * @return 堆栈轨迹字符串
     */
    private String getStackTraceAsString(Throwable e) {
        if (e == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}