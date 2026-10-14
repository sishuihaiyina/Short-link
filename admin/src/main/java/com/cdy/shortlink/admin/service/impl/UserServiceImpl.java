package com.cdy.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cdy.shortlink.admin.common.constant.RedisCacheConstant;
import com.cdy.shortlink.admin.common.convention.exception.ClientException;
import com.cdy.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.cdy.shortlink.admin.dao.entity.UserDO;
import com.cdy.shortlink.admin.dao.mapper.UserMapper;
import com.cdy.shortlink.admin.dto.req.UserLoginReqDTO;
import com.cdy.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.cdy.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.cdy.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.cdy.shortlink.admin.dto.resp.UserRespDTO;
import com.cdy.shortlink.admin.service.GroupService;
import com.cdy.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户接口实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final GroupService groupService;

    /**
     * 根据用户名查询用户信息
     * @param username 用户名
     * @return 用户返回实体
     */
    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    /**
     * 查询用户名是否存在
     * @param username 用户名
     * @return 是否存在
     */
    @Override
    public Boolean hasUsername(String username) {
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    /**
     * 注册用户
     * @param userRegisterReqDTO 用户注册请求实体
     */
    @Override
    public void register(UserRegisterReqDTO userRegisterReqDTO) {
        if (!hasUsername(userRegisterReqDTO.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }
        // 防止恶意请求毫秒级触发大量请求去注册一个未注册的用户名
        RLock lock = redissonClient.getLock(RedisCacheConstant.LOCK_USER_REGISTER_KEY + userRegisterReqDTO.getUsername());
        try {
            if (lock.tryLock()) {
                try {
                    int inserted = baseMapper.insert(BeanUtil.toBean(userRegisterReqDTO, UserDO.class));
                    if (inserted < 1) {
                        throw new ClientException(UserErrorCodeEnum.USER_SAVE_ERROR);
                    }
                } catch (DuplicateKeyException ex) {
                    throw new ClientException(UserErrorCodeEnum.USER_EXIST);
                }
                userRegisterCachePenetrationBloomFilter.add(userRegisterReqDTO.getUsername());
                // 注册成功后，创建默认分组
                groupService.saveGroup(userRegisterReqDTO.getUsername(),"默认分组");
                return;
            }
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 修改用户信息
     * @param userUpdateReqDTO 用户更新请求实体
     */
    @Override
    public void update(UserUpdateReqDTO userUpdateReqDTO) {
        // TODO  验证当前用户名是否登录
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, userUpdateReqDTO.getUsername());
        baseMapper.update(BeanUtil.toBean(userUpdateReqDTO, UserDO.class), updateWrapper);
    }

    /**
     * 用户登录
     * @param userLoginReqDTO 用户登录请求实体
     * @return 用户登录响应实体
     */
    @Override
    public UserLoginRespDTO login(UserLoginReqDTO userLoginReqDTO) {
        /**
         * 问题：这里直接用明文密码去匹配数据库字段。如果数据库中密码是加密存储的（如 BCrypt），
         * 这种写法根本查不到用户。正确做法应该是：先根据用户名查出用户，再用 BCrypt.matches 校验密码。
         * 直接等值查询会失去加盐哈希的意义，且存在 SQL 注入风险（虽然 MyBatis-Plus 预编译能防注入，但密码明文比对本身不安全
         **/
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, userLoginReqDTO.getUsername())
                .eq(UserDO::getPassword, userLoginReqDTO.getPassword())
                .eq(UserDO::getDelFlag, 0);

        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }

        /**
         * 问题：这种设计只允许单端登录。如果用户想在手机和电脑同时登录，会被拒绝。
         * 而且，如果用户异常退出（如清除了浏览器缓存但未调用登出），Redis 中的 key 仍在 30 分钟内有效，用户无法重新登录，体验差。
         * 更合理的做法是允许同一用户有多个 token，或者提供“挤下线”功能
         */
        Boolean hasLogin = stringRedisTemplate.hasKey("login_" + userLoginReqDTO.getUsername());
        if (hasLogin != null && hasLogin) {
            throw new ClientException(UserErrorCodeEnum.USER_LOGIN);
        }

        String uuid = UUID.randomUUID().toString();

        /**
         * 问题：
         * 1.存储了完整 UserDO：UserDO 可能包含密码、盐值等敏感字段。一旦 Redis 被攻破，密码会泄露。
         * 应该只存必要的用户信息（如 id、username、nickname），或者单独存一个脱敏后的 DTO。
         *
         * 2.过期时间固定 30 分钟：没有实现滑动续期。用户如果在 30 分钟内一直操作，到了 30 分钟仍然会过期，需要重新登录。
         * 更好的做法是在 checkLogin 中，如果 token 有效，就刷新过期时间。
         *
         * 3.并发问题：两个请求同时登录，都查到 hasLogin 为 false，然后都执行 put，后写入的会覆盖先写入的，导致先登录的 token 失效。
         * 可以使用 setIfAbsent 或分布式锁来保证原子性
         **/
        stringRedisTemplate.opsForHash().put("login_" + userLoginReqDTO.getUsername(), uuid, JSON.toJSONString(userDO));
        stringRedisTemplate.expire("login_" + userLoginReqDTO.getUsername(), 30L, TimeUnit.DAYS);

        return new UserLoginRespDTO(uuid);
    }

    /**
     * 检查用户登录状态
     * @param token 用户token
     * @return 是否登录
     */
    @Override
    public Boolean checkLogin(String username, String token) {
        /**
         * 注意：这里没有刷新过期时间，所以登录态是固定的 30 分钟。如果希望滑动续期，应该在这里判断有效后，重新设置 key 的过期时间
         */
        return stringRedisTemplate.opsForHash().get("login_" + username, token) != null;
    }

    /**
     * 退出登录
     * @param username 用户名
     * @param token 用户token
     */
    @Override
    public void logout(String username, String token) {
        if (checkLogin(username, token)) {
            stringRedisTemplate.delete("login_" + username);
            return;
        }
        throw new ClientException(UserErrorCodeEnum.USER_NOT_LOGIN);
    }
}
