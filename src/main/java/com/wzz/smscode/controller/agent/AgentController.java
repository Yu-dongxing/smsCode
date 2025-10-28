// src/main/java/com/wzz/smscode/controller/agent/AgentController.java
package com.wzz.smscode.controller.agent;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.common.Result;
import com.wzz.smscode.dto.AgentDashboardStatsDTO;
import com.wzz.smscode.dto.AgentProjectLineUpdateDTO;
import com.wzz.smscode.dto.AgentProjectPriceDTO;
import com.wzz.smscode.dto.CreatDTO.UserCreateDTO;
import com.wzz.smscode.dto.EntityDTO.LedgerDTO;
import com.wzz.smscode.dto.EntityDTO.UserDTO;
import com.wzz.smscode.dto.LoginDTO.AgentLoginDTO;
import com.wzz.smscode.dto.SubUserProjectPriceDTO;
import com.wzz.smscode.dto.update.UserUpdateDtoByUser;
import com.wzz.smscode.entity.SystemConfig;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.service.SystemConfigService;
import com.wzz.smscode.service.UserLedgerService;
import com.wzz.smscode.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
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
    public Result<?> login(@RequestBody AgentLoginDTO loginDTO) {
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
    public Result<?> listUsers(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        // 通过 Sa-Token 获取当前登录的代理ID
        long agentId = StpUtil.getLoginIdAsLong();

        // 可以在这里再次校验代理身份，但更推荐在 Sa-Token 的拦截器或全局逻辑中统一处理
        checkAgentPermission(agentId);

        IPage<User> pageRequest = new Page<>(page, size);
        IPage<User> subUsersPage = userService.listSubUsers(agentId, pageRequest);

        return Result.success(subUsersPage);
    }
    @Autowired
    private SystemConfigService systemConfigService;
    /**
     * 获取公告接口
     */
    @GetMapping("/notice")
    public Result<?> getUserNotice(){
        SystemConfig config = systemConfigService.getConfig();
        return Result.success(config.getSystemNotice());
    }

    /**
     * 创建一个下级用户账号
     */
    @SaCheckLogin
    @PostMapping("/createUser")
    public Result<?> createUserbyAgent( @RequestBody UserCreateDTO userCreateDTO) {
        long agentId = StpUtil.getLoginIdAsLong();
        if (userCreateDTO.getUsername() == null || userCreateDTO.getPassword() == null) {
            return Result.error("用户名或者密码参数为空");
        }
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
    public Result<?> updateUser(@RequestBody UserUpdateDtoByUser userDTO) {
        long agentId = StpUtil.getLoginIdAsLong();
        try {
            // 权限校验已转移到 Service 层，Service 层需要使用 agentId 来判断权限
            boolean success = userService.updateUserByAgent(userDTO, agentId);
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
        log.info("为下级用户充值{}::{}", agentId, targetUserId);
        try {
            // 业务逻辑委托给 Service 层，Service 层会进行权限和余额等校验
            userService.rechargeUserFromAgentBalance(targetUserId, amount, agentId);
            return Result.success("充值成功");
        }catch (BusinessException e){
            return Result.success("充值失败",e.getMessage());
        }
        catch(Exception e) {
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
        try {
            userService.deductUserToAgentBalance(targetUserId, amount, agentId);
            return Result.success("扣款成功");
        }
        catch (BusinessException e){
            return Result.success("扣款失败",e.getMessage());
        }
        catch (Exception e) {
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

    /**
     * 查询当前代理登录用户的所有下级的资金流水表（分页）
     *
     * @param targetUserId (可选) 指定下级用户ID，用于筛选特定用户的流水
     * @param startTime    (可选) 查询起始时间
     * @param endTime      (可选) 查询结束时间
     * @param page         当前页码 默认 1
     * @param size         每页大小 默认 10
     * @return 分页后的资金流水 DTO 列表
     */
    @SaCheckLogin
    @GetMapping("/subordinate-ledgers")
    public Result<IPage<LedgerDTO>> viewAllSubordinateLedgers(
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) Date startTime,
            @RequestParam(required = false) Date endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        long agentId = StpUtil.getLoginIdAsLong();
        checkAgentPermission(agentId); // 复用权限检查

        try {
            Page<UserLedger> pageRequest = new Page<>(page, size);
            IPage<UserLedger> ledgerPage = userLedgerService.listSubordinateLedgers(agentId, pageRequest, targetUserId, startTime, endTime);
            IPage<LedgerDTO> dtoPage = ledgerPage.convert(ledger -> {
                LedgerDTO dto = new LedgerDTO();
                dto.setId(ledger.getId());
                dto.setUserId(ledger.getUserId());
                dto.setFundType(ledger.getFundType());
                dto.setPrice(ledger.getBalanceAfter().subtract(ledger.getBalanceBefore()));
                dto.setBalanceBefore(ledger.getBalanceBefore());
                dto.setBalanceAfter(ledger.getBalanceAfter());
                dto.setRemark(ledger.getRemark());
                dto.setTimestamp(ledger.getTimestamp());
                return dto;
            });

            return Result.success(dtoPage);

        } catch (SecurityException e) {
            log.warn("代理 {} 尝试查询不属于自己的下级 {} 的流水: {}", agentId, targetUserId, e.getMessage());
            return Result.error( "权限不足，无法查询该用户的流水");
        } catch (Exception e) {
            log.error("代理 {} 查询所有下级流水分页时发生系统内部错误", agentId, e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "查询流水失败，请稍后重试");
        }
    }

    /**
     * 获取代理仪表盘统计数据
     * @return 包含余额、下级总数、今日下级充值、下级回码率的 Result 对象
     */
    @SaCheckLogin
    @GetMapping("/dashboard-stats")
    public Result<AgentDashboardStatsDTO> getDashboardStats() {
        long agentId = StpUtil.getLoginIdAsLong();
        try {
            // 调用 Service 层获取统计数据
            AgentDashboardStatsDTO stats = userService.getAgentDashboardStats(agentId);
            return Result.success(stats);
        } catch (BusinessException e) {
            log.warn("获取代理 {} 仪表盘数据业务异常: {}", agentId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取代理 {} 的仪表盘数据时发生系统内部错误", agentId, e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "获取统计数据失败，请稍后重试");
        }
    }
    /**
     * 查询当前代理用户的项目价格配置
     */
//    @GetMapping("/get/by-agent/project")
//    public Result<?> getByAgenrToProject(){
//        try{
//            StpUtil.checkLogin();
//            Long agentId = StpUtil.getLoginIdAsLong();
//            List<AgentProjectPriceDTO> agentProjectPrices = userService.getAgentProjectPrices(agentId);
//            if (agentProjectPrices.isEmpty()){
//                return Result.success("获取成功，暂无数据");
//            }
//            return Result.success("获取成功",agentProjectPrices);
//        }catch (BusinessException e){
//            return Result.error("获取失败！");
//        }
//    }

    /**
     * 查询当前代理用户的所有下级的项目价格配置
     */
    @GetMapping("/get/by-agent/project")
    public Result<?> getSubUsersProjectPrices() {
        try {
            // 假设你使用了 Sa-Token 或类似框架进行登录校验
            StpUtil.checkLogin();
            Long agentId = StpUtil.getLoginIdAsLong();

            List<SubUserProjectPriceDTO> result = userService.getSubUsersProjectPrices(agentId);

            if (result.isEmpty()) {
                return Result.success("查询成功，暂无下级用户或价格数据");
            }
            return Result.success("查询成功", result);

        } catch (BusinessException e) {
            log.error("查询下级项目价格失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询下级项目价格时发生未知错误", e);
            return Result.error("系统错误，请联系管理员");
        }
    }

    /**
     * 更新当前代理用户的项目线路配置（如价格、备注等）传入的是项目配置表的id不是项目id和线路id
     */
    @PostMapping("/update/by-agent/project-config")
    public Result<?> updateAgentProjectConfig(@RequestBody AgentProjectLineUpdateDTO updateDTO) {
        try {
            StpUtil.checkLogin();
            Long agentId = StpUtil.getLoginIdAsLong();
            userService.updateAgentProjectConfig(agentId, updateDTO);
            return Result.success("配置更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新代理项目配置时发生未知错误", e);
            return Result.error("更新失败，系统内部错误");
        }
    }
}