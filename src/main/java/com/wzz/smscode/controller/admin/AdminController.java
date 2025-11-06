package com.wzz.smscode.controller.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzz.smscode.common.CommonResultDTO;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.common.Result;
import com.wzz.smscode.dto.*;
import com.wzz.smscode.dto.CreatDTO.UserCreateDTO;
import com.wzz.smscode.dto.EntityDTO.LedgerDTO;
import com.wzz.smscode.dto.EntityDTO.UserDTO;
import com.wzz.smscode.dto.LoginDTO.AdminLoginDTO;
import com.wzz.smscode.dto.number.NumberDTO;
import com.wzz.smscode.dto.project.ProjectPriceInfoDTO;
import com.wzz.smscode.dto.project.SubUserProjectPriceDTO;
import com.wzz.smscode.dto.update.UpdateUserDto;
import com.wzz.smscode.entity.*;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.enums.RequestType;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.service.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    private final ErrorLogService errorLogService;

    public AdminController(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }
    // @Autowired private ErrorLogService errorLogService; // 假设存在错误日志服务

    /**
     * 管理员用户登录
     */
    @PostMapping("/login")
    public Result<?> adminLogin(@RequestBody AdminLoginDTO adminLoginDTO){
        if (username.equals(adminLoginDTO.getUsername()) && password.equals(adminLoginDTO.getPassword())){
            Map<String,String> loginData = new HashMap<>();
            StpUtil.login("0"); // 登录ID为 "0"，代表管理员
            loginData.put("token",StpUtil.getTokenValue());
            return Result.success("管理员登录成功，返回token", StpUtil.getTokenValue());
        }else {
            return Result.error("用户名密码不正确！");
        }
    }


    /**
     * 列出用户列表。
     * 根据提供的参数，可以查询所有用户或特定用户的直接下级。
     * @param parentId 可选参数，指定要查询的用户的父ID；如果提供，则返回该用户的直接下级
     * @param page 页码，默认为1
     * @param size 每页显示的记录数，默认为10
     * @return 返回一个Result对象，包含操作状态、消息以及数据（用户列表）
     */
    @RequestMapping(value = "/listUsers", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<?> listUsers(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String userName,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        IPage<User> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (parentId != null) {
            queryWrapper.eq(User::getParentId, parentId);
        }
        if (StringUtils.hasText(userName)){
            queryWrapper.like(User::getUserName, userName);
        }
        queryWrapper.orderByDesc(User::getCreateTime);
        IPage<User> userPage = userService.page(pageRequest,queryWrapper);
        return Result.success("查询成功", userPage);
    }

    /**
     * 创建新用户或代理账户
     * @param userCreateDTO
     * @return
     */
    @PostMapping("/createUser")
    public Result<?> createUser(@RequestBody UserCreateDTO userCreateDTO) {
        try {
            // + 管理员操作，operatorId 直接传入 0L
            boolean success = userService.createUser(userCreateDTO, 0L);
            return success ? Result.success("创建成功") : Result.error(-5, "创建失败");
        }catch (IllegalArgumentException | SecurityException | IllegalStateException  e) {
            // 记录业务异常信息，但返回通用错误提示
            log.warn("创建用户业务校验失败: {}", e.getMessage());
            return Result.error("创建失败，输入信息有误或权限不足");
        } catch (BusinessException e) {
            log.error("创建用户时发生系统业务内部错误", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, e.getMessage());
        } catch (Exception e) {
            // 记录未预料到的系统异常
            log.error("创建用户时发生系统内部错误", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "创建用户时发生系统内部错误，请联系管理员");
        }
    }

    /**
     * 编辑用户信息
     * @param userDTO
     * @return
     */
    @PostMapping("/updateUser")
    public Result<?> updateUser(@RequestBody User userDTO) {
        try {
            boolean success = userService.updateUserByEn(userDTO, 0L);
            return success ? Result.success("修改成功") : Result.error(-5, "信息无变化或修改失败");
        } catch (Exception e) {
            return Result.error(-5, e.getMessage());
        }
    }

    /**
     * 根据用户id更新密码
     */
    @PostMapping("/update/passward")
    public Result<?> updateByUserIdToPassWard(@RequestBody UpdateUserDto updateUserDto ){
        try{
            Boolean is_ok = userService.updatePassWardByUserId(updateUserDto);
            if (is_ok){
                return Result.success("更新成功");
            }
            return Result.error("更新失败！");
        }catch (BusinessException e){
            return Result.error(e.getMessage());
        }

    }

    /**
     * 为用户充值（系统资金）
     * @param targetUserId 目标用户ID
     * @param amount 充值金额
     * @return
     */
    @PostMapping("/rechargeUser")
    public CommonResultDTO<?> rechargeUser(
            //- @RequestParam Long adminId,
            //- @RequestParam String password,
            @RequestParam Long targetUserId,
            @RequestParam BigDecimal amount) {
        // + 管理员操作，operatorId 直接传入 0L
        return userService.rechargeUser(targetUserId, amount, 0L);
    }

    /**
     * 扣减用户余额（回收系统）
     * @param targetUserId 目标用户ID
     * @param amount 扣款金额
     * @return
     */
    @PostMapping("/deductUser")
    public CommonResultDTO<?> deductUser(
            //- @RequestParam Long adminId,
            //- @RequestParam String password,
            @RequestParam Long targetUserId,
            @RequestParam BigDecimal amount) {
        // + 管理员操作，operatorId 直接传入 0L
        return userService.deductUser(targetUserId, amount, 0L);
    }

    /**
     * 删除/禁用用户账户
     * @param targetUserId 目标用户ID
     * @return
     */
    @PostMapping("/deleteUser")
    public Result<?> deleteUser(
            //- @RequestParam Long adminId,
            //- @RequestParam String password,
            @RequestParam Long targetUserId) {
        // 采用软删除，将用户状态设置为-1（禁用）
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(targetUserId);
        userDTO.setStatus(-1); // -1 代表禁用
        try {
            // + 管理员操作，operatorId 直接传入 0L
//            boolean success = userService.updateUser(userDTO, 0L);
            boolean success = userService.delectByuserId(userDTO.getUserId());

            return success ? Result.success("用户删除成功") : Result.error(-5, "删除失败");
        } catch (BusinessException e) {
            return Result.error(-5, e.getMessage());
        }
    }

    // --- 账本与记录查询 ---




    /**
     * 查看指定用户的账本明细
     * @param targetUserId 目标用户 ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页数量
     * @return
     */
    @RequestMapping(value = "/viewUserLedger", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<IPage<LedgerDTO>> viewUserLedger(
            //- @RequestParam Long adminId,
            //- @RequestParam String password,
            @RequestParam Long targetUserId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        Page pageRequest = new Page<>(page, size);
        // + 管理员可无密码查询任意用户，传入 adminId=0L, password=null
        IPage<LedgerDTO> resultPage = userLedgerService.listAllLedger(0L, null, null,targetUserId, startTime, endTime, pageRequest,null,null,null);
        return Result.success("查询成功", resultPage);
    }

    /**
     * 查看全局账本记录
     * @param username 用户名（模糊查询）
     * @param filterByUserId 用户ID
     * @param remark 备注（模糊查询）
     * @param fundType 资金类型（0-业务扣费, 1-后台操作）
     * @param ledgerType 账本类型（1-入账，0-出账）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页数量
     * @return
     */
    @RequestMapping(value = "/viewAllLedger", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<IPage<LedgerDTO>> viewAllLedger(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long filterByUserId,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Integer fundType, // 新增：资金类型
            @RequestParam(required = false) Integer ledgerType, // 新增：账本类型
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        Page<UserLedger> pageRequest = new Page<>(page, size);

        // 调用修改后的 service 方法，并传入新增的参数
        IPage<LedgerDTO> resultPage = userLedgerService.listAllLedger(
                0L, null, username, filterByUserId, startTime, endTime, pageRequest, remark, fundType, ledgerType
        );

        return Result.success("查询成功", resultPage);
    }

    /**
     * 查看某用户的号码记录
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
            //- @RequestParam Long adminId,
            //- @RequestParam String password,
            @RequestParam Long targetUserId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        IPage<NumberRecord> pageRequest = new Page<>(page, size);
        IPage<NumberDTO> resultPage = numberRecordService.listUserNumbers(targetUserId, null, status, startTime, endTime, pageRequest);
        return Result.success("查询成功", resultPage);
    }

    /**
     * 查询号码记录
     * @param status 状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页面
     * @param size 页小
     * @param userId 用户id
     * @param projectId 项目id
     * @param lineId 线路id
     * @param phoneNumber 手机号码
     * @param charged 扣费状态
     * @return
     */
    @RequestMapping(value = "/viewAllNumbers", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<IPage<NumberRecord>> viewAllNumbers(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long userId, //用户id
            @RequestParam(required = false) String projectId, //项目id
            @RequestParam(required = false) String lineId, //线路id
            @RequestParam(required = false) String phoneNumber, // 用于模糊查询
            @RequestParam(required = false) Integer charged, //扣费状态
            @RequestParam(required = false) String userName // 用于模糊查询
    ) {

        IPage<NumberRecord> pageRequest = new Page<>(page, size);
        // 调用服务层方法，并传入所有参数
        IPage<NumberRecord> resultPage = numberRecordService.listAllNumbers(
                status, startTime, endTime, userId, projectId, phoneNumber, charged, pageRequest,lineId,userName
        );

        return Result.success("查询成功", resultPage);
    }


    // --- 系统统计与配置 ---

    @Data
    private static class SystemStatsDTO {
        /**
         * 总共用户数
         */
        private Long totalUsers;
        /**
         * 总代理数
         */
        private Long totalAgents;
        /**
         * 总取号数
         */
        private Long totalNumbersReceived;
        /**
         * 总取码数
         */
        private Long totalCodesReceived;
        /**
         * 总体回码率
         */
        private String overallCodeRate;
        /**
         * 近24小时取号数
         */
        private Long last24hNumbers;
        /**
         * 近24小时取码数
         */

        private Long last24hCodes;

        /**
         * 近24小时回码率
         */
        private String last24hCodeRate;

        /**
         * 总余额
         */
        private String totalPrice;
    }

    /**
     * 获取系统的总体统计数据。
     *
     * @return 返回一个包含系统统计数据的Result对象。如果管理员验证失败或无权限，则返回错误信息；否则返回成功信息及统计数据。
     */
    @GetMapping("/statistics")
    public Result<?> getStatistics() {

//        if (checkAdminPermission(adminId, password).getStatus() != 0) {
//            return Result.error(Constants.ERROR_AUTH_FAILED, "管理员验证失败或无权限");
//        }

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
        // 创建 QueryWrapper 用于构建 SQL 查询
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 使用 SQL 的 SUM 函数计算 balance 列的总和，并设置别名为 totalBalance
        queryWrapper.select("sum(balance) as totalBalance");

        // 执行查询，返回一个 Map
        Map<String, Object> map = userService.getMap(queryWrapper);

        BigDecimal totalBalance = BigDecimal.ZERO;
        if (map != null && map.get("totalBalance") != null) {
            // 将结果转换为 BigDecimal
            totalBalance = new BigDecimal(map.get("totalBalance").toString());
        }
        stats.setTotalPrice(totalBalance.setScale(2, RoundingMode.HALF_UP).toString());

        return Result.success("统计数据获取成功", stats);
    }

    /**
     * 获取公告接口
     */
    @GetMapping("/get/user/notice")
    public Result<?> getUserNotice(){
        SystemConfig config = systemConfigService.getConfig();
        return Result.success(config.getSystemNotice());
    }

    /**
     * 获取系统配置信息。
     * @return 返回包含系统配置的Result对象。如果验证失败或无权限，则返回错误信息；否则返回获取到的系统配置信息。
     */
    @RequestMapping(value = "/getConfig", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<SystemConfig> getConfig() {
        return Result.success("获取成功", systemConfigService.getConfig());
    }

    /**
     * 更新系统配置信息。

     * @param config 系统配置信息，必须提供
     * @return 返回操作结果，成功则返回包含"更新成功"的消息；失败则根据情况返回错误代码和相应的错误消息
     */
    @PostMapping("/updateConfig")
    public Result<?> updateConfig(
             @RequestBody SystemConfig config) {
        try {
            boolean success = systemConfigService.updateConfig(config);
            return success ? Result.success("更新成功") : Result.error(-5, "更新失败");
        } catch (BusinessException e) {
            return Result.error(-5, e.getMessage());
        }
    }

    /**
     * 根据手机号获取验证码()
     */
    @PostMapping("/by-phone/get/code/{phone}")
    public Result<?> getCodeByPhone(@PathVariable String phone){
        NumberRecord numberRecord = numberRecordService.getRecordByPhone(phone);
        return Result.success(numberRecord);
    }





    /**
     * 查询错误日志列表。
     *
     * @return 返回一个Result对象，包含查询结果或错误信息。如果管理员验证失败或无
     * */
    @RequestMapping(value = "/listErrorLogs", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<?> listErrorLogs(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long size) {
        Page pageRequest = new Page<>(page, size);
        IPage<LedgerDTO> resultPage = errorLogService.page(pageRequest);

        return Result.success(resultPage);
    }

    /**
     * 获取特定错误日志的详情。
     *
     * @param errorId 错误日志的唯一标识符，用于定位具体的错误日志记录。
     * @return 返回一个Result对象，包含请求的结果。如果管理员验证失败或无权限访问，则返回相应的错误信息；当前实现中，此方法直接返回功能尚未实现的信息。
     */
    @GetMapping("/getErrorDetail")
    public Result<?> getErrorDetail( @RequestParam Long errorId) {
        return Result.success(errorLogService.getErrorDetail(errorId));
    }

    // --- 私有辅助方法 ---

    /**
     * 检查用户是否为管理员
     * @param user 用户实体
     * @return true 如果是管理员
     */
    private boolean isAdmin(User user) {
        return user != null && user.getId() == 1;
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

    /**
     * + DTO for daily statistics trend
     */
    @Data
    public static class DailyStatsDTO {
        /**
         * 日期
         */
        private String date;
        /**
         *
         */
        private Long numberCount;
        private Long codeCount;
    }

    /**
     * 返回每日号码/验证码数量趋势
     * @param days 查询最近 N 天的数据，默认为 30
     * @return 包含每日统计数据的列表
     */
    @GetMapping("/dailyStatsTrend")
    public Result<List<DailyStatsDTO>> getDailyStatsTrend(@RequestParam(defaultValue = "30") int days) {
        try {
            LocalDateTime startTime = LocalDateTime.now().minusDays(days);
            QueryWrapper<NumberRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.select(
                            "DATE(get_number_time) as date",
                            "count(id) as numberCount", // 统计总取号数
                            "count(code) as codeCount"   // 统计 code 字段非空的记录数，即成功获取验证码的数量
                    )
                    .ge("get_number_time", startTime)
                    .groupBy("date")
                    .orderByAsc("date");

            List<Map<String, Object>> resultMaps = numberRecordService.listMaps(queryWrapper);

            List<DailyStatsDTO> dailyStats = resultMaps.stream().map(map -> {
                DailyStatsDTO dto = new DailyStatsDTO();
                // 获取日期
                Object dateObj = map.get("date");
                dto.setDate(dateObj != null ? dateObj.toString() : "");

                // 获取取号总数 (数据库返回的可能是 Long 或 BigInteger, 用 Number 类型接收最稳妥)
                Object numberCountObj = map.getOrDefault("numberCount", 0);
                dto.setNumberCount(((Number) numberCountObj).longValue());

                // 获取成功验证码数
                Object codeCountObj = map.getOrDefault("codeCount", 0);
                dto.setCodeCount(((Number) codeCountObj).longValue());

                return dto;
            }).collect(Collectors.toList());

            return Result.success("查询成功", dailyStats);
        } catch (Exception e) {
            log.error("查询每日趋势数据失败", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "系统错误，查询趋势数据失败");
        }
    }

    /**
     * 获取项目认证类型枚举
     * @return
     */
    @GetMapping("/enum/auth-types")
    public Result<?> getAuthTypes() {
        List<Map<String, String>> aa = Arrays.stream(AuthType.values())
                .map(authType -> {
                    Map<String, String> map = new HashMap<>();
                    // key "value" 对应枚举的 value 字段
                    map.put("value", authType.getValue());
                    // key "description" 对应枚举的 description 字段
                    map.put("description", authType.getDescription());
                    return map;
                })
                .toList();
        return Result.success(aa);
    }

    /**
     * 获取项目请求类型枚举
     * @return
     */
    @GetMapping("/enum/request-types")
    public Result<?> getRequestTypes() {
        List<Map<String, String>> aa = Arrays.stream(RequestType.values())
                .map(authType -> {
                    Map<String, String> map = new HashMap<>();
                    // key "value" 对应枚举的 value 字段
                    map.put("value", authType.getCode());
                    // key "description" 对应枚举的 description 字段
                    map.put("description", authType.getDesc());
                    return map;
                })
                .toList();

        return Result.success(aa);
    }

    @Autowired
    private UserProjectLineService userProjectLineService;
    /**
     * 分页并按条件筛选获取用户及其项目价格配置
     *
     * @param pageNum   当前页码
     * @param pageSize  每页数量
     * @param userName  用户名 (可选, 模糊查询)
     * @param projectId 项目ID (可选, 精确查询)
     * @param lineId    线路ID (可选, 精确查询)
     * @return 返回一个包含分页和筛选结果的用户项目配置列表
     */
    @GetMapping("/user-project-prices")
    public Result<?> getAllUserProjectPrices(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long lineId
    ) {
        try {
            // 1. 创建 MyBatis-Plus 分页对象
            IPage<UserProjectLine> page = new Page<>(pageNum, pageSize);

            // 2. 构建动态查询条件
            QueryWrapper<UserProjectLine> queryWrapper = new QueryWrapper<>();

            // 2.1 处理用户名筛选
            if (StringUtils.hasText(userName)) {
                // 根据用户名模糊查询出对应的用户ID列表
                QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
                userQueryWrapper.lambda().like(User::getUserName, userName);
                List<User> matchedUsers = userService.list(userQueryWrapper);

                // 如果没有找到匹配的用户，直接返回空结果，避免查询 user_project_line 表
                if (CollectionUtils.isEmpty(matchedUsers)) {
                    return Result.success("查询成功", new Page<>(pageNum, pageSize, 0));
                }

                Set<Long> userIds = matchedUsers.stream().map(User::getId).collect(Collectors.toSet());
                queryWrapper.lambda().in(UserProjectLine::getUserId, userIds);
            }

            // 2.2 处理项目ID筛选
            if (projectId != null) {
                queryWrapper.lambda().eq(UserProjectLine::getProjectId, projectId);
            }

            // 2.3 处理线路ID筛选
            if (lineId != null) {
                queryWrapper.lambda().eq(UserProjectLine::getLineId, lineId);
            }

            // 可选：添加默认排序，让结果更稳定
            queryWrapper.lambda().orderByDesc(UserProjectLine::getCreateTime);

            // 3. 执行带条件的分页查询
            IPage<UserProjectLine> pagedResult = userProjectLineService.page(page, queryWrapper);
            List<UserProjectLine> currentPageLines = pagedResult.getRecords();

            if (CollectionUtils.isEmpty(currentPageLines)) {
                return Result.success("查询成功", pagedResult); // 返回空的分页结果
            }

            // 4. 按 userId 进行分组
            Map<Long, List<UserProjectLine>> groupedByUserId = currentPageLines.stream()
                    .collect(Collectors.groupingBy(UserProjectLine::getUserId));

            // 5. 提取当前页所有相关的用户ID
            Set<Long> userIdsOnPage = groupedByUserId.keySet();

            // 6. 一次性查询所有相关用户信息
            List<User> users = userService.listByIds(userIdsOnPage);
            Map<Long, String> userIdToNameMap = users.stream()
                    .collect(Collectors.toMap(User::getId, User::getUserName));

            // 7. 组装成 SubUserProjectPriceDTO 列表
            List<SubUserProjectPriceDTO> resultList = new ArrayList<>();
            for (Map.Entry<Long, List<UserProjectLine>> entry : groupedByUserId.entrySet()) {
                Long userId = entry.getKey();
                List<UserProjectLine> userLines = entry.getValue();

                SubUserProjectPriceDTO subUserDto = new SubUserProjectPriceDTO();
                subUserDto.setUserId(userId);
                subUserDto.setUserName(userIdToNameMap.getOrDefault(userId, "未知用户"));

                List<ProjectPriceInfoDTO> priceInfoList = userLines.stream().map(line -> {
                    ProjectPriceInfoDTO priceDto = new ProjectPriceInfoDTO();
                    priceDto.setId(line.getId());
                    priceDto.setUserProjectLineTableId(line.getId());
                    priceDto.setProjectName(line.getProjectName());
                    priceDto.setProjectId(line.getProjectId());
                    priceDto.setLineId(line.getLineId());
                    priceDto.setPrice(line.getAgentPrice());
                    priceDto.setCostPrice(line.getCostPrice());
                    return priceDto;
                }).collect(Collectors.toList());

                subUserDto.setProjectPrices(priceInfoList);
                resultList.add(subUserDto);
            }

            // 8. 创建新的分页结果对象，返回处理后的数据
            IPage<SubUserProjectPriceDTO> finalPageResult = new Page<>(pageNum, pageSize, pagedResult.getTotal());
            finalPageResult.setRecords(resultList);

            return Result.success("查询成功", finalPageResult);
        } catch (Exception e) {
            log.error("分页并筛选用户项目配置失败", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "系统错误，查询配置失败");
        }
    }

    /**
     * 编辑或更新指定用户的项目价格配置
     * @param subUserProjectPriceDTO 包含用户ID和该用户全新的项目价格配置列表
     * @return 返回操作结果
     */
    @PostMapping("/user-project-prices/update")
    public Result<?> updateUserProjectPrices(@RequestBody SubUserProjectPriceDTO subUserProjectPriceDTO) {
        try {
            // 调用包含事务处理的 Service 方法
            boolean success = userProjectLineService.updateUserProjectLines(subUserProjectPriceDTO);
            return success ? Result.success("更新成功") : Result.error("更新失败或数据无变化");
        } catch (BusinessException e) {
            log.warn("编辑用户项目配置业务校验失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("编辑用户项目配置时发生系统内部错误", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "系统错误，更新失败");
        }
    }

    /**
     * 更新用户项目价格配置表
     */
    @PostMapping("/userProjectLine/update")
    public Result<?> updateByUserProjectLine(@RequestBody ProjectPriceInfoDTO projectPriceInfoDTO) {
        try {
            if (projectPriceInfoDTO.getUserProjectLineTableId() == null) {
                return Result.error("更新失败：缺少必要的配置ID");
            }
            UserProjectLine userProjectLineToUpdate = new UserProjectLine();
            BeanUtils.copyProperties(projectPriceInfoDTO, userProjectLineToUpdate);

            // 手动设置不一致或需要特殊处理的属性
            userProjectLineToUpdate.setId(projectPriceInfoDTO.getUserProjectLineTableId());
             userProjectLineToUpdate.setAgentPrice(projectPriceInfoDTO.getPrice());

            // 3. 调用 Service 层进行更新
            boolean isSuccess = userProjectLineService.updateUserProjectLinesById(userProjectLineToUpdate);

            // 4. 返回结果
            if (isSuccess) {
                return Result.success("更新成功");
            } else {
                return Result.error("更新失败");
            }
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新用户项目价格配置失败", e);
            return Result.error("系统内部错误，更新失败");
        }
    }

    /**
     * 根据用户id分页查询账本记录
     */
    @GetMapping("/get/user-id/leader/")
    public Result<?> getUserProjectLineById(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) Long userId) {
        try {
//            StpUtil.checkLogin();
//            if (StpUtil.getLoginIdAsLong() != 0){
//                return Result.error("权限不够！");
//            }
//            if (userId == null) {
//                return Result.error("传入用户id不正确！");
//            }
        }catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
        Page<UserLedger> page = new Page<>(pageNum, pageSize);
        return Result.success(userLedgerService.listUserLedgerByUSerId(userId,page));
    }

    /**
     * 数据报表
     * 管理员端
     * @param queryDTO 查询参数，包含分页和筛选条件
     */
    @PostMapping("/get/data")
    public Result<?> getData(@RequestBody StatisticsQueryDTO queryDTO){ // 使用 DTO 接收参数
        try{
            // 调用 service，传入分页和筛选条件，注意这里我们假设管理员ID仍然是 0L
            IPage<ProjectStatisticsDTO> reportPage = numberRecordService.getStatisticsReport(0L, queryDTO);
            return Result.success(reportPage);
        }catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 为指定用户新增项目价格配置
     * <p>
     * 此接口用于为现有用户添加一个或多个新的项目线路价格，而不会影响其已有的配置。
     * 如果尝试添加的配置已存在，则会自动跳过。
     *
     * @param request 包含用户ID和要添加的价格配置列表
     * @return 操作结果
     */
    @PostMapping("/user-project-prices/add")
    public Result<?> addUserProjectPrices(@RequestBody AddUserProjectPricesRequestDTO request) {
        try {
            // 调用统一的 Service 方法，管理员 operatorId 固定为 0L
            userService.addProjectPricesForUser(request, 0L);
            return Result.success("新增配置成功");
        } catch (BusinessException | SecurityException e) {
            log.warn("管理员新增用户项目配置业务校验失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("管理员新增用户项目配置时发生系统内部错误", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "系统错误，新增失败");
        }
    }

    @Autowired
    private PriceTemplateService priceTemplateService;


    /**
     * 创建价格模板
     * @param createDTO 模板信息
     * @return 操作结果
     */
    @PostMapping("/price-templates")
    public Result<?> createPriceTemplate(@RequestBody PriceTemplateCreateDTO createDTO) {
        try {
            boolean success = priceTemplateService.createTemplate(createDTO,0L);
            return success ? Result.success("创建成功") : Result.error("创建失败");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("创建价格模板失败", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "系统错误，创建失败");
        }
    }

    /**
     * 获取所有价格模板
     * @return 模板列表
     */
    @GetMapping("/price-templates")
    public Result<List<PriceTemplateResponseDTO>> getAllPriceTemplates() {
        try {
            List<PriceTemplateResponseDTO> templates = priceTemplateService.listTemplatesByCreator(-1L);
            return Result.success("查询成功", templates);
        } catch (Exception e) {
            log.error("查询价格模板失败", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "系统错误，查询失败");
        }
    }

    /**
     * 更新价格模板
     * @param templateId 模板ID
     * @param updateDTO 更新后的模板信息
     * @return 操作结果
     */
    @PostMapping("/price-templates/{templateId}")
    public Result<?> updatePriceTemplate(@PathVariable Long templateId, @RequestBody PriceTemplateCreateDTO updateDTO) {
        try {
            boolean success = priceTemplateService.updateTemplate(templateId, updateDTO,0L);
            return success ? Result.success("更新成功") : Result.error("更新失败");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新价格模板失败", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "系统错误，更新失败");
        }
    }

    /**
     * 删除价格模板
     * @param templateId 模板ID
     * @return 操作结果
     */
    @GetMapping("/price-templates/{templateId}")
    public Result<?> deletePriceTemplate(@PathVariable Long templateId) {
        try {
            boolean success = priceTemplateService.deleteTemplate(templateId,0L);
            return success ? Result.success("删除成功") : Result.error("删除失败");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除价格模板失败", e);
            return Result.error(Constants.ERROR_SYSTEM_ERROR, "系统错误，删除失败");
        }
    }
}