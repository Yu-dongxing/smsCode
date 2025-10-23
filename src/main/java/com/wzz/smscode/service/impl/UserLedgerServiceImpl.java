package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.CreatDTO.LedgerCreationDTO;
import com.wzz.smscode.dto.EntityDTO.LedgerDTO;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.enums.FundType;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.UserLedgerMapper;
import com.wzz.smscode.service.UserLedgerService;
import com.wzz.smscode.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserLedgerServiceImpl extends ServiceImpl<UserLedgerMapper, UserLedger> implements UserLedgerService {

    @Autowired
    @Lazy
    private UserService userService; // 注入用户服务用于身份认证

    /**
     * 查询用户资金列表
     * @param userId 用户id
     * @param password 用户密码
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 分页
     * @return 分页的用户资金列表
     */
    @Override
    public IPage<LedgerDTO> listUserLedger(Long userId, String password, Date startTime, Date endTime, Page<UserLedger> page){
        // 1. 身份验证
        User user = userService.authenticate(userId, password);
        if (user == null) {
            log.warn("用户 {} 账本查询失败：身份验证未通过", userId);
            return null;
        }

        // 2. 构建查询条件
        LambdaQueryWrapper<UserLedger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLedger::getUserId, userId); // 严格限制只能查询自己的记录
        wrapper.ge(startTime != null, UserLedger::getTimestamp, startTime);
        wrapper.le(endTime != null, UserLedger::getTimestamp, endTime);
        wrapper.orderByDesc(UserLedger::getTimestamp);

        Page<UserLedger> ledgerPage = this.page(page, wrapper);
        return ledgerPage.convert(this::convertToDTO);
    }

    @Override
    public IPage<LedgerDTO> listAllLedger(Long adminId, String adminPassword, Long filterByUserId, Date startTime, Date endTime, Page<UserLedger> page) {
        // 1. 管理员身份验证
        User admin = userService.authenticate(adminId, adminPassword);
        // TODO: 此处应增加更严格的管理员角色判断
        if (admin == null || admin.getIsAgent() != 1) { // 假设管理员也是一种特殊的代理
            log.warn("管理员 {} 账本查询失败：身份验证或权限不足", adminId);
            return null;
        }

        // 2. 构建查询条件
        LambdaQueryWrapper<UserLedger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(filterByUserId != null && filterByUserId > 0, UserLedger::getUserId, filterByUserId); // 可选的用户筛选
        wrapper.ge(startTime != null, UserLedger::getTimestamp, startTime);
        wrapper.le(endTime != null, UserLedger::getTimestamp, endTime);
        wrapper.orderByDesc(UserLedger::getTimestamp);

        // 3. 执行分页查询
        Page<UserLedger> ledgerPage = this.page(page, wrapper);

        // 4. 转换 DTO
        return ledgerPage.convert(this::convertToDTO);
    }

    /**
     * [优化] 从账本中计算用户余额（用于审计和数据校对）
     * <p>
     * 正常业务中应直接从 User 表的 balance 字段获取余额。
     * 此方法通过 SQL 直接在数据库中计算，性能高且避免内存溢出。
     * </p>
     * @param userId 用户id
     * @return 根据账本计算出的理论总余额
     */
    @Override
    public BigDecimal calculateUserBalanceFromLedger(Long userId) {
        BigDecimal calculatedBalance = baseMapper.sumAmountByUserId(userId);
        return calculatedBalance != null ? calculatedBalance : BigDecimal.ZERO;
    }

    /*
     * Java Stream 版本的实现 (适用于记录数不多的情况):
     *
     * List<UserLedger> records = this.list(new LambdaQueryWrapper<UserLedger>().eq(UserLedger::getUserId, userId));
     * if (records.isEmpty()) {
     *     return BigDecimal.ZERO;
     * }
     * return records.stream()
     *               .map(UserLedger::getAmount) // 假设变动金额字段为 amount
     *               .reduce(BigDecimal.ZERO, BigDecimal::add);
     */

    @Transactional
    @Override
    public boolean createLedgerEntry(Long userId, FundType fundType, BigDecimal amount, BigDecimal balanceAfter, String remarks) {
        if (userId == null || fundType == null || amount == null || balanceAfter == null) {
            throw new IllegalArgumentException("创建账本记录的参数不能为空");
        }

        UserLedger ledger = new UserLedger();
        ledger.setUserId(userId);
        ledger.setFundType(fundType.getCode());
        //todo 用户变动金额出现问题
        ledger.setBalanceAfter(balanceAfter); // 变动后余额
        ledger.setBalanceBefore(balanceAfter.subtract(amount)); // 推算出变动前余额
        ledger.setRemark(remarks);
        ledger.setTimestamp(LocalDateTime.now()); // 设置当前时间

        return this.save(ledger);
    }

    /**
     * 私有辅助方法：将 UserLedger 实体转换为 LedgerDTO
     * @param ledger 数据库实体
     * @return 脱敏后的 DTO
     */
    private LedgerDTO convertToDTO(UserLedger ledger) {
        if (ledger == null) return null;
        LedgerDTO dto = new LedgerDTO();
        // 使用 Spring 的 BeanUtils 快速复制属性
        BeanUtils.copyProperties(ledger, dto);
        // 如果字段名不完全匹配，需要手动设置
        // dto.setPrice(ledger.getAmount());
        return dto;
    }

    /**
     * [核心优化] 统一的账本创建和余额更新方法（接受UserId，Amount）
     * <p>
     * 该方法是系统中所有资金变动的唯一入口，确保事务性和数据一致性。
     * 1. 使用悲观锁锁定用户记录，防止并发问题。
     * 2. 计算新余额并检查余额是否充足（对于出账）。
     * 3. 更新用户表中的余额。
     * 4. 创建详细的资金流水记录。
     * </p>
     *
     * @param request 包含所有账本所需信息的DTO对象
     * @return 操作成功返回true，否则抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createLedgerAndUpdateBalance(LedgerCreationDTO request) {
        // 1. 参数校验
        if (request.getUserId() == null || request.getAmount() == null ||
                request.getLedgerType() == null || request.getFundType() == null ||
                request.getAmount().compareTo(BigDecimal.ZERO) <= 0) { // 金额必须是正数
            throw new IllegalArgumentException("创建账本和更新余额的必要参数缺失或无效");
        }
        User user = userService.findAndLockById(request.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在: " + request.getUserId());
        }

        // 3. 计算余额
        BigDecimal balanceBefore = user.getBalance();
        BigDecimal newBalance;
        BigDecimal amount = request.getAmount();

        // 根据账本类型（入账/出账）计算新余额
        if (request.getLedgerType() == 1) { // 1-入账
            newBalance = balanceBefore.add(amount);
        } else if (request.getLedgerType() == 0) { // 0-出账
            newBalance = balanceBefore.subtract(amount);
            // 出账时，检查余额是否充足
            if (newBalance.signum() < 0) {
                throw new BusinessException("用户 " + request.getUserId() + " 余额不足");
            }
        } else {
            throw new IllegalArgumentException("无效的账本类型: " + request.getLedgerType());
        }

        // 4. 更新用户余额
        user.setBalance(newBalance);
        boolean userUpdateSuccess = userService.updateById(user);
        if (!userUpdateSuccess) {
            log.error("更新用户 {} 余额失败!", user.getId());
            throw new RuntimeException("更新用户余额失败，事务已回滚");
        }

        // 5. 创建并保存账本记录
        UserLedger ledger = new UserLedger();
        ledger.setUserId(request.getUserId());
        ledger.setPrice(amount); // `price` 字段记录变动额，始终为正
        ledger.setLedgerType(request.getLedgerType());
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(newBalance);
        ledger.setFundType(request.getFundType().getCode());
        ledger.setTimestamp(LocalDateTime.now());
        ledger.setRemark(request.getRemark());

        return this.save(ledger);
    }

    @Override
    public IPage<UserLedger> listSubordinateLedgers(Long agentId, Page<UserLedger> page, Long targetUserId, Date startTime, Date endTime) {
        // 1. 获取该代理的所有下级用户的ID
        LambdaQueryWrapper<User> userQuery = new LambdaQueryWrapper<User>().eq(User::getParentId, agentId);
        List<User> subUsers = userService.list(userQuery);

        if (CollectionUtils.isEmpty(subUsers)) {
            // 如果没有下级用户，直接返回空的分页结果
            return new Page<>(page.getCurrent(), page.getSize(), 0);
        }
        List<Long> subUserIds = subUsers.stream().map(User::getId).collect(Collectors.toList());

        // 2. 构建资金流水的查询条件
        LambdaQueryWrapper<UserLedger> ledgerWrapper = new LambdaQueryWrapper<>();

        // 3. 安全性校验与核心查询条件
        if (targetUserId != null) {
            // 如果传入了 targetUserId，必须校验该用户是否是当前代理的下级
            if (!subUserIds.contains(targetUserId)) {
                // 抛出异常或返回空，这里抛出异常让 Controller 捕获更清晰
                throw new SecurityException("试图查询的用户不属于该代理");
            }
            // 只查询这一个下级用户
            ledgerWrapper.eq(UserLedger::getUserId, targetUserId);
        } else {
            // 查询所有下级用户
            ledgerWrapper.in(UserLedger::getUserId, subUserIds);
        }

        // 4. 添加其他筛选条件
        if (startTime != null) {
            ledgerWrapper.ge(UserLedger::getTimestamp, startTime);
        }
        if (endTime != null) {
            ledgerWrapper.le(UserLedger::getTimestamp, endTime);
        }

        // 5. 按时间倒序排序
        ledgerWrapper.orderByDesc(UserLedger::getTimestamp);

        // 6. 执行分页查询并返回
        return this.page(page, ledgerWrapper);
    }


}