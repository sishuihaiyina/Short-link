package com.cdy.shortlink.project.mq.consumer;

import com.cdy.shortlink.project.common.constant.RedisKeyConstant;
import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.cdy.shortlink.project.service.ShortLinkStatsSaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 延迟记录短链接统计组件
 * <p>
 * 用于「修改分组期间拿不到读锁」的统计重试：延迟 5 秒后再尝试落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayShortLinkStatsConsumer implements InitializingBean {

    private final RedissonClient redissonClient;
    private final ShortLinkStatsSaveService shortLinkStatsSaveService;

    public void onMessage() {
        Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setName("delay_short-link_stats_consumer");
                            thread.setDaemon(Boolean.TRUE);
                            return thread;
                        })
                .execute(() -> {
                    RBlockingDeque<ShortLinkStatsRecordDTO> blockingDeque =
                            redissonClient.getBlockingDeque(RedisKeyConstant.DELAY_QUEUE_STATS_KEY);
                    // 保持延迟队列引用，Redisson 内部会把到期元素转移到 blockingDeque
                    redissonClient.getDelayedQueue(blockingDeque);
                    for (; ; ) {
                        try {
                            // 阻塞式拉取，最多等 500ms；不能用 poll() + parkUntil，否则会空转打满 CPU
                            ShortLinkStatsRecordDTO statsRecord = blockingDeque.poll(500, TimeUnit.MILLISECONDS);
                            if (statsRecord == null) {
                                continue;
                            }
                            // 如果锁仍被占用，save 方法会再次转入延迟队列
                            shortLinkStatsSaveService.saveShortLinkStats(null, null, statsRecord);
                        } catch (InterruptedException e) {
                            // 恢复中断标记并退出消费线程
                            Thread.currentThread().interrupt();
                            return;
                        } catch (Throwable ex) {
                            log.error("延迟短链接统计处理失败", ex);
                        }
                    }
                });
    }

    @Override
    public void afterPropertiesSet() {
        onMessage();
    }
}
