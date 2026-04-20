package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.OperationLogQueryDTO;
import com.wzz.smscode.entity.OperationLog;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.enums.OperationType;

import java.math.BigDecimal;

public interface OperationLogService extends IService<OperationLog> {

    void recordSuccess(OperationType operationType,
                       User operator,
                       User targetUser,
                       BigDecimal amount,
                       BigDecimal balanceBefore,
                       BigDecimal balanceAfter,
                       String remark);

    IPage<OperationLog> listLogs(OperationLogQueryDTO queryDTO);
}
