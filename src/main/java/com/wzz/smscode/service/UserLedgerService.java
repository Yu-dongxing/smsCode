package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.CreatDTO.LedgerCreationDTO;
import com.wzz.smscode.dto.EntityDTO.LedgerDTO;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.enums.FundType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

public interface UserLedgerService extends IService<UserLedger> {

    IPage<LedgerDTO> listUserLedger(Long userId, String password, Date startTime, Date endTime, Page<UserLedger> page);

    IPage<UserLedger> listUserLedgerByUSerId(Long userId, Page<UserLedger> page);

    IPage<LedgerDTO> listAllLedger(Long adminId, String adminPassword,String username, Long filterByUserId, Date startTime, Date endTime, Page<UserLedger> page,String remark);

    BigDecimal calculateUserBalanceFromLedger(Long userId);

    @Transactional
    boolean createLedgerEntry(Long userId, FundType fundType, BigDecimal amount, BigDecimal balanceAfter, String remarks);

    @Transactional(rollbackFor = Exception.class) // 确保任何异常都会回滚事务
    boolean createLedgerAndUpdateBalance(LedgerCreationDTO request);

    IPage<UserLedger> listSubordinateLedgers(Long agentId, Page<UserLedger> page, Long targetUserId, Date startTime, Date endTime);
}
