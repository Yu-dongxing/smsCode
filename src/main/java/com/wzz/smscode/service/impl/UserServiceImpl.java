package com.wzz.smscode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzz.smscode.common.CommonResultDTO;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.dto.*;
import com.wzz.smscode.dto.CreatDTO.LedgerCreationDTO;
import com.wzz.smscode.dto.CreatDTO.UserCreateDTO;
import com.wzz.smscode.dto.EntityDTO.UserDTO;
import com.wzz.smscode.dto.LoginDTO.UserLoginDto;
import com.wzz.smscode.dto.ResultDTO.UserResultDTO;
import com.wzz.smscode.dto.update.UpdateUserDto;
import com.wzz.smscode.dto.update.UserUpdatePasswardDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.enums.FundType;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.UserMapper;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.service.UserLedgerService;
import com.wzz.smscode.service.UserProjectLineService;
import com.wzz.smscode.service.UserService;
import com.wzz.smscode.util.BalanceUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    //    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProjectService projectService;
    @Autowired private UserLedgerService ledgerService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public User authenticate(Long userId, String password) {
        User user = this.getById(userId);
//        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
//            return user;
//        }
        return null;
    }


    @Autowired
    private UserLedgerService userLedgerService;

    /**
     * 获取代理仪表盘的统计数据
     * @param agentId 代理的用户ID
     * @return 包含核心统计数据的DTO
     */
    @Override
    public AgentDashboardStatsDTO getAgentDashboardStats(Long agentId) {
        AgentDashboardStatsDTO statsDTO = new AgentDashboardStatsDTO();

        // 1. 获取代理自身信息，查询"我的余额"
        User agent = this.getById(agentId);
        if (agent == null) {
            throw new BusinessException("代理用户不存在");
        }
        statsDTO.setMyBalance(agent.getBalance());

        // 2. 查询该代理的所有下级用户
        LambdaQueryWrapper<User> subUsersQuery = new LambdaQueryWrapper<User>().eq(User::getParentId, agentId);
        List<User> subUsers = this.list(subUsersQuery);

        if (subUsers.isEmpty()) {
            // 如果没有下级，直接返回默认值
            statsDTO.setTotalSubUsers(0L);
            statsDTO.setTodaySubUsersRecharge(BigDecimal.ZERO);
            statsDTO.setSubUsersCodeRate(0.0);
            return statsDTO;
        }

        // 3. 计算"我的下级总人数"
        statsDTO.setTotalSubUsers((long) subUsers.size());
        List<Long> subUserIds = subUsers.stream().map(User::getId).collect(Collectors.toList());

        // 4. 计算"我的下级所有人今日充值"
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        LambdaQueryWrapper<UserLedger> ledgerWrapper = new LambdaQueryWrapper<>();
        ledgerWrapper.in(UserLedger::getUserId, subUserIds)
                // FundType.AGENT_RECHARGE 代表上级给下级充值，是下级的入账记录
                .eq(UserLedger::getFundType, FundType.AGENT_RECHARGE.getCode())
                // ledgerType 1 代表入账
                .eq(UserLedger::getLedgerType, 1)
                .ge(UserLedger::getTimestamp, todayStart)
                .le(UserLedger::getTimestamp, todayEnd);

        List<UserLedger> todayRechargeLedgers = userLedgerService.list(ledgerWrapper);
        BigDecimal totalRecharge = todayRechargeLedgers.stream()
                .map(UserLedger::getPrice) // price 字段记录了变动金额
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        statsDTO.setTodaySubUsersRecharge(totalRecharge);

        // 5. 计算"我的下级的回码率"
        // 累加所有下级的总取号数和总成功取码数来计算总回码率
        long totalGetCount = subUsers.stream().mapToLong(User::getTotalGetCount).sum();
        long totalCodeCount = subUsers.stream().mapToLong(User::getTotalCodeCount).sum();

        if (totalGetCount == 0) {
            statsDTO.setSubUsersCodeRate(0.0);
        } else {
            double rate = ((double) totalCodeCount / totalGetCount) * 100.0;
            // 格式化为两位小数
            statsDTO.setSubUsersCodeRate(BigDecimal.valueOf(rate)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue());
        }

        return statsDTO;
    }

    @Override
    public User findAndLockById(Long userId) {
        return baseMapper.selectByIdForUpdate(userId);
    }

    @Override
    public User AgentLogin(String username, String password) {
        User user = this.getByUserName(username);
        if (user == null) {
            return null;
        }
        // TODO: 必须使用加密方式校验密码！此处为示例，请替换为您的加密校验逻辑
        // if (passwordEncoder.matches(password, user.getPassword())) {
        if (password.equals(user.getPassword())) { // 极不安全，仅作演示
            return user;
        }
        return null;
    }

    @Override
    public boolean updateUserByEn(User userDTO, long l) {
        return updateById(userDTO);
    }

    @Override
    public Boolean updatePassWardByUserId(UpdateUserDto id) {
        User user = userMapper.selectById(id.getUserId());
        if (user==null){
            throw new BusinessException(0,"用户查询失败");
        }
        user.setPassword(id.getUserPassword());
        return updateById(user);
    }

    @Override
    public Boolean updatePassWardByUserName(UserUpdatePasswardDTO updateUserDto) {
        User user = getByUserName(updateUserDto.getUserName());
        if (user == null){
            throw new BusinessException(0,"没有该用户！");
        }
        if (!user.getPassword().equals(updateUserDto.getOldPassword())){
            throw new BusinessException(0,"用户名旧密码不正确");
        }
        user.setPassword(updateUserDto.getNewPassword());
        return updateById(user);
    }


    /**
     * 计算用户
     * @param userId
     * @param password
     * @return
     */

    @Override
    public User authenticateUserByUserName(String userId, String password) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName,userId);

        User user = this.getOne(queryWrapper);
