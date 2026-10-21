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

    /**
     * 短链接修改分组 ID 锁前缀 Key
     */
    public static final String LOCK_GID_UPDATE_KEY = "short-link_lock_update-gid_%s";

    /**
     * 短链接延迟队列消费统计 Key
     */
    public static final String DELAY_QUEUE_STATS_KEY = "short-link_delay-queue:stats";
}
