package com.cdy.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cdy.shortlink.admin.dao.entity.UserDO;
import com.cdy.shortlink.admin.dto.req.UserLoginReqDTO;
import com.cdy.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.cdy.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.cdy.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.cdy.shortlink.admin.dto.resp.UserRespDTO;

/**
 * 用户接口层
 */
public interface UserService extends IService<UserDO> {

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户返回实体
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 查询用户名是否存在
     * @param username 用户名
     * @return 是否存在
     */
    Boolean hasUsername(String username);

    /**
     * 注册用户
     * @param userRegisterReqDTO 用户注册请求实体
     */
    void register(UserRegisterReqDTO userRegisterReqDTO);

    /**
     * 修改用户信息
     * @param userUpdateReqDTO 用户更新请求实体
     */
    void update(UserUpdateReqDTO userUpdateReqDTO);

    /**
     * 用户登录
     * @param userLoginReqDTO 用户登录请求实体
     * @return 用户登录响应实体
     */
    UserLoginRespDTO login(UserLoginReqDTO userLoginReqDTO);

    /**
     * 检查用户登录状态
     * @param token 用户token
     * @return 是否登录
     */
    Boolean checkLogin(String username, String token);

    /**
     * 退出登录
     * @param username 用户名
     * @param token 用户token
     */
    void logout(String username, String token);

    /**
     * 修改当前登录用户的密码
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    void changePassword(String oldPassword, String newPassword);

    /**
     * 强制当前用户的所有登录态失效（退出所有设备）
     */
    void kickAllSessions();
}