//        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
//            return user;
//        }
        return user;
    }

    @Override
    public CommonResultDTO<BigDecimal> getBalance(Long userId, String password) {
        return null;
    }

    @Override
    public CommonResultDTO<BigDecimal> getBalance(String userName, String password) {
        User user = authenticateUserByUserName(userName, password);
        if (user == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户ID或密码错误");
        }
        return CommonResultDTO.success("查询成功", user.getBalance());
    }


    /* 新建一个简单 VO，用来装需要的一次性数据 */
    @Data
    @AllArgsConstructor
    private static class ProjMeta {
        String projectName;
        BigDecimal costPrice;
    }

    @Autowired
    private UserProjectLineService userProjectLineService;

    /**
     * [重构] 创建用户并处理初始余额
     * 1. 创建用户实体，初始余额设置为0。
     * 2. 保存用户实体以获取ID。
     * 3. 如果提供了初始余额，则调用统一的资金服务接口 `createLedgerAndUpdateBalance` 为新用户充值。
     * 4. 如果操作者是代理，则再次调用该接口从代理账户扣款。
     * 整个过程由 @Transactional 注解保证事务一致性。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createUser(UserCreateDTO dto, Long operatorId) {
        // 1. 校验操作员身份
        boolean isAdmin = (operatorId == 0L);
        User operator;
        if (isAdmin) {
            operator = new User();
            operator.setId(0L);
        } else {
            operator = this.getById(operatorId);
            if (operator == null) throw new BusinessException("操作员不存在");
            if (operator.getIsAgent() != 1) throw new BusinessException("无权创建用户");
        }

        // 2. 构造用户实体，余额初始化为0，后续通过账本服务更新
        User user = new User();
        user.setUserName(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setParentId(operatorId);
        user.setIsAgent(dto.getIsAgent() ? 1 : 0);
        user.setStatus(0); // 默认正常状态
        user.setBalance(BigDecimal.ZERO); // 初始化余额为0

//        // 3. 处理价格配置
//        Map<String, BigDecimal> finalPrices = dto.getProjectPrices();
//        if (finalPrices == null || finalPrices.isEmpty()) {
//            finalPrices = generateInitialPricesFor(operator);
//        } else {
//            validateProjectPrices(finalPrices, operator);
//        }
//        try {
//            user.setProjectPrices(objectMapper.writeValueAsString(finalPrices));
//        } catch (Exception e) {
//            throw new RuntimeException("序列化价格JSON失败", e);
//        }

        // 4. 保存用户
        boolean saved = this.save(user);
        if (!saved) {
            throw new RuntimeException("创建用户基础信息失败");
        }

        // 4. [核心改造] 处理价格配置并存入新表
        List<ProjectPriceDTO> priceSettings = dto.getProjectPrices();
        if (CollectionUtils.isEmpty(priceSettings)) {
            priceSettings = generateInitialPricesFor(operator);
        } else {
            validateProjectPrices(priceSettings, operator);
        }
        log.info("处理价格配置并存入新表{}",priceSettings);

        if (!CollectionUtils.isEmpty(priceSettings)) {
            List<UserProjectLine> linesToInsert = new ArrayList<>();
            Map<String, ProjMeta> projMetaMap = projectService.list().stream()
                    .collect(Collectors.toMap(
                            p -> p.getProjectId() + "-" + p.getLineId(),
                            p -> new ProjMeta(p.getProjectName(), p.getCostPrice()),
                            (a, b) -> a));   // 重复 key 取第一个

            for (ProjectPriceDTO priceDto : priceSettings) {
                UserProjectLine upl = new UserProjectLine();
                upl.setUserId(user.getId());
                upl.setProjectId(String.valueOf(priceDto.getProjectId()));
                upl.setLineId(String.valueOf(priceDto.getLineId()));

                ProjMeta meta = projMetaMap.get(priceDto.getProjectId() + "-" + priceDto.getLineId());
                upl.setProjectName(meta.getProjectName());
                upl.setCostPrice(meta.getCostPrice());   // <-- 新增
                upl.setAgentPrice(priceDto.getPrice());

                linesToInsert.add(upl);
            }
            userProjectLineService.saveBatch(linesToInsert);
        }


        // 5. [核心改造] 通过统一的账本服务处理初始余额
        BigDecimal initialBalance = dto.getInitialBalance();
        if (initialBalance != null && initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            try {
                // 5.1 为新用户充值
                LedgerCreationDTO userRechargeDto = LedgerCreationDTO.builder()
                        .userId(user.getId())
                        .amount(initialBalance) // 金额统一为正数
                        .ledgerType(1)          // 1-入账
                        .fundType(FundType.AGENT_RECHARGE)
                        .remark("新用户初始充值")
                        .build();
                ledgerService.createLedgerAndUpdateBalance(userRechargeDto);

                // 5.2 如果操作员不是管理员，则从操作员账户扣款
                if (!isAdmin) {
                    LedgerCreationDTO operatorDeductDto = LedgerCreationDTO.builder()
                            .userId(operatorId)
                            .amount(initialBalance) // 金额统一为正数
                            .ledgerType(0)          // 0-出账
                            .fundType(FundType.AGENT_DEDUCTION)
                            .remark("为下级用户 " + user.getUserName() + " 充值")
                            .build();
                    ledgerService.createLedgerAndUpdateBalance(operatorDeductDto);
                }
            } catch (BusinessException e) {
                // 回滚事务并向上抛出业务异常，例如“余额不足”
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                log.error("为新用户 {} 分配初始资金失败: {}", user.getUserName(), e.getMessage());
                throw new BusinessException("为新用户分配初始资金失败: " + e.getMessage());
            } catch (Exception e) {
                // 回滚事务并记录未知异常
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                log.error("为新用户 {} 分配初始资金时发生系统错误", user.getUserName(), e);
                throw new RuntimeException("系统错误，创建用户资金记录失败", e);
            }
        }
        return true;
    }


    @Transactional
    @Override
    public boolean updateUser(UserDTO userDTO, Long operatorId) {
        User targetUser = this.getById(userDTO.getUserId());
        if (targetUser == null) throw new IllegalArgumentException("目标用户不存在");

        boolean isAdmin = (operatorId == 0L);

        if (!isAdmin) {
            User operator = this.getById(operatorId);
            if (operator == null) throw new IllegalArgumentException("操作员不存在");
            if (!Objects.equals(targetUser.getParentId(), operatorId)) {
                throw new SecurityException("无权修改该用户信息");
            }
        }

        boolean changed = false;

        if (userDTO.getStatus() != null && isAdmin) {
            targetUser.setStatus(userDTO.getStatus());
            changed = true;
        }

        if(userDTO.getIsAgent() != null && isAdmin) {
            targetUser.setIsAgent(userDTO.getIsAgent() ? 1 : 0);
            changed = true;
        }

        if(changed) {
            this.updateById(targetUser);
        }

        // [核心改造] 修改价格配置
        if (userDTO.getProjectPrices() != null) {
            User operatorForValidation = isAdmin ? new User() {{ setId(0L); }} : this.getById(operatorId);

            // 构造 ProjectPriceDTO 列表用于校验
            List<ProjectPriceDTO> pricesToValidate = userDTO.getProjectPrices().entrySet().stream().map(entry -> {
                String[] ids = entry.getKey().split("-");
                ProjectPriceDTO dto = new ProjectPriceDTO();
                dto.setProjectId(Long.parseLong(ids[0]));
                dto.setLineId(Long.parseLong(ids[1]));
                dto.setPrice(entry.getValue());
                return dto;
            }).collect(Collectors.toList());

            validateProjectPrices(pricesToValidate, operatorForValidation);

            // 更新价格：先删除旧的，再插入新的
//            userProjectLineService.remove(new LambdaQueryWrapper<UserProjectLine>().eq(UserProjectLine::getUserId, userDTO.getUserId()));

            List<UserProjectLine> linesToInsert = pricesToValidate.stream().map(priceDto -> {
                UserProjectLine upl = new UserProjectLine();
                upl.setUserId(userDTO.getUserId());
                upl.setProjectId(String.valueOf(priceDto.getProjectId()));
                upl.setLineId(String.valueOf(priceDto.getLineId()));
                upl.setAgentPrice(priceDto.getPrice());
                // projectName 可以在此从 projectService 获取并设置
                return upl;
            }).collect(Collectors.toList());

            if (!linesToInsert.isEmpty()) {
                userProjectLineService.saveBatch(linesToInsert);
            }
            return true; // 价格操作已执行
        }

        return changed;
    }


    @Override
    public IPage<User> listSubUsers(Long operatorId, IPage<User> page) {

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getParentId, operatorId);

        IPage<User> userPage = this.page(page, wrapper);
        return userPage;
    }


    /**
     * [无需修改] 此方法已正确使用统一资金服务接口
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CommonResultDTO<?> chargeUser(Long targetUserId, BigDecimal amount, Long operatorId, boolean isRecharge) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CommonResultDTO.error(-5, "金额必须为正数");
        }

        User targetUser = this.getById(targetUserId);
        if (targetUser == null) return CommonResultDTO.error(-5, "目标用户不存在");

        boolean isAdmin = (operatorId == 0L);
        if (!isAdmin) {
            User operator = this.getById(operatorId);
            if (operator == null) return CommonResultDTO.error(-5, "操作员不存在");
            if (!Objects.equals(targetUser.getParentId(), operatorId)) {
                return CommonResultDTO.error(-5, "无权操作该用户");
            }
        }

        try {
            // 1. 操作目标用户
            LedgerCreationDTO targetLedger = LedgerCreationDTO.builder()
                    .userId(targetUserId)
                    .amount(amount) // 金额始终为正
                    .ledgerType(isRecharge ? 1 : 0) // 1-入账, 0-出账
                    .fundType(isRecharge ? FundType.AGENT_RECHARGE : FundType.AGENT_DEDUCTION)
                    .remark((isRecharge ? "上级充值: " : "上级扣款: ") + operatorId)
                    .build();
            ledgerService.createLedgerAndUpdateBalance(targetLedger);

            // 2. 如果操作员不是管理员，则反向操作操作员自己的账户
            if (!isAdmin) {
                LedgerCreationDTO operatorLedger = LedgerCreationDTO.builder()
                        .userId(operatorId)
                        .amount(amount) // 金额始终为正
                        .ledgerType(isRecharge ? 0 : 1) // 代理是反向操作：给别人充值=自己扣款
                        .fundType(isRecharge ? FundType.AGENT_DEDUCTION : FundType.AGENT_RECHARGE)
                        .remark((isRecharge ? "为下级充值: " : "从下级扣款: ") + targetUserId)
                        .build();
                ledgerService.createLedgerAndUpdateBalance(operatorLedger);
            }
        } catch (BusinessException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return CommonResultDTO.error(Constants.ERROR_INSUFFICIENT_BALANCE, e.getMessage());
        } catch (Exception e) {
            log.error("资金操作失败，未知异常", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "系统内部错误，操作失败");
        }

        return CommonResultDTO.success("操作成功");
    }

    @Override
    public CommonResultDTO<?> rechargeUser(Long targetUserId, BigDecimal amount, Long operatorId) {
        return chargeUser(targetUserId, amount, operatorId, true);
    }

    @Override
    public CommonResultDTO<?> deductUser(Long targetUserId, BigDecimal amount, Long operatorId) {
        return chargeUser(targetUserId, amount, operatorId, false);
    }


    @Override
    public boolean isUserAllowed(Long userId) {
        User user = this.getById(userId);
        if (user == null || user.getStatus() != 0) return false;
        // 假设 `hasOngoingRecord` 从号码记录表中查询
        boolean hasOngoingRecord = false; // TODO: 实现查询逻辑
        return BalanceUtil.canGetNumber(user, hasOngoingRecord);
    }


    @Override
    public void updateUserStatsForNewNumber(Long userId, boolean codeReceived) {
        // 使用原子更新，性能更高且避免并发问题
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<User>()
                .setSql("daily_get_count = daily_get_count + 1, total_get_count = total_get_count + 1")
                .eq(User::getId, userId);

        if (codeReceived) {
            updateWrapper.setSql("daily_code_count = daily_code_count + 1, total_code_count = total_code_count + 1");
        }

        this.update(updateWrapper);
    }

    @Override
    public void resetDailyStatsAllUsers() {
        log.info("开始执行每日统计数据重置任务...");
        boolean success = this.update(new LambdaUpdateWrapper<User>()
                .set(User::getDailyGetCount, 0)
                .set(User::getDailyCodeCount, 0)
                .set(User::getDailyCodeRate, 0.0)
        );
        log.info("每日统计数据重置任务完成，结果: {}", success);
    }

    // --- 私有辅助方法 ---

    /**
     * [新增] 获取指定用户的项目线路价格配置
     */
    @Override
    public List<UserProjectLine> getUserProjectLines(Long userId) {
        return userProjectLineService.getLinesByUserId(userId);
    }

    /**
     * [已废弃 -> 移除]
     * 安全地更新用户余额（带悲观锁）的方法已被移除。
     * 所有资金变动均通过 UserLedgerService.createLedgerAndUpdateBalance() 实现，
     * 该方法内部已包含锁和事务管理，确保了数据一致性。
     */
    // private void updateBalanceWithLock(Long userId, BigDecimal amount) { ... }
    /**
     * [重构] 将 User 实体转换为 UserDTO，价格从新表获取
     */
    public UserDTO convertToDTO(User user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setUserId(user.getId());
        dto.setBalance(user.getBalance());
        dto.setStatus(user.getStatus());
        dto.setIsAgent(user.getIsAgent() == 1);
        dto.setParentId(user.getParentId());

        // 从 user_project_line 表获取价格信息
        List<UserProjectLine> userLines = userProjectLineService.getLinesByUserId(user.getId());
        if (!CollectionUtils.isEmpty(userLines)) {
            Map<String, BigDecimal> pricesMap = userLines.stream()
                    .collect(Collectors.toMap(
                            line -> line.getProjectId() + "-" + line.getLineId(),
                            UserProjectLine::getAgentPrice,
                            (price1, price2) -> price1 // 如果有重复key，保留第一个
                    ));
            dto.setProjectPrices(pricesMap);
        } else {
            dto.setProjectPrices(Collections.emptyMap());
        }

        return dto;
    }

    /**
     * [重构] 校验为下级设置的价格是否合规
     */
    private void validateProjectPrices(List<ProjectPriceDTO> pricesToSet, User operator) {
        if (CollectionUtils.isEmpty(pricesToSet)) {
            return;
        }
        boolean isAdmin = (operator.getId() != null && operator.getId() == 0L);
        Map<String, ProjectPriceSummaryDTO> priceSummaries = projectService.getAllProjectPriceSummaries();

        Map<String, BigDecimal> operatorPrices = Collections.emptyMap();
        if (!isAdmin) {
            List<UserProjectLine> operatorLines = userProjectLineService.getLinesByUserId(operator.getId());
            if (!CollectionUtils.isEmpty(operatorLines)) {
                operatorPrices = operatorLines.stream().collect(Collectors.toMap(
                        line -> line.getProjectId() + "-" + line.getLineId(),
                        UserProjectLine::getAgentPrice
                ));
            }
        }

        for (ProjectPriceDTO priceDto : pricesToSet) {
            String priceKey = priceDto.getProjectId() + "-" + priceDto.getLineId();
            BigDecimal priceToSet = priceDto.getPrice();
            String projectId = String.valueOf(priceDto.getProjectId());

            ProjectPriceSummaryDTO summary = priceSummaries.get(projectId);
            if (summary == null) {
                log.warn("价格校验警告: 项目 '{}' 没有找到价格摘要信息，跳过此项校验。", projectId);
                continue;
            }

            if (isAdmin) {
                if (priceToSet.compareTo(summary.getMinPrice()) < 0) {
                    throw new IllegalArgumentException(String.format("价格设置错误：线路 %s 的价格 %s 不能低于项目最低价 %s", priceKey, priceToSet, summary.getMinPrice()));
                }
            } else { // 代理
                BigDecimal operatorCost = operatorPrices.get(priceKey);
                if (operatorCost == null) {
                    throw new IllegalArgumentException(String.format("价格设置错误：您没有线路 %s 的价格配置，无法为下级设置", priceKey));
                }
                if (priceToSet.compareTo(operatorCost) < 0) {
                    throw new IllegalArgumentException(String.format("价格设置错误：线路 %s 的价格 %s 不能低于您的成本价 %s", priceKey, priceToSet, operatorCost));
                }
            }

            if (priceToSet.compareTo(summary.getMaxPrice()) > 0) {
                throw new IllegalArgumentException(String.format("价格设置错误：线路 %s 的价格 %s 不能高于项目最高价 %s", priceKey, priceToSet, summary.getMaxPrice()));
            }
        }
        log.info("操作员 {} 为下级设置的价格已通过校验。", operator.getId());
    }

    /**
     * [重构] 为新用户生成初始价格配置
     */
    private List<ProjectPriceDTO> generateInitialPricesFor(User operator) {
        boolean isAdmin = (operator.getId() != null && operator.getId() == 0L);

        if (!isAdmin) {
            List<UserProjectLine> operatorLines = userProjectLineService.getLinesByUserId(operator.getId());
            if (!CollectionUtils.isEmpty(operatorLines)) {
                log.info("为新用户生成初始价格：成功继承自操作员 {} 的价格配置。", operator.getId());
                return operatorLines.stream().map(line -> {
                    ProjectPriceDTO dto = new ProjectPriceDTO();
                    dto.setProjectId(Long.parseLong(line.getProjectId()));
                    dto.setLineId(Long.parseLong(line.getLineId()));
                    dto.setPrice(line.getAgentPrice());
                    return dto;
                }).collect(Collectors.toList());
            }
        }

        log.info("操作员 {} 无价格配置或为管理员，为新用户生成初始价格：采用系统默认最高价策略。", operator.getId());
        Map<String, ProjectPriceSummaryDTO> priceSummaries = projectService.getAllProjectPriceSummaries();
        return projectService.list().stream()
                .map(line -> {
                    ProjectPriceSummaryDTO summary = priceSummaries.get(line.getProjectId());
                    BigDecimal price = (summary != null && summary.getMaxPrice() != null) ? summary.getMaxPrice() : line.getPriceMin();
                    ProjectPriceDTO dto = new ProjectPriceDTO();
                    dto.setProjectId(Long.parseLong(line.getProjectId()));
                    dto.setLineId(Long.parseLong(line.getLineId()));
                    dto.setPrice(price);
                    return dto;
                }).collect(Collectors.toList());
    }

    /**
     * 通过用户名查询用户
     */
    @Override
    public User getByUserName(String userName){
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName,userName);
        return this.getOne(queryWrapper);
    }

    @Override
    public Boolean login(UserLoginDto userLoginDto) {
        User user = getByUserName(userLoginDto.getUsername());
        if (user == null) return false;
        // 在这里应加入密码校验逻辑
        return true;
    }

    @Override
    public Boolean regist(UserResultDTO userDTO) {
        User user = getByUserName(userDTO.getUserName());
        if (user != null) {
            throw new BusinessException(0,"用户已经注册过了");
        }
        User uu = new User();
        //todo
        BeanUtil.copyProperties(userDTO, uu);
        return userMapper.insert(uu)>0;
    }
}