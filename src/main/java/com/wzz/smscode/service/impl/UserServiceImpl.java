package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.dto.CommonResultDTO;
import com.wzz.smscode.dto.ProjectPriceSummaryDTO;
import com.wzz.smscode.dto.UserCreateDTO;
import com.wzz.smscode.dto.UserDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.enums.FundType;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.UserMapper;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.service.UserLedgerService;
import com.wzz.smscode.service.UserService;
import com.wzz.smscode.util.BalanceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProjectService projectService;
    @Autowired private UserLedgerService ledgerService; // 假设存在账本服务
    @Autowired private ObjectMapper objectMapper;

    @Override
    public User authenticate(Long userId, String password) {
        User user = this.getById(userId);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    @Override
    public CommonResultDTO<BigDecimal> getBalance(Long userId, String password) {
        User user = authenticate(userId, password);
        if (user == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户ID或密码错误");
        }
        return CommonResultDTO.success("查询成功", user.getBalance());
    }

    @Transactional
    @Override
    public boolean createUser(UserCreateDTO dto, Long operatorId) {
        User operator = this.getById(operatorId);
        if (operator == null) throw new IllegalArgumentException("操作员不存在");
        // 假设管理员的 isAgent 也为 1，或者有专门的 isAdmin 字段/角色
        if (operator.getIsAgent() != 1) throw new SecurityException("无权创建用户");

        User user = new User();
//        user.setId(dto.getUserId()); // 如果ID是自增，请移除此行
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setParentId(operatorId);
        user.setIsAgent(dto.getIsAgent() ? 1 : 0);
        user.setStatus(0); // 默认正常状态
        user.setBalance(dto.getInitialBalance() != null ? dto.getInitialBalance() : BigDecimal.ZERO);

        // --- 价格配置处理 ---
        Map<String, BigDecimal> finalPrices = dto.getProjectPrices();
        if (finalPrices == null || finalPrices.isEmpty()) {
            // 如果未提供价格，则继承上级或使用系统默认价
            finalPrices = generateInitialPricesFor(operator);
        } else {
            // 如果提供了价格，则进行校验
            validateProjectPrices(finalPrices, operator);
        }
        try {
            user.setProjectPrices(objectMapper.writeValueAsString(finalPrices));
        } catch (Exception e) {
            throw new RuntimeException("序列化价格JSON失败", e);
        }

        // 保存用户
        boolean saved = this.save(user);
        if (!saved) return false;

        // --- 初始余额与账本处理 ---
        if (dto.getInitialBalance() != null && dto.getInitialBalance().compareTo(BigDecimal.ZERO) > 0) {
            boolean isAdmin = false; // TODO: 实现管理员判断逻辑

            // 下级用户增加余额记录
            ledgerService.createLedgerEntry(user.getId(), FundType.AGENT_RECHARGE, dto.getInitialBalance(), user.getBalance(), "新用户初始充值");

            if (!isAdmin) { // 操作者是代理
                // 从代理余额扣除
                updateBalanceWithLock(operatorId, dto.getInitialBalance().negate());
                // 代理扣款记录
                User updatedOperator = this.getById(operatorId); // 获取最新余额
                ledgerService.createLedgerEntry(operatorId, FundType.AGENT_DEDUCTION, dto.getInitialBalance().negate(), updatedOperator.getBalance(), "为下级 " + user.getId() + " 充值");
            }
        }
        return true;
    }

    @Transactional
    @Override
    public boolean updateUser(UserDTO userDTO, Long operatorId) {
        User targetUser = this.getById(userDTO.getUserId());
        User operator = this.getById(operatorId);
        if (targetUser == null || operator == null) throw new IllegalArgumentException("用户或操作员不存在");

        // 权限校验
        boolean isAdmin = false; // TODO: 实现管理员判断逻辑
        boolean isParent = Objects.equals(targetUser.getParentId(), operatorId);
        if (!isAdmin && !isParent) throw new SecurityException("无权修改该用户信息");

        boolean changed = false;
        // 修改密码
//        if (StringUtils.hasText(userDTO.getPassword())) { // 假设 DTO 中有 password 字段用于修改
//            targetUser.setPassword(passwordEncoder.encode(userDTO.getPassword()));
//            changed = true;
//        }
        // 修改状态（仅管理员）
        if (userDTO.getStatus() != null && isAdmin) {
            targetUser.setStatus(userDTO.getStatus());
            changed = true;
        }
        // 修改价格配置
        if (userDTO.getProjectPrices() != null) {
            validateProjectPrices(userDTO.getProjectPrices(), operator);
            try {
                targetUser.setProjectPrices(objectMapper.writeValueAsString(userDTO.getProjectPrices()));
                changed = true;
            } catch (Exception e) {
                throw new RuntimeException("序列化价格JSON失败", e);
            }
        }
        // 修改代理权限（仅管理员）
        if(userDTO.getIsAgent() != null && isAdmin) {
            targetUser.setIsAgent(userDTO.getIsAgent() ? 1 : 0);
            changed = true;
        }

        if(changed) {
            return this.updateById(targetUser);
        }
        return false;
    }


    @Override
    public IPage<UserDTO> listSubUsers(Long operatorId, IPage<User> page) {
        // TODO: 管理员(operatorId)可查询所有用户，需加判断逻辑
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getParentId, operatorId);

        IPage<User> userPage = this.page(page, wrapper);
        return userPage.convert(this::convertToDTO);
    }


    @Transactional
    @Override
    public CommonResultDTO<?> chargeUser(Long targetUserId, BigDecimal amount, Long operatorId, boolean isRecharge) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CommonResultDTO.error(-5, "金额必须为正数");
        }

        User targetUser = this.getById(targetUserId);
        User operator = this.getById(operatorId);
        if (targetUser == null || operator == null) return CommonResultDTO.error(-5, "用户或操作员不存在");

        boolean isAdmin = false; // TODO: 实现管理员判断逻辑
        if (!isAdmin && !Objects.equals(targetUser.getParentId(), operatorId)) {
            return CommonResultDTO.error(-5, "无权操作该用户");
        }

        BigDecimal targetAmount = isRecharge ? amount : amount.negate();
        BigDecimal operatorAmount = isRecharge ? amount.negate() : amount;

        try {
            if (!isAdmin) { // 代理操作
                updateBalanceWithLock(operatorId, operatorAmount);
            }
            // 目标用户资金变动
            updateBalanceWithLock(targetUserId, targetAmount);

            // 记录账本
            User updatedTarget = this.getById(targetUserId);
            User updatedOperator = this.getById(operatorId);

            FundType targetFundType = isRecharge ? FundType.AGENT_RECHARGE : FundType.AGENT_DEDUCTION;
            String targetDesc = (isRecharge ? "上级充值: " : "上级扣款: ") + operatorId;
            ledgerService.createLedgerEntry(targetUserId, targetFundType, targetAmount, updatedTarget.getBalance(), targetDesc);

            if (!isAdmin) {
                FundType operatorFundType = isRecharge ? FundType.AGENT_DEDUCTION : FundType.AGENT_RECHARGE;
                String operatorDesc = (isRecharge ? "为下级充值: " : "从下级扣款: ") + targetUserId;
                ledgerService.createLedgerEntry(operatorId, operatorFundType, operatorAmount, updatedOperator.getBalance(), operatorDesc);
            }

        } catch (BusinessException e) {
            return CommonResultDTO.error(Constants.ERROR_INSUFFICIENT_BALANCE, e.getMessage());
        } catch (Exception e) {
            log.error("资金操作失败", e);
            throw new RuntimeException("资金操作失败，事务已回滚"); // 抛出异常以触发事务回滚
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
        // 回码率的计算建议通过定时任务批量更新，以减少单次操作的数据库压力
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
     * 安全地更新用户余额（带悲观锁）
     */
    private void updateBalanceWithLock(Long userId, BigDecimal amount) {
        User user = baseMapper.selectByIdForUpdate(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在: " + userId);

        BigDecimal newBalance = user.getBalance().add(amount);

        if (newBalance.signum() < 0) {
            throw new BusinessException("用户 " + userId + " 余额不足");
        }

        user.setBalance(newBalance);
        this.updateById(user);
    }

    /**
     * 将 User 实体转换为 UserDTO
     */
    public UserDTO convertToDTO(User user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        // ... (省略 BeanUtils.copyProperties)
        dto.setUserId(user.getId());
        dto.setBalance(user.getBalance());
        dto.setStatus(user.getStatus());
        dto.setIsAgent(user.getIsAgent() == 1);
        dto.setParentId(user.getParentId());
        // ... 复制其他统计字段
        try {
            if (StringUtils.hasText(user.getProjectPrices())) {
                dto.setProjectPrices(objectMapper.readValue(user.getProjectPrices(), new TypeReference<>() {}));
            } else {
                dto.setProjectPrices(Collections.emptyMap());
            }
        } catch (Exception e) {
            log.error("解析用户 {} 价格JSON失败", user.getId(), e);
            dto.setProjectPrices(Collections.emptyMap());
        }
        return dto;
    }

    /**
     * 校验为下级设置的价格是否合规。
     *
     * @param pricesToSet 准备为下级设置的价格 Map
     * @param operator    执行操作的用户（上级代理或管理员）
     * @throws IllegalArgumentException 如果价格不合规，则抛出异常
     */
    private void validateProjectPrices(Map<String, BigDecimal> pricesToSet, User operator) {
        if (pricesToSet == null || pricesToSet.isEmpty()) {
            return; // 允许不设置价格
        }

        boolean isAdmin = false; // TODO: 实现管理员角色判断逻辑, e.g., operator.getRoleId() == 1

        // 1. 获取所有项目的价格摘要（最高价、最低价）
        Map<String, ProjectPriceSummaryDTO> priceSummaries = projectService.getAllProjectPriceSummaries();

        // 2. 如果操作者是代理，则获取其自身的价格配置
        Map<String, BigDecimal> operatorPrices = Collections.emptyMap();
        if (!isAdmin) {
            try {
                if (StringUtils.hasText(operator.getProjectPrices())) {
                    operatorPrices = objectMapper.readValue(operator.getProjectPrices(), new TypeReference<>() {});
                }
            } catch (Exception e) {
                log.error("解析操作员 {} 的价格JSON失败", operator.getId(), e);
                throw new RuntimeException("操作员价格配置错误，无法完成校验");
            }
        }

        // 3. 遍历待设置的每一项价格进行校验
        for (Map.Entry<String, BigDecimal> entry : pricesToSet.entrySet()) {
            String priceKey = entry.getKey();
            BigDecimal priceToSet = entry.getValue();
            String projectId = priceKey.split("-")[0];

            ProjectPriceSummaryDTO summary = priceSummaries.get(projectId);
            if (summary == null) {
                log.warn("价格校验警告: 线路 '{}' 所属的项目 '{}' 没有找到价格摘要信息，跳过此项校验。", priceKey, projectId);
                continue;
            }

            if (isAdmin) {
                // 管理员规则：价格必须在项目的 [min, max] 区间内
                if (priceToSet.compareTo(summary.getMinPrice()) < 0) {
                    throw new IllegalArgumentException(String.format("价格设置错误：线路 %s 的价格 %s 不能低于项目最低价 %s", priceKey, priceToSet, summary.getMinPrice()));
                }
                if (priceToSet.compareTo(summary.getMaxPrice()) > 0) {
                    throw new IllegalArgumentException(String.format("价格设置错误：线路 %s 的价格 %s 不能高于项目最高价 %s", priceKey, priceToSet, summary.getMaxPrice()));
                }
            } else {
                // 代理规则：价格必须 >= 自己的成本价，且 <= 项目的最高价
                BigDecimal operatorCost = operatorPrices.get(priceKey);
                if (operatorCost == null) {
                    throw new IllegalArgumentException(String.format("价格设置错误：您没有线路 %s 的价格配置，无法为下级设置", priceKey));
                }
                if (priceToSet.compareTo(operatorCost) < 0) {
                    throw new IllegalArgumentException(String.format("价格设置错误：线路 %s 的价格 %s 不能低于您的成本价 %s", priceKey, priceToSet, operatorCost));
                }
                if (priceToSet.compareTo(summary.getMaxPrice()) > 0) {
                    throw new IllegalArgumentException(String.format("价格设置错误：线路 %s 的价格 %s 不能高于项目最高价 %s", priceKey, priceToSet, summary.getMaxPrice()));
                }
            }
        }
        log.info("操作员 {} 为下级设置的价格已通过校验。", operator.getId());
    }

    /**
     * 为新用户生成初始价格配置。
     * 策略：优先继承操作员的价格；若操作员无价格，则使用所有项目的系统最高价。
     *
     * @param operator 创建新用户的操作员
     * @return 生成的初始价格 Map
     */
    private Map<String, BigDecimal> generateInitialPricesFor(User operator) {
        // 策略1: 优先继承操作员的价格
        if (StringUtils.hasText(operator.getProjectPrices())) {
            try {
                Map<String, BigDecimal> inheritedPrices = objectMapper.readValue(operator.getProjectPrices(), new TypeReference<>() {});
                if (!inheritedPrices.isEmpty()) {
                    log.info("为新用户生成初始价格：成功继承自操作员 {} 的价格配置。", operator.getId());
                    return inheritedPrices;
                }
            } catch (Exception e) {
                log.error("解析操作员 {} 的价格JSON失败，将采用系统默认最高价策略。", operator.getId(), e);
                // 解析失败，则降级到策略2
            }
        }

        // 策略2: 操作员无价格或解析失败，使用系统最高价
        log.info("操作员 {} 无价格配置，为新用户生成初始价格：采用系统默认最高价策略。", operator.getId());
        Map<String, BigDecimal> defaultPrices = new HashMap<>();

        List<Project> allLines = projectService.list();
        Map<String, ProjectPriceSummaryDTO> priceSummaries = projectService.getAllProjectPriceSummaries();

        for (Project line : allLines) {
            String priceKey = line.getProjectId() + "-" + line.getLineId();
            ProjectPriceSummaryDTO summary = priceSummaries.get(line.getProjectId());

            if (summary != null && summary.getMaxPrice() != null) {
                defaultPrices.put(priceKey, summary.getMaxPrice());
            } else {
                // 降级保护：如果某个项目没有价格摘要，使用线路自身的基础价格
                log.warn("生成初始价格警告：项目 '{}' 未找到价格摘要，使用线路 '{}' 的基础价格 {} 作为默认价。", line.getProjectId(), priceKey, line.getPriceMin());
                defaultPrices.put(priceKey, line.getPriceMin());
            }
        }

        return defaultPrices;
    }
}