package com.wzz.smscode.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.CommonResultDTO;
import com.wzz.smscode.dto.UserCreateDTO;
import com.wzz.smscode.dto.UserDTO;
import com.wzz.smscode.entity.User;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface UserService extends IService<User> {
    User authenticate(Long userId, String password);

    CommonResultDTO<BigDecimal> getBalance(Long userId, String password);

    @Transactional
    boolean createUser(UserCreateDTO dto, Long operatorId);

    @Transactional
    boolean updateUser(UserDTO userDTO, Long operatorId);


    @Transactional
    CommonResultDTO<?> chargeUser(Long targetUserId, BigDecimal amount, Long operatorId, boolean isRecharge);

    CommonResultDTO<?> rechargeUser(Long targetUserId, BigDecimal amount, Long operatorId);

    CommonResultDTO<?> deductUser(Long targetUserId, BigDecimal amount, Long operatorId);

    boolean isUserAllowed(Long userId);

    void updateUserStatsForNewNumber(Long userId, boolean codeReceived);

    void resetDailyStatsAllUsers();
}
