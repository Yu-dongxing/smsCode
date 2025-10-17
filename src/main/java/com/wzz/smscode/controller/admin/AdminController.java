package com.wzz.smscode.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.common.Result;
import com.wzz.smscode.dto.*;
import com.wzz.smscode.entity.NumberRecord;
import com.wzz.smscode.entity.SystemConfig;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.service.*;
import com.wzz.smscode.service.impl.UserServiceImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 管理员后台接口控制器
 * <p>
 * 提供系统级的用户管理、资金监控、系统配置等功能。
 * 所有接口都需要管理员ID和密码进行验证。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Value("${admin.username}")
    private String username;

    @Value("${admin.password}")
    private String password;

    @Autowired private UserService userService;
    @Autowired private UserLedgerService userLedgerService;
    @Autowired private NumberRecordService numberRecordService;
    @Autowired private SystemConfigService systemConfigService;
    // @Autowired private ErrorLogService errorLogService; // 假设存在错误日志服务


    //@Data
    //public static class adminUserLogin{}
    /**
     * 管理员用户登录
     */
    @PostMapping("/login")
    public Result<?> adminLogin(){
        return null;
    }


    /**
     * 列出用户列表。
     * 根据提供的参数，可以查询所有用户或特定用户的直接下级。需要管理员权限验证。
     *
     * @param adminId 管理员ID
     * @param password 管理员密码
     * @param parentId 可选参数，指定要查询的用户的父ID；如果提供，则返回该用户的直接下级
     * @param page 页码，默认为1
     * @param size 每页显示的记录数，默认为10
     * @return 返回一个Result对象，包含操作状态、消息以及数据（用户列表）
     */
    @RequestMapping(value = "/listUsers", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<?> listUsers(
            @RequestParam Long adminId,
            @RequestParam String password,
             @RequestParam(required = false) Long parentId,
             @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error("管理员验证失败或无权限");
        }

        if (parentId != null) {
            // 如果提供了 parentId，则调用现有方法查询其直接下级
            return Result.success(userService.listSubUsers(parentId, new Page<>(page, size)));
        }

        // 如果未提供 parentId，则查询所有用户
        IPage<User> pageRequest = new Page<>(page, size);
        IPage<User> userPage = userService.page(pageRequest, new LambdaQueryWrapper<User>().orderByDesc(User::getId));
        return Result.success("查询成功", userPage.convert(((UserServiceImpl) userService)::convertToDTO));
    }

    /**
     * 创建新用户或代理账户
     * @param adminId
     * @param password
     * @param userCreateDTO
     * @return
     */
    @PostMapping("/createUser")
    public Result<?> createUser(
            @RequestParam Long adminId,
            @RequestParam String password,
            @RequestBody UserCreateDTO userCreateDTO) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        try {
            boolean success = userService.createUser(userCreateDTO, adminId);
            return success ? Result.success("创建成功") : Result.error(-5, "创建失败");
        } catch (Exception e) {
            return Result.error(-5, e.getMessage());
        }
    }

    /**
     * 修改用户信息
     * @param adminId
     * @param password
     * @param userDTO
     * @return
     */
    @PostMapping("/updateUser")
    public Result<?> updateUser(
            @RequestParam Long adminId,
            @RequestParam String password,
            @RequestBody UserDTO userDTO) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        try {
            boolean success = userService.updateUser(userDTO, adminId);
            return success ? Result.success("修改成功") : Result.error(-5, "信息无变化或修改失败");
        } catch (Exception e) {
            return Result.error(-5, e.getMessage());
        }
    }

    /**
     * 为用户充值（系统资金）
     * @param adminId 管理员ID
     * @param password 管理员密码
     * @param targetUserId 目标用户ID
     * @param amount 充值金额
     * @return
     */
    @PostMapping("/rechargeUser")
    public CommonResultDTO<?> rechargeUser(
            @RequestParam Long adminId,
            @RequestParam String password,
            @RequestParam Long targetUserId,
            @RequestParam BigDecimal amount) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }
        // 管理员操作，operatorId 传入 adminId，Service 内部会判断操作者是否为管理员，从而决定资金来源
        return userService.rechargeUser(targetUserId, amount, adminId);
    }

    /**
     * 扣减用户余额（回收系统）
     * @param adminId 管理员ID
     * @param password 管理员密码
     * @param targetUserId 目标用户ID
     * @param amount 扣款金额
     * @return
     */
    @PostMapping("/deductUser")
    public CommonResultDTO<?> deductUser(
             @RequestParam Long adminId,
             @RequestParam String password,
            @RequestParam Long targetUserId,
             @RequestParam BigDecimal amount) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }
        return userService.deductUser(targetUserId, amount, adminId);
    }

    /**
     * 删除/禁用用户账户
     * @param adminId 管理员ID
     * @param password 管理员密码
     * @param targetUserId 目标用户ID
     * @return
     */
    @PostMapping("/deleteUser")
    public Result<?> deleteUser(
            @RequestParam Long adminId,
            @RequestParam String password,
            @RequestParam Long targetUserId) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }
        // 采用软删除，将用户状态设置为-1（禁用）
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(targetUserId);
        userDTO.setStatus(-1); // -1 代表禁用
        try {
            boolean success = userService.updateUser(userDTO, adminId);
            return success ? Result.success("用户已禁用") : Result.error(-5, "操作失败");
        } catch (Exception e) {
            return Result.error(-5, e.getMessage());
        }
    }

    // --- 账本与记录查询 ---

    /**
     * 查看指定用户的账本明细
     * @param adminId  管理员ID
     * @param password 管理员密码
     * @param targetUserId 目标用户 ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页数量
     * @return
     */
    @RequestMapping(value = "/viewUserLedger", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<IPage<LedgerDTO>> viewUserLedger(
            @RequestParam Long adminId,
            @RequestParam String password,
            @RequestParam Long targetUserId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        Page<UserLedger> pageRequest = new Page<>(page, size);
        // 管理员可无密码查询任意用户
        IPage<LedgerDTO> resultPage = userLedgerService.listAllLedger(adminId, password, targetUserId, startTime, endTime, pageRequest);
        return Result.success("查询成功", resultPage);
    }

    /**
     * 查看全局账本记录
     * @param adminId 管理员ID
     * @param password 管理员密码
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页数量
     * @return
     */
    @RequestMapping(value = "/viewAllLedger", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<IPage<LedgerDTO>> viewAllLedger(
            @RequestParam Long adminId,
            @RequestParam String password,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
           @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        Page<UserLedger> pageRequest = new Page<>(page, size);
        IPage<LedgerDTO> resultPage = userLedgerService.listAllLedger(adminId, password, null, startTime, endTime, pageRequest);
        return Result.success("查询成功", resultPage);
    }

    /**
     * 查看某用户的号码记录
     * @param adminId 管理员ID
     * @param password 管理员密码
     * @param targetUserId 目标用户ID
     * @param status 状态过滤
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页数量
     * @return
     */
    @RequestMapping(value = "/viewUserNumbers", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<IPage<NumberDTO>> viewUserNumbers(
             @RequestParam Long adminId,
             @RequestParam String password,
             @RequestParam Long targetUserId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        // 管理员无需密码，直接查询
        IPage<NumberRecord> pageRequest = new Page<>(page, size);
        IPage<NumberDTO> resultPage = numberRecordService.listUserNumbers(targetUserId, null, status, startTime, endTime, pageRequest);
        return Result.success("查询成功", resultPage);
    }

    /**
     * 查看全局号码记录
     * @param adminId 管理员ID
     * @param password 管理员密码
     * @param status 状态过滤
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页数量
     * @return
     */
    @RequestMapping(value = "/viewAllNumbers", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<IPage<NumberRecord>> viewAllNumbers(
            @RequestParam Long adminId,
            @RequestParam String password,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long page,
             @RequestParam(defaultValue = "10") long size) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        IPage<NumberRecord> pageRequest = new Page<>(page, size);
        IPage<NumberRecord> resultPage = numberRecordService.listAllNumbers(status, startTime, endTime, pageRequest);
        return Result.success("查询成功", resultPage);
    }

    // --- 系统统计与配置 ---

    @Data
    private static class SystemStatsDTO {
        private Long totalUsers;
        private Long totalAgents;
        private Long totalNumbersReceived;
        private Long totalCodesReceived;
        private String overallCodeRate;
        private Long last24hNumbers;
        private Long last24hCodes;
        private String last24hCodeRate;
    }

    /**
     * 获取系统的总体统计数据。
     *
     * @param adminId 管理员ID，必须提供
     * @param password 管理员密码，必须提供
     * @return 返回一个包含系统统计数据的Result对象。如果管理员验证失败或无权限，则返回错误信息；否则返回成功信息及统计数据。
     */
    @GetMapping("/statistics")
    public Result<SystemStatsDTO> getStatistics(
             @RequestParam Long adminId,
            @RequestParam String password) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        SystemStatsDTO stats = new SystemStatsDTO();
        stats.setTotalUsers(userService.count());
        stats.setTotalAgents(userService.count(new LambdaQueryWrapper<User>().eq(User::getIsAgent, 1)));

        stats.setTotalNumbersReceived(numberRecordService.count());
        stats.setTotalCodesReceived(numberRecordService.count(new LambdaQueryWrapper<NumberRecord>().eq(NumberRecord::getStatus, 2)));
        if (stats.getTotalNumbersReceived() > 0) {
            stats.setOverallCodeRate(
                    BigDecimal.valueOf(stats.getTotalCodesReceived())
                            .divide(BigDecimal.valueOf(stats.getTotalNumbersReceived()), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP) + "%"
            );
        } else {
            stats.setOverallCodeRate("0.00%");
        }

        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        stats.setLast24hNumbers(numberRecordService.count(new LambdaQueryWrapper<NumberRecord>().ge(NumberRecord::getGetNumberTime, yesterday)));
        stats.setLast24hCodes(numberRecordService.count(new LambdaQueryWrapper<NumberRecord>().eq(NumberRecord::getStatus, 2).ge(NumberRecord::getGetNumberTime, yesterday)));
        if (stats.getLast24hNumbers() > 0) {
            stats.setLast24hCodeRate(
                    BigDecimal.valueOf(stats.getLast24hCodes())
                            .divide(BigDecimal.valueOf(stats.getLast24hNumbers()), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP) + "%"
            );
        } else {
            stats.setLast24hCodeRate("0.00%");
        }

        return Result.success("统计数据获取成功", stats);
    }

    /**
     * 获取系统配置信息。
     *
     * @param adminId 管理员ID，必须提供
     * @param password 管理员密码，必须提供
     * @return 返回包含系统配置的Result对象。如果验证失败或无权限，则返回错误信息；否则返回获取到的系统配置信息。
     */
    @RequestMapping(value = "/getConfig", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<SystemConfig> getConfig(
            @RequestParam Long adminId,
            @RequestParam String password) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        return Result.success("获取成功", systemConfigService.getConfig());
    }

    /**
     * 更新系统配置信息。
     *
     * @param adminId 管理员ID，必须提供
     * @param password 管理员密码，必须提供
     * @param config 系统配置信息，必须提供
     * @return 返回操作结果，成功则返回包含"更新成功"的消息；失败则根据情况返回错误代码和相应的错误消息
     */
    @PostMapping("/updateConfig")
    public Result<?> updateConfig(
             @RequestParam Long adminId,
             @RequestParam String password,
             @RequestBody SystemConfig config) {

        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }

        try {
            boolean success = systemConfigService.updateConfig(config);
            return success ? Result.success("更新成功") : Result.error(-5, "更新失败");
        } catch (Exception e) {
            return Result.error(-5, e.getMessage());
        }
    }


    /**
     * 查询错误日志列表。
     *
     * @param adminId 管理员ID，用于验证管理员身份。
     * @param password 管理员密码，用于验证管理员身份。
     * @return 返回一个Result对象，包含查询结果或错误信息。如果管理员验证失败或无
     * */
    @RequestMapping(value = "/listErrorLogs", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<?> listErrorLogs(@RequestParam Long adminId, @RequestParam String password) {
        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }
        // TODO: 调用 ErrorLogService.listErrors(...)
        return Result.error(-5, "此功能尚未实现");
    }

    /**
     * 获取特定错误日志的详情。
     *
     * @param adminId 管理员ID，用于验证管理员身份。
     * @param password 管理员密码，与adminId一起用于验证管理员身份。
     * @param errorId 错误日志的唯一标识符，用于定位具体的错误日志记录。
     * @return 返回一个Result对象，包含请求的结果。如果管理员验证失败或无权限访问，则返回相应的错误信息；当前实现中，此方法直接返回功能尚未实现的信息。
     */
    @GetMapping("/getErrorDetail")
    public Result<?> getErrorDetail(@RequestParam Long adminId, @RequestParam String password, @RequestParam Long errorId) {
        if (checkAdminPermission(adminId, password).getStatus() != 0) {
            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }
        // TODO: 调用 ErrorLogService.getErrorDetail(errorId)
        return Result.error(-5, "此功能尚未实现");
    }

    // --- 私有辅助方法 ---

    /**
     * 检查用户是否为管理员
     * @param user 用户实体
     * @return true 如果是管理员
     */
    private boolean isAdmin(User user) {
        // TODO: 这里应有更健壮的管理员判断逻辑，例如检查角色表或特定的字段。
        // 假设 ID为1的用户是超级管理员，或者 isAgent 也可作为一种权限标识。
        // 为确保安全，实际项目中应使用专门的 isAdmin 字段或角色ID。
        return user != null && user.getId() == 1; // 临时规则：ID为1的用户是超级管理员
    }

    /**
     * 校验管理员身份和权限
     * @param adminId 管理员ID
     * @param password 密码
     * @return CommonResultDTO，成功时 status=0
     */
    private CommonResultDTO<?> checkAdminPermission(Long adminId, String password) {
        User admin = userService.authenticate(adminId, password);
        if (admin == null || !isAdmin(admin)) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
        }
        return CommonResultDTO.success();
    }
}