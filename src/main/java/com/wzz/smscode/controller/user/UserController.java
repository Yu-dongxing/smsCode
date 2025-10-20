package com.wzz.smscode.controller.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzz.smscode.common.CommonResultDTO;
import com.wzz.smscode.common.Constants;
import com.wzz.smscode.common.Result;
import com.wzz.smscode.dto.*;
import com.wzz.smscode.dto.EntityDTO.UserDTO;
import com.wzz.smscode.dto.LoginDTO.UserLoginDto;
import com.wzz.smscode.dto.ResultDTO.UserResultDTO;
import com.wzz.smscode.dto.update.UpdateUserDto;
import com.wzz.smscode.dto.update.UserUpdatePasswardDTO;
import com.wzz.smscode.entity.NumberRecord;
import com.wzz.smscode.entity.SystemConfig;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.service.NumberRecordService;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.service.SystemConfigService;
import com.wzz.smscode.service.UserService;
import com.wzz.smscode.service.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 用户接口控制器
 * 该接口 GET/POST 自适应
 * <p>
 * 面向普通用户，提供取号、取码、查询信息等核心功能。
 * 所有接口均需要提供 userId 和 password 进行身份验证。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private NumberRecordService numberRecordService;

    @Autowired
    private UserService userService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private SystemConfigService systemConfigService;
    /**
     * 获取手机号码
     *
     * @param userName    用户姓名
     * @param password  用户密码
     * @param projectId 项目ID
     * @param lineId    线路ID
     * @return CommonResultDTO，成功时 data 为手机号字符串
     */
    @RequestMapping(value = "/getNumber", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonResultDTO<String> getNumber(
            @RequestParam String userName,
            @RequestParam String password,
            @RequestParam String projectId,
             @RequestParam Integer lineId) {
        return numberRecordService.getNumber(userName, password, projectId, lineId);
    }

    /**
     * 更新密码
     */
    @PostMapping("/update/passward")
    public Result<?> updateByUserIdToPassWard(@RequestBody UserUpdatePasswardDTO updateUserDto ){
        try{
            Boolean is_ok = userService.updatePassWardByUserName(updateUserDto);
            if (is_ok){
                return Result.success("更新成功");
            }
            return Result.error("更新失败！");
        }catch (BusinessException e){
            return Result.error(e.getMessage());
        }

    }

    /**
     * 获取公告接口
     */
    @GetMapping("/notice")
    public Result<?> getUserNotice(){
        SystemConfig config = systemConfigService.getConfig();
        return Result.success(config.getSystemNotice());
    }

    /**
     * 获取验证码
     *
     * @param userName      用户名称
     * @param password    用户密码
     * @param phoneNumber 获取到的手机号码
     * @return CommonResultDTO，成功时 data 为验证码字符串
     */
    @RequestMapping(value = "/getCode", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonResultDTO<String> getCode(
             @RequestParam String userName,
            @RequestParam String password,
            @RequestParam String phoneNumber) {

        return numberRecordService.getCode(userName, password, phoneNumber);
    }

    /**
     * 查询账户余额
     *
     * @param userName   用户名称
     * @param password 用户密码
     * @return CommonResultDTO，成功时 data 为余额
     */
    @RequestMapping(value = "/getBalance", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonResultDTO<BigDecimal> getBalance(
            @RequestParam String userName,
            @RequestParam String password) {


        return userService.getBalance(userName, password);
    }

    /**
     * 用户登录并获取详细信息
     *
     * @param userName   用户ID
     * @param password 用户密码
     * @return CommonResultDTO，成功时 data 为 UserDTO
     */
    @RequestMapping(value = "/info", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonResultDTO<UserDTO> getUserInfo(
            @RequestParam String userName,
            @RequestParam String password) {
        User user = userService.authenticateUserByUserName(userName, password);
        if (user == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户ID或密码错误");
        }
        // 调用在 UserServiceImpl 中定义的 DTO 转换方法
        UserDTO userDTO = ((UserServiceImpl) userService).convertToDTO(user);
        return CommonResultDTO.success("登录成功", userDTO);
    }

    /**
     * 查询号码获取记录
     *
     * @param userName    用户名称
     * @param password  用户密码
     * @param status    状态过滤 (可选)
     * @param startTime 开始时间 (可选, 格式: yyyy-MM-dd HH:mm:ss)
     * @param endTime   结束时间 (可选, 格式: yyyy-MM-dd HH:mm:ss)
     * @param page      当前页码 (默认 1)
     * @param size      每页数量 (默认 10)
     * @return CommonResultDTO，data 为分页后的 NumberDTO 列表
     */
    @RequestMapping(value = "/listNumbers", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonResultDTO<IPage<NumberDTO>> listNumbers(
            @RequestParam String userName,
            @RequestParam String password,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
             @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        IPage<NumberRecord> pageRequest = new Page<>(page, size);
        IPage<NumberDTO> resultPage = numberRecordService.listUserNumbersByUSerName(userName, password, status, startTime, endTime, pageRequest);

        if (resultPage == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户名或密码错误");
        }
        return CommonResultDTO.success("查询成功", resultPage);
    }

    /**
     * 查询项目列表
     */

    /**
     * 查询某项目的可用线路列表
     *
     * @param userName    用户ID
     * @param password  用户密码
     * @param projectId 项目ID
     * @return CommonResultDTO，data 为可用的线路ID集合
     */
    @RequestMapping(value = "/listProjectLines", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonResultDTO<?> listProjectLines(
            @RequestParam String userName,
            @RequestParam String password,
            @RequestParam String projectId) {
        // 权限校验
        User user = userService.authenticateUserByUserName(userName, password);
        if (user == null) {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED, "用户ID或密码错误");
        }
        List<String> lines = projectService.listLines(projectId);
        return CommonResultDTO.success("查询成功", lines);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public CommonResultDTO<?> userLogin(@RequestBody UserLoginDto userLoginDto){
        Boolean is_true = userService.login(userLoginDto);
        if (is_true){
            return CommonResultDTO.success("用户登录成功");
        }
        else {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED,"用户认证失败！");
        }
    }
    /**
     * 用户注册（前端不需要使用）
     */
    @PostMapping("/regist")
    public CommonResultDTO<?> userRegist(@RequestBody UserResultDTO userDTO){
        Boolean is_true = userService.regist(userDTO);
        if (is_true){
            return CommonResultDTO.success("用户注册成功");
        }else {
            return CommonResultDTO.error(Constants.ERROR_AUTH_FAILED,"用户注册失败！");
        }
    }
}