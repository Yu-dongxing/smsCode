package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.LedgerDTO;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.enums.FundType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

public interface UserLedgerService extends IService<UserLedger> {

    IPage<LedgerDTO> listUserLedger(Long userId, String password, Date startTime, Date endTime, Page<UserLedger> page);

    IPage<LedgerDTO> listAllLedger(Long adminId, String adminPassword, Long filterByUserId, Date startTime, Date endTime, Page<UserLedger> page);

    BigDecimal calculateUserBalanceFromLedger(Long userId);

    @Transactional
    boolean createLedgerEntry(Long userId, FundType fundType, BigDecimal amount, BigDecimal balanceAfter, String remarks);
}
