package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.CommonResultDTO;
import com.wzz.smscode.dto.NumberDTO;
import com.wzz.smscode.entity.NumberRecord;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

public interface NumberRecordService extends IService<NumberRecord> {
    @Transactional
    CommonResultDTO<String> getNumber(Long userId, String password, String projectId, Integer lineId);

    @Async("taskExecutor") // 指定使用的线程池Bean名
    void retrieveCode(Long numberId);

    CommonResultDTO<String> getCode(Long userId, String password, String phoneNumber);

    IPage<NumberDTO> listUserNumbers(Long userId, String password, Integer statusFilter, Date startTime, Date endTime, IPage<NumberRecord> page);

    IPage<NumberRecord> listAllNumbers(Integer statusFilter, Date startTime, Date endTime, IPage<NumberRecord> page);
}
