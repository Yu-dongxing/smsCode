package com.wzz.smscode.controller.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.dto.*;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.service.UserLedgerService;
import com.wzz.smscode.service.UserService;
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
 * 所有接口都需要代理用户本人的 userId 和 password 进行认证。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserLedgerService userLedgerService;

    /**
     * 查询所有下级用户列表（分页）
     */
    @RequestMapping(value = "/listUsers", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonResultDTO<IPage<UserDTO>> listUsers(
             @RequestParam Long userId,
             @RequestParam String password,
            @RequestParam(defaultValue = "1") long page,
             @RequestParam(defaultValue = "10") long size) {

        CommonResultDTO<User> checkResult = checkAgentPermission(userId, password);
        if (checkResult.getStatus() != 0) {
            return new CommonResultDTO<>(checkResult.getStatus(), checkResult.getMsg(), null);
        }

        IPage<User> pageRequest = new Page<>(page, size);
        IPage<UserDTO> subUsersPage = userService.listSubUsers(userId, pageRequest);

        return CommonResultDTO.success("查询成功", subUsersPage);
    }

    /**
     * 创建一个下级用户账号
     */
    @PostMapping("/createUser")
    public CommonResultDTO<?> createUser(
             @RequestParam Long userId,
           @RequestParam String password,
             @RequestBody UserCreateDTO userCreateDTO) {
        // 权限校验在 createUser service 方法内部已实现
        try {
            boolean success = userService.createUser(userCreateDTO, userId);
            return success ? CommonResultDTO.success("创建成功", null) : CommonResultDTO.error(-5, "创建失败，请稍后重试");
        } catch (IllegalArgumentException | SecurityException | IllegalStateException e) {
            return CommonResultDTO.error(-5, e.getMessage());
        } catch (Exception e) {
            // 捕获 BusinessException 或其他运行时异常
            return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, e.getMessage());
        }
    }

    /**
     * 修改下级用户的信息
     */
    @PostMapping("/updateUser")
    public CommonResultDTO<?> updateUser(
            @RequestParam Long userId,
            @RequestParam String password,
            @RequestBody UserDTO userDTO) {
        // 权限校验在 updateUser service 方法内部已实现
        try {
            boolean success = userService.updateUser(userDTO, userId);
            return success ? CommonResultDTO.success("修改成功", null) : CommonResultDTO.error(-5, "信息无变化或修改失败");
        } catch (IllegalArgumentException | SecurityException e) {
            return CommonResultDTO.error(-5, e.getMessage());
        } catch (Exception e) {
            return CommonResultDTO.error(Constants.ERROR_SYSTEM_ERROR, "系统内部错误");
        }
    }

    /**
     * 为下级用户充值
     */
    @PostMapping("/rechargeUser")
    public CommonResultDTO<?> rechargeUser(
            @RequestParam Long userId,
            @RequestParam String password,
            @RequestParam Long targetUserId,
            @RequestParam BigDecimal amount) {
        // 身份验证
        User agent = userService.authenticate(userId, password);
        if (agent == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "代理ID或密码错误");
        }
        // 业务逻辑委托给 Service 层，Service 层会进行权限和余额等校验
        return userService.rechargeUser(targetUserId, amount, userId);
    }

    /**
     * 扣减下级用户余额
     */
    @PostMapping("/deductUser")
    public CommonResultDTO<?> deductUser(
             @RequestParam Long userId,
             @RequestParam String password,
             @RequestParam Long targetUserId,
             @RequestParam BigDecimal amount) {
        // 身份验证
        User agent = userService.authenticate(userId, password);
        if (agent == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "代理ID或密码错误");
        }
        // 业务逻辑委托给 Service 层
        return userService.deductUser(targetUserId, amount, userId);
    }

    /**
     * 查看某个下级用户的资金账本
     */
    @RequestMapping(value = "/viewUserLedger", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonResultDTO<IPage<LedgerDTO>> viewUserLedger(
           @RequestParam Long userId,
             @RequestParam String password,
             @RequestParam Long targetUserId,
             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
             @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
             @RequestParam(defaultValue = "1") long page,
             @RequestParam(defaultValue = "10") long size) {

        CommonResultDTO<User> checkResult = checkAgentPermission(userId, password);
        if (checkResult.getStatus() != 0) {
            return new CommonResultDTO<>(checkResult.getStatus(), checkResult.getMsg(), null);
        }

        // 验证 targetUserId 是否是该代理的下级
        User targetUser = userService.getById(targetUserId);
        if (targetUser == null || !Objects.equals(targetUser.getParentId(), userId)) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "无权查看该用户的账本");
        }

        // 由于 UserLedgerService.listUserLedger 需要目标用户密码，这不适用于代理场景。
        // 因此，我们在这里重新构建查询，绕过密码验证。
        Page<UserLedger> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<UserLedger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLedger::getUserId, targetUserId);
        wrapper.ge(startTime != null, UserLedger::getTimestamp, startTime);
        wrapper.le(endTime != null, UserLedger::getTimestamp, endTime);
        wrapper.orderByDesc(UserLedger::getTimestamp);

        IPage<UserLedger> ledgerPage = userLedgerService.page(pageRequest, wrapper);
        // 手动转换 DTO
        IPage<LedgerDTO> dtoPage = ledgerPage.convert(ledger -> {
            LedgerDTO dto = new LedgerDTO();
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

        return CommonResultDTO.success("查询成功", dtoPage);
    }

    /**
     * 检查用户是否为有效代理
     *
     * @param agentId  代理ID
     * @param password 代理密码
     * @return 包含用户实体的 CommonResultDTO，若校验失败则 status != 0
     */
    private CommonResultDTO<User> checkAgentPermission(Long agentId, String password) {
        User agent = userService.authenticate(agentId, password);
        if (agent == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "代理ID或密码错误");
        }
        if (agent.getIsAgent() != 1) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "权限不足，非代理用户");
        }
        return CommonResultDTO.success(agent);
    }
}