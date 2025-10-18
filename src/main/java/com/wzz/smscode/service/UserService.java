package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.common.CommonResultDTO;
import com.wzz.smscode.dto.CreatDTO.UserCreateDTO;
import com.wzz.smscode.dto.EntityDTO.UserDTO;
import com.wzz.smscode.dto.LoginDTO.UserLoginDto;
import com.wzz.smscode.dto.ResultDTO.UserResultDTO;
import com.wzz.smscode.entity.User;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface UserService extends IService<User> {
    User authenticate(Long userId, String password);

    User authenticateUserByUserName(String userId, String password);

    CommonResultDTO<BigDecimal> getBalance(Long userId, String password);

    CommonResultDTO<BigDecimal> getBalance(String userName, String password);

    @Transactional
    boolean createUser(UserCreateDTO dto, Long operatorId);

    @Transactional
    boolean updateUser(UserDTO userDTO, Long operatorId);


    IPage<UserDTO> listSubUsers(Long operatorId, IPage<User> page);

    @Transactional
    CommonResultDTO<?> chargeUser(Long targetUserId, BigDecimal amount, Long operatorId, boolean isRecharge);

    CommonResultDTO<?> rechargeUser(Long targetUserId, BigDecimal amount, Long operatorId);

    CommonResultDTO<?> deductUser(Long targetUserId, BigDecimal amount, Long operatorId);

    boolean isUserAllowed(Long userId);

    void updateUserStatsForNewNumber(Long userId, boolean codeReceived);

    void resetDailyStatsAllUsers();

    User getByUserName(String userName);

    Boolean login(UserLoginDto userLoginDto);

    Boolean regist(UserResultDTO userDTO);

    User findAndLockById(Long userId);

    User AgentLogin(String username, String password);
}
