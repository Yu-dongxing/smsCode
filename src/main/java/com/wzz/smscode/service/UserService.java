package com.wzz.smscode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.common.CommonResultDTO;
import com.wzz.smscode.dto.AddUserProjectPricesRequestDTO;
import com.wzz.smscode.dto.agent.AgentDashboardStatsDTO;
import com.wzz.smscode.dto.agent.AgentProjectLineUpdateDTO;
import com.wzz.smscode.dto.agent.AgentProjectPriceDTO;
import com.wzz.smscode.dto.CreatDTO.UserCreateDTO;
import com.wzz.smscode.dto.EntityDTO.UserDTO;
import com.wzz.smscode.dto.LoginDTO.UserLoginDto;
import com.wzz.smscode.dto.ResultDTO.UserResultDTO;
import com.wzz.smscode.dto.project.SubUserProjectPriceDTO;
import com.wzz.smscode.dto.update.UpdateUserDto;
import com.wzz.smscode.dto.update.UserUpdateDtoByUser;
import com.wzz.smscode.dto.update.UserUpdatePasswardDTO;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserProjectLine;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public interface UserService extends IService<User> {
    User authenticate(Long userId, String password);

    User authenticateUserByUserName(String userId, String password);

    CommonResultDTO<BigDecimal> getBalance(Long userId, String password);

    CommonResultDTO<BigDecimal> getBalance(String userName, String password);

    @Transactional
    boolean createUser(UserCreateDTO dto, Long operatorId);

    @Transactional
    boolean updateUser(UserDTO userDTO, Long operatorId);


    IPage<User> listSubUsers(String userName,Long operatorId, IPage<User> page);

    @Transactional
    CommonResultDTO<?> chargeUser(Long targetUserId, BigDecimal amount, Long operatorId, boolean isRecharge);

    CommonResultDTO<?> rechargeUser(Long targetUserId, BigDecimal amount, Long operatorId);

    CommonResultDTO<?> deductUser(Long targetUserId, BigDecimal amount, Long operatorId);

    boolean isUserAllowed(Long userId);

    void updateUserStatsForNewNumber(Long userId, boolean codeReceived);

    void resetDailyStatsAllUsers();

    List<UserProjectLine> getUserProjectLines(Long userId);

    User getByUserName(String userName);

    Boolean login(UserLoginDto userLoginDto);

    Boolean regist(UserResultDTO userDTO);

    AgentDashboardStatsDTO getAgentDashboardStats(Long agentId);

    User findAndLockById(Long userId);

    User AgentLogin(String username, String password);

    boolean updateUserByEn(User userDTO, long l);

    boolean updateUserByAgent(UserUpdateDtoByUser userDTO, long l);

    Boolean updatePassWardByUserId(UpdateUserDto id);

    Boolean updatePassWardByUserName(UserUpdatePasswardDTO updateUserDto);

    @Transactional(rollbackFor = Exception.class)
        // 如果您已在 UserService 接口中声明此方法
    void rechargeUserFromAgentBalance(Long targetUserId, BigDecimal amount, Long agentId);

    @Transactional(rollbackFor = Exception.class)
        // 如果您已在 UserService 接口中声明此方法
    void deductUserToAgentBalance(Long targetUserId, BigDecimal amount, Long agentId);

    List<AgentProjectPriceDTO> getAgentProjectPrices(Long agentId);

    @Transactional
    void updateAgentProjectConfig(Long agentId, AgentProjectLineUpdateDTO updateDTO);

    IPage<SubUserProjectPriceDTO> getSubUsersProjectPrices(String userName,Long agentId, Page<User> page);

    List<Long> findUserIdsByUsernameLike(String username);

    boolean delectByuserId(Long userId);

    /**
     * 为指定用户新增一个或多个项目价格配置
     * <p>
     * 此方法会根据 operatorId 自动处理管理员和代理的不同权限与价格校验逻辑。
     *
     * @param request    包含目标用户ID和待添加价格列表的请求体
     * @param operatorId 操作员ID (0L 代表管理员, 其他为代理ID)
     */
    void addProjectPricesForUser(AddUserProjectPricesRequestDTO request, Long operatorId);
}
