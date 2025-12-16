package com.wzz.smscode;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzz.smscode.entity.NumberRecord;
import com.wzz.smscode.service.NumberRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class StartupTaskRecovery implements ApplicationRunner {

    @Autowired
    private NumberRecordService numberRecordService;

    @Override
    public void run(ApplicationArguments args) {
        log.info(">>> 系统启动，开始扫描异常中断的取码任务...");

        long lastId = 0L; // 游标，记录上一批次最大的ID
        int batchSize = 100; // 每批次处理500条
        int totalProcessed = 0;

        while (true) {
            LambdaQueryWrapper<NumberRecord> wrapper = new LambdaQueryWrapper<NumberRecord>()
                    .in(NumberRecord::getStatus, 0, 1)
                    .gt(NumberRecord::getId, lastId) // 核心：游标条件
                    .orderByAsc(NumberRecord::getId) // 核心：必须按ID排序
                    .last("LIMIT " + batchSize);     // 核心：限制条数
            List<NumberRecord> records = numberRecordService.list(wrapper);
            if (records.isEmpty()) {
                break;
            }
            log.info(">>> [任务恢复] 本批次扫描到 {} 条中断任务，正在重新调度...", records.size());
            for (NumberRecord record : records) {
                try {
                    numberRecordService.recoverInterruptedTask(record);
                } catch (Exception e) {
                    log.error(">>> 恢复任务 [{}] 失败", record.getId(), e);
                }
                lastId = record.getId();
            }
            totalProcessed += records.size();
            try {
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(60000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (totalProcessed == 0) {
            log.info(">>> 未发现中断任务。");
        } else {
            log.info(">>> 所有中断任务扫描完成，共恢复调度 {} 个任务。", totalProcessed);
        }
    }
}