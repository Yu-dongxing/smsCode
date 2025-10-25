package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.common.CommonResultDTO;
import com.wzz.smscode.dto.CodeResult;
import com.wzz.smscode.dto.NumberDTO;
import com.wzz.smscode.entity.NumberRecord;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

public interface NumberRecordService extends IService<NumberRecord> {
//    @Transactional
//    CommonResultDTO<String> getNumber(Long userId, String password, String projectId, Integer lineId);

    CommonResultDTO<String> getNumber(Long userId, String password, String projectId, Integer lineId);

    @Transactional
    CommonResultDTO<String> getNumber(String userName, String password, String projectId, Integer lineId);

    @Async("taskExecutor") // 指定使用的线程池Bean名
    void retrieveCode(Long numberId);


//    CommonResultDTO<String> getCode(Long userId, String password, String phoneNumber);

    @Async("taskExecutor")
    void retrieveCode(Long numberId, String identifier);


    // ... 其他方法保持不变 ...
//    CommonResultDTO<String> getCode(String userName, String password, String identifier);

    // ... 其他方法保持不变 ...
//    CommonResultDTO<String> getCode(String userName, String password, String identifier);

    // ... 其他方法保持不变 ...
//    CommonResultDTO<String> getCode(String userName, String password, String identifier);

    @Transactional
    void updateRecordAfterRetrieval(NumberRecord record, CodeResult result);

    // ... 其他方法保持不变 ...
    CommonResultDTO<String> getCode(String userName, String password, String identifier);

    IPage<NumberDTO> listUserNumbers(Long userName, String password, Integer statusFilter, Date startTime, Date endTime, IPage<NumberRecord> page);

    IPage<NumberDTO> listUserNumbersByUSerName(String userName, String password, Integer statusFilter, Date startTime, Date endTime, IPage<NumberRecord> page);

    IPage<NumberRecord> listAllNumbers(
            Integer statusFilter, Date startTime, Date endTime,
            Long userId, String projectId, String phoneNumber, Integer charged,
            IPage<NumberRecord> page,String lineId
    );

    NumberRecord getRecordByPhone(String phone);
}
