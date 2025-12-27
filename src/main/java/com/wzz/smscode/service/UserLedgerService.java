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


    IPage<UserLedger> listUserLedgerByUSerId(Long userId, Page<UserLedger> page);

    IPage<LedgerDTO> listAllLedger(Long adminId, String adminPassword,String username, Long filterByUserId, Date startTime, Date endTime, Page<UserLedger> page,String remark,Integer fundType,
                                   Integer ledgerType);

    BigDecimal calculateUserBalanceFromLedger(Long userId);


    @Transactional(rollbackFor = Exception.class) // 确保任何异常都会回滚事务
    BigDecimal createLedgerAndUpdateBalance(LedgerCreationDTO request);

    IPage<UserLedger> listSubordinateLedgers(String userName,Long agentId, Page<UserLedger> page, Long targetUserId, Date startTime, Date endTime, Integer fundType, Integer ledgerType);

    BigDecimal getTotalProfitByUserId(Long userId);

    BigDecimal getTotalProfit();

    @Transactional(rollbackFor = Exception.class)
    void deleteLedgerByDays(Long operatorId, Long targetUserId, Integer days, boolean isAdmin);

    IPage<LedgerDTO> listAgentOwnLedger(Long userId, String userName, String remark, Date startTime, Date endTime, Integer fundType, Integer ledgerType, Page<UserLedger> page);
}
