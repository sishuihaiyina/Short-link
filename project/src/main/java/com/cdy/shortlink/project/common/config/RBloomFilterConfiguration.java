package com.cdy.shortlink.project.common.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 布隆过滤器配置
 */
@Configuration
public class RBloomFilterConfiguration {

    /**
     * 防止短链接注册查询数据库的布隆过滤器
     */
    @Bean
    public RBloomFilter<String> shortUriCreateRegisterCachePenetrationBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> cachePenetrationBloomFilter = redissonClient.getBloomFilter("xxx");
        cachePenetrationBloomFilter.tryInit(100000000, 0.001);    // 100000000个元素，0.001误判率
        return cachePenetrationBloomFilter;
    }
}
