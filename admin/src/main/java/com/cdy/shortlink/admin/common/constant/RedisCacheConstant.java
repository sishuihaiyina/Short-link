package com.cdy.shortlink.admin.common.constant;

/**
 * Redis缓存常量
 */
public class RedisCacheConstant {

    /**
     * 用户注册分布式锁
     */
    public static final String LOCK_USER_REGISTER_KEY = "short-link:lock_user_register:";

    /**
     * 分组创建分布式锁
     */
    public static final String LOCK_GROUP_CREATE_KEY = "short-link:lock_group_create: %s";

    /**
     * 用户登录 Token 缓存前缀（需与网关 TokenValidate 过滤器保持一致）
     */
    public static final String LOGIN_USER_KEY = "short-link:login:";
}
