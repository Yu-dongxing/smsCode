// src/main/java/com/wzz/smscode/controller/agent/AgentController.java
package com.wzz.smscode.controller.agent;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.common.Result;
import com.wzz.smscode.dto.CreatDTO.UserCreateDTO;
import com.wzz.smscode.dto.EntityDTO.LedgerDTO;
import com.wzz.smscode.dto.EntityDTO.UserDTO;
import com.wzz.smscode.dto.LoginDTO.AgentLoginDTO;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.service.UserLedgerService;
import com.wzz.smscode.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

/**
 * 代理后台接口控制器
 * <p>
 * 提供下级用户管理、资金操作等功能。
 * 所有接口（除登录外）都需要通过 Sa-Token 进行登录认证。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LogManager.getLogger(AgentController.class);
    @Autowired
    private UserService userService;

    @Autowired
    private UserLedgerService userLedgerService;

    /**
     * 代理登录接口
     *
     * @param loginDTO 登录信息 (username, password)
     * @return 包含 Token 的 Result 对象
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody AgentLoginDTO loginDTO) {
        // 调用业务层进行登录验证
        User agent = userService.AgentLogin(loginDTO.getUsername(), loginDTO.getPassword());

        // 验证失败
        if (agent == null) {
            return Result.error("用户名或密码错误");
        }
        // 验证是否为代理
        if (agent.getIsAgent() != 1) {
            return Result.error(403, "权限不足，非代理用户");
        }
        // 登录成功，使用 Sa-Token 创建会话
        StpUtil.login(agent.getId());
        // 返回 Token 信息
        return Result.success("登录成功", StpUtil.getTokenValue());
    }

    /**
     * 查询所有下级用户列表（分页）
     */
    @SaCheckLogin // 使用注解，如果未登录，直接抛出异常，由全局异常处理器返回JSON
    @GetMapping("/listUsers") // 推荐使用更具体的 GetMapping
    public Result<IPage<UserDTO>> listUsers(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        // 通过 Sa-Token 获取当前登录的代理ID
        long agentId = StpUtil.getLoginIdAsLong();

        // 可以在这里再次校验代理身份，但更推荐在 Sa-Token 的拦截器或全局逻辑中统一处理
        checkAgentPermission(agentId);

        IPage<User> pageRequest = new Page<>(page, size);
        IPage<UserDTO> subUsersPage = userService.listSubUsers(agentId, pageRequest);

        return Result.success(subUsersPage);
    }

    /**
     * 创建一个下级用户账号
     */
    @SaCheckLogin
    @PostMapping("/createUser")
    public Result<?> createUser(@RequestBody UserCreateDTO userCreateDTO) {
        long agentId = StpUtil.getLoginIdAsLong();
        try {
            boolean success = userService.createUser(userCreateDTO, agentId);
            return success ? Result.success("创建成功") : Result.error("创建失败，请稍后重试");
        } catch (IllegalArgumentException | SecurityException | IllegalStateException | BusinessException e) {
            // 记录业务异常信息，但返回通用错误提示
            log.warn("创建用户业务校验失败: {}", e.getMessage());
            return Result.error("创建失败，输入信息有误或权限不足");
        } catch (Exception e) {
            // 记录未预料到的系统异常
            log.error("创建用户时发生系统内部错误", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "创建用户时发生系统内部错误，请联系管理员");
        }
    }

    /**
     * 修改下级用户的信息
     */
    @SaCheckLogin
    @PostMapping("/updateUser")
    public Result<?> updateUser(@RequestBody UserDTO userDTO) {
        long agentId = StpUtil.getLoginIdAsLong();
        try {
            // 权限校验已转移到 Service 层，Service 层需要使用 agentId 来判断权限
            boolean success = userService.updateUser(userDTO, agentId);
            return success ? Result.success("修改成功") : Result.error("信息无变化或修改失败");
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("修改用户信息业务校验失败: {}", e.getMessage());
            return Result.error("修改失败，提交的数据不合法或无权操作");
        } catch (Exception e) {
            log.error("修改用户信息时发生系统内部错误", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "修改用户信息时发生系统内部错误，请联系管理员");
        }
    }

    /**
     * 为下级用户充值
     */
    @SaCheckLogin
    @PostMapping("/rechargeUser")
    public Result<?> rechargeUser(
            @RequestParam Long targetUserId,
            @RequestParam BigDecimal amount) {
        long agentId = StpUtil.getLoginIdAsLong();
        checkAgentPermission(agentId);

        try {
            // 业务逻辑委托给 Service 层，Service 层会进行权限和余额等校验
            userService.rechargeUser(targetUserId, amount, agentId);
            return Result.success("充值成功");
        } catch(Exception e) {
            // 记录异常信息
            log.error("代理 {} 为用户 {} 充值 {} 元时失败", agentId, targetUserId, amount, e);
            return Result.error("充值失败，请稍后重试");
        }
    }

    /**
     * 扣减下级用户余额
     */
    @SaCheckLogin
    @PostMapping("/deductUser")
    public Result<?> deductUser(
            @RequestParam Long targetUserId,
            @RequestParam BigDecimal amount) {
        long agentId = StpUtil.getLoginIdAsLong();
        checkAgentPermission(agentId);

        // 业务逻辑委托给 Service 层
        try {
            userService.deductUser(targetUserId, amount, agentId);
            return Result.success("扣款成功");
        } catch (Exception e) {
            log.error("代理 {} 为用户 {} 扣款 {} 元时失败", agentId, targetUserId, amount, e);
            return Result.error("扣款失败，请稍后重试");
        }
    }

    /**
     * 查看某个下级用户的资金账本
     */
    @SaCheckLogin
    @GetMapping("/viewUserLedger") // 推荐使用更具体的 GetMapping
    public Result<IPage<LedgerDTO>> viewUserLedger(
            @RequestParam Long targetUserId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        long agentId = StpUtil.getLoginIdAsLong();
        checkAgentPermission(agentId);

        // 验证 targetUserId 是否是该代理的下级
        User targetUser = userService.getById(targetUserId);
        if (targetUser == null || !Objects.equals(targetUser.getParentId(), agentId)) {
            return Result.error(403, "无权查看该用户的账本");
        }

        Page<UserLedger> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<UserLedger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLedger::getUserId, targetUserId);
        if (startTime != null) {
            wrapper.ge(UserLedger::getTimestamp, startTime);
        }
        if (endTime != null) {
            wrapper.le(UserLedger::getTimestamp, endTime);
        }
        wrapper.orderByDesc(UserLedger::getTimestamp);

        IPage<UserLedger> ledgerPage = userLedgerService.page(pageRequest, wrapper);

        // 手动转换 DTO
        IPage<LedgerDTO> dtoPage = ledgerPage.convert(ledger -> {
            LedgerDTO dto = new LedgerDTO();
            // ... (原转换逻辑不变)
            dto.setId(ledger.getId());
            dto.setUserId(ledger.getUserId());
            dto.setFundType(ledger.getFundType());
            dto.setPrice(ledger.getBalanceAfter().subtract(ledger.getBalanceBefore())); // 动态计算变动金额
            dto.setBalanceBefore(ledger.getBalanceBefore());
            dto.setBalanceAfter(ledger.getBalanceAfter());
            dto.setRemark(ledger.getRemark());
            dto.setTimestamp(ledger.getTimestamp());
            return dto;
        });

        return Result.success(dtoPage);
    }

    /**
     * 检查当前登录用户是否为有效代理 (内部使用)
     * 如果不是，则直接抛出异常
     * @param agentId 代理ID
     */
    private void checkAgentPermission(Long agentId) {
        User agent = userService.getById(agentId);
        // 在 SaCheckLogin 后，agent 一般不会为 null，但做个健壮性检查
        if (agent == null || agent.getIsAgent() != 1) {
            // 抛出异常，让全局异常处理器捕获，可以自定义一个业务异常
            throw new SecurityException("权限不足，非代理用户");
        }
    }
}