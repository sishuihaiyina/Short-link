package com.cdy.shortlink.project.mq.consumer;

import com.cdy.shortlink.project.mq.StatsStreamConsumerName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短链接统计消息 pending 恢复任务。
 * <p>
 * 消费失败的消息不会被 ACK，会一直留在 Redis Stream 的 PEL 中。
 * 本任务定时扫描超过 min-idle 仍未被 ACK 的消息：
 * <ul>
 *     <li>未超过最大重试次数：用 XCLAIM 转给当前实例重新消费；</li>
 *     <li>超过最大重试次数：转入死信队列（DLQ）并 ACK 原消息，避免无限重试。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsPendingRecoveryTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final StatsStreamConsumerName statsStreamConsumerName;
    private final ShortLinkStatsSaveConsumer shortLinkStatsSaveConsumer;

    @Value("${spring.data.redis.channel-topic.short-link-stats}")
    private String topic;

    @Value("${spring.data.redis.channel-topic.short-link-stats-group}")
    private String group;

    @Value("${spring.data.redis.channel-topic.short-link-stats-dlq:short_link:stats-stream:dlq}")
    private String dlqTopic;

    /**
     * pending 消息至少空闲多久才重新投递（毫秒）
     */
    @Value("${short-link.stats.recovery.min-idle-ms:60000}")
    private long minIdleMs;

    /**
     * 单个消息最大重试次数，超过后转入 DLQ
     */
    @Value("${short-link.stats.recovery.max-retries:5}")
    private long maxRetries;

    /**
     * 每轮最多扫描多少条 pending 消息
     */
    @Value("${short-link.stats.recovery.batch-size:100}")
    private long batchSize;

    @Scheduled(fixedDelayString = "${short-link.stats.recovery.fixed-delay-ms:30000}")
    public void recover() {
        try {
            PendingMessages pendingMessages = streamOps()
                    .pending(topic, group, Range.unbounded(), batchSize);
            for (PendingMessage pendingMessage : pendingMessages) {
                if (pendingMessage.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofMillis(minIdleMs)) < 0) {
                    continue;
                }
                if (pendingMessage.getTotalDeliveryCount() >= maxRetries) {
                    moveToDlq(pendingMessage);
                } else {
                    retry(pendingMessage);
                }
            }
        } catch (Exception ex) {
            log.error("短链接统计 pending 消息恢复任务执行失败，topic：{}，group：{}", topic, group, ex);
        }
    }

    /**
     * XCLAIM 到当前消费者后复用正常消费逻辑：成功会 ACK，失败继续留在 PEL
     */
    private void retry(PendingMessage pendingMessage) {
        try {
            List<MapRecord<String, String, String>> claimed = streamOps()
                    .claim(topic, group, statsStreamConsumerName.getName(),
                            Duration.ofMillis(minIdleMs), pendingMessage.getId());
            for (MapRecord<String, String, String> record : claimed) {
                shortLinkStatsSaveConsumer.onMessage(record);
            }
            if (!claimed.isEmpty()) {
                log.warn("短链接统计消息重试，id：{}，已投递次数：{}",
                        pendingMessage.getIdAsString(), pendingMessage.getTotalDeliveryCount());
            }
        } catch (Exception ex) {
            log.error("短链接统计消息重试失败，id：{}", pendingMessage.getIdAsString(), ex);
        }
    }

    /**
     * 重试次数超限：写入 DLQ → ACK → 删除原消息
     */
    private void moveToDlq(PendingMessage pendingMessage) {
        String id = pendingMessage.getIdAsString();
        try {
            Map<String, String> dlqMessage = new HashMap<>();
            dlqMessage.put("originalStream", topic);
            dlqMessage.put("originalId", id);
            dlqMessage.put("deliveryCount", String.valueOf(pendingMessage.getTotalDeliveryCount()));
            dlqMessage.put("failedAt", String.valueOf(System.currentTimeMillis()));
            Map<String, String> payload = loadPayload(id);
            if (payload != null) {
                payload.forEach((key, value) -> {
                    // XADD 不接受 null 值
                    if (value != null) {
                        dlqMessage.put(key, value);
                    }
                });
            }
            streamOps().add(dlqTopic, dlqMessage);
            streamOps().acknowledge(topic, group, id);
            streamOps().delete(topic, id);
            log.error("短链接统计消息重试次数超限，已转入死信队列，id：{}，投递次数：{}",
                    id, pendingMessage.getTotalDeliveryCount());
        } catch (Exception ex) {
            log.error("短链接统计消息转入死信队列失败，id：{}", id, ex);
        }
    }

    /**
     * 从 stream 中读取原始消息内容（消息可能已被删除，返回 null）
     */
    private Map<String, String> loadPayload(String id) {
        List<MapRecord<String, String, String>> records =
                streamOps().range(topic, Range.closed(id, id));
        return records.isEmpty() ? null : records.get(0).getValue();
    }

    /**
     * StringRedisTemplate#opsForStream 的泛型会被推断为 StreamOperations<String, Object, Object>，
     * 这里是 String-String 的 stream，做一次受控转换便于直接复用消费端类型。
     */
    @SuppressWarnings("unchecked")
    private StreamOperations<String, String, String> streamOps() {
        return (StreamOperations<String, String, String>) (StreamOperations<?, ?, ?>) stringRedisTemplate.opsForStream();
    }
}
