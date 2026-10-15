package com.cdy.shortlink.project.common.constant;

/**
 * Redis key 常量类
 */
public class RedisKeyConstant {

    /**
     * 跳转短链接 key 前缀
     */
    public static final String GOTO_SHORT_LINK_KEY = "short-link_goto_%s";

    /**
     * 分布式锁 key 前缀
     */
    public static final String LOCK_GOTO_SHORT_LINK_KEY = "short-link_lock_goto_%s";

    /**
     * 跳转短链接空值 key 前缀
     */
    public static final String GOTO_IS_NULL_SHORT_LINK_KEY = "short-link_is-null_goto_%s";
}
