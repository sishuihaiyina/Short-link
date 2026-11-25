package com.cdy.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
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
     * 登录态有效期（分钟），网关校验通过后同样会续期
     */
    @Value("${short-link.login.token-ttl-minutes:30}")
    private long loginTokenTtlMinutes;

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
        /** TODO 优化登录逻辑1
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

        String loginKey = RedisCacheConstant.LOGIN_USER_KEY + userLoginReqDTO.getUsername();
        // 允许用户在不同端登录，如手机和电脑
        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(loginKey);
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            // 已经有有效登录态：直接复用，同时把过期时间续上
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElseThrow(() -> new ClientException("用户登录错误"));
            renewLoginToken(loginKey);
            return new UserLoginRespDTO(token);
        }

        String uuid = UUID.randomUUID().toString();
        // 只存网关/前端需要的字段，不要把带密码的 UserDO 整个丢进 Redis
        JSONObject loginUser = new JSONObject();
        loginUser.put("id", userDO.getId());
        loginUser.put("username", userDO.getUsername());
        // 网关会 URLEncoder.encode(realName)，null 会 NPE，这里兜底成用户名
        loginUser.put("realName", StrUtil.blankToDefault(userDO.getRealName(), userDO.getUsername()));
        stringRedisTemplate.opsForHash().put(loginKey, uuid, loginUser.toJSONString());
        renewLoginToken(loginKey);

        return new UserLoginRespDTO(uuid);
    }

    /**
     * 检查用户登录状态
     * @param token 用户token
     * @return 是否登录
     */
    @Override
    public Boolean checkLogin(String username, String token) {
        String loginKey = RedisCacheConstant.LOGIN_USER_KEY + username;
        if (stringRedisTemplate.opsForHash().get(loginKey, token) == null) {
            return false;
        }
        // 滑动续期：只要还在用，登录态就一直有效
        renewLoginToken(loginKey);
        return true;
    }

    /**
     * 退出登录（只踢掉当前这个 token，其它端不受影响）
     * @param username 用户名
     * @param token 用户token
     */
    @Override
    public void logout(String username, String token) {
        String loginKey = RedisCacheConstant.LOGIN_USER_KEY + username;
        if (stringRedisTemplate.opsForHash().get(loginKey, token) == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NOT_LOGIN);
        }
        stringRedisTemplate.opsForHash().delete(loginKey, token);
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(loginKey))) {
            stringRedisTemplate.delete(loginKey);
        }
    }

    /**
     * 续期登录态
     */
    private void renewLoginToken(String loginKey) {
        stringRedisTemplate.expire(loginKey, loginTokenTtlMinutes, TimeUnit.MINUTES);
    }
}
