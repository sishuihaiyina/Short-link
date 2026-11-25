package com.cdy.shortlink.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.cdy.shortlink.admin.common.biz.user.UserContext;
import com.cdy.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.cdy.shortlink.admin.common.convention.exception.ClientException;
import com.cdy.shortlink.admin.common.convention.result.Result;
import com.cdy.shortlink.admin.common.convention.result.Results;
import com.cdy.shortlink.admin.dto.req.UserChangePasswordReqDTO;
import com.cdy.shortlink.admin.dto.req.UserLoginReqDTO;
import com.cdy.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.cdy.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.cdy.shortlink.admin.dto.resp.UserActualRespDTO;
import com.cdy.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.cdy.shortlink.admin.dto.resp.UserRespDTO;
import com.cdy.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户接口控制层
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 根据用户名查询用户信息
     */
    @GetMapping("/api/short-link/admin/v1/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username) {
        checkSelf(username);
        return Results.success(userService.getUserByUsername(username));
    }

    /**
     * 根据用户名查询用户无敏信息
     */
    @GetMapping("/api/short-link/admin/v1/actual/user/{username}")
    public Result<UserActualRespDTO> getActualUserByUsername(@PathVariable("username") String username) {
        checkSelf(username);
        return Results.success(BeanUtil.toBean(userService.getUserByUsername(username), UserActualRespDTO.class));
    }

    /**
     * 查询用户名是否存在
     */
    @GetMapping("/api/short-link/admin/v1/user/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") String username) {
        return Results.success(userService.hasUsername(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("/api/short-link/admin/v1/user")
    public Result<Void> register(@RequestBody UserRegisterReqDTO userRegisterReqDTO) {
        userService.register(userRegisterReqDTO);
        return Results.success();
    }

    /**
     * 修改用户信息
     */
    @PutMapping("/api/short-link/admin/v1/user")
    public Result<Void> update(@RequestBody UserUpdateReqDTO userUpdateReqDTO) {
        String currentUsername = UserContext.getUsername();
        if (StrUtil.isBlank(currentUsername)) {
            throw new ClientException(BaseErrorCode.NO_PERMISSION_ERROR);
        }
        // 忽略请求体中的 username，只能修改当前登录用户自己的信息
        userUpdateReqDTO.setUsername(currentUsername);
        userService.update(userUpdateReqDTO);
        return Results.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/api/short-link/admin/v1/user/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO userLoginReqDTO) {
        return Results.success(userService.login(userLoginReqDTO));
    }

    /**
     * 检查用户登录状态
     */
    @GetMapping("/api/short-link/admin/v1/user/check-login")
    public Result<Boolean> checkLogin(@RequestParam("username") String username, @RequestParam("token") String token) {
        return Results.success(userService.checkLogin(username, token));
    }

    /**
     * 退出登录
     */
    @DeleteMapping("/api/short-link/admin/v1/user/logout")
    public Result<Void> logout(@RequestParam("username") String username, @RequestParam("token") String token) {
        checkSelf(username);
        userService.logout(username, token);
        return Results.success();
    }

    /**
     * 修改当前登录用户的密码，改完所有设备需要重新登录
     */
    @PutMapping("/api/short-link/admin/v1/user/password")
    public Result<Void> changePassword(@RequestBody UserChangePasswordReqDTO userChangePasswordReqDTO) {
        userService.changePassword(userChangePasswordReqDTO.getOldPassword(), userChangePasswordReqDTO.getNewPassword());
        return Results.success();
    }

    /**
     * 退出所有设备（让当前用户的所有 token 失效）
     */
    @DeleteMapping("/api/short-link/admin/v1/user/sessions")
    public Result<Void> kickAllSessions() {
        userService.kickAllSessions();
        return Results.success();
    }

    /**
     * 校验目标用户名是否为当前登录用户，防止横向越权访问他人信息
     */
    private void checkSelf(String username) {
        String currentUsername = UserContext.getUsername();
        if (StrUtil.isBlank(currentUsername) || !currentUsername.equals(username)) {
            throw new ClientException(BaseErrorCode.NO_PERMISSION_ERROR);
        }
    }
}
