package com.cdy.shortlink.project.mq.producer;

import com.cdy.shortlink.project.common.constant.RedisKeyConstant;
import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 延迟消费短链接统计发送者
 * <p>
 * 延迟队列在构造时创建一次并复用，避免每次发送都注册新的定时转移任务。
 */
@Component
public class DelayShortLinkStatsProducer {

    private final RDelayedQueue<ShortLinkStatsRecordDTO> delayedQueue;

    public DelayShortLinkStatsProducer(RedissonClient redissonClient) {
        RBlockingDeque<ShortLinkStatsRecordDTO> blockingDeque =
                redissonClient.getBlockingDeque(RedisKeyConstant.DELAY_QUEUE_STATS_KEY);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingDeque);
    }

    /**
     * 发送延迟消费短链接统计
     *
     * @param statsRecord 短链接统计实体参数
     */
    public void send(ShortLinkStatsRecordDTO statsRecord) {
        // 延迟 5 秒，给「修改分组」的写锁留出释放时间
        delayedQueue.offer(statsRecord, 5, TimeUnit.SECONDS);
    }
}
