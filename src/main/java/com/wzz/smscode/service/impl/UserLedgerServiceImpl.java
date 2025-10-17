package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.LedgerDTO;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.enums.FundType;
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
     * 根据用户ID计算其账本中所有金额的总和
     * @param userId 用户id
     * @return 数值
     */
    //todo  用户金额计算出现错误
    @Override
    public BigDecimal calculateUserBalanceFromLedger(Long userId) {
        // 为避免内存溢出，不应一次性加载所有记录。
        // 最优解是直接在数据库层面进行计算。
        return baseMapper.sumAmountByUserId(userId);
        // 你需要在 UserLedgerMapper 中添加一个自定义的 SUM 查询方法
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
}