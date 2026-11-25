package com.cdy.shortlink.project.common.config;

import com.cdy.shortlink.project.mq.StatsStreamConsumerName;
import com.cdy.shortlink.project.mq.consumer.ShortLinkStatsSaveConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis Stream 消息队列配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfiguration {

    private final RedisConnectionFactory redisConnectionFactory;
    private final ShortLinkStatsSaveConsumer shortLinkStatsSaveConsumer;
    private final StatsStreamConsumerName statsStreamConsumerName;

    @Value("${spring.data.redis.channel-topic.short-link-stats}")
    private String topic;
    @Value("${spring.data.redis.channel-topic.short-link-stats-group}")
    private String group;

    @Bean
    public ExecutorService asyncStreamConsumer() {
        AtomicInteger index = new AtomicInteger();
        int processors = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(processors,
                processors + processors >> 1,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("stream_consumer_short-link_stats_" + index.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(ExecutorService asyncStreamConsumer) {
        // 消费者组必须先存在，否则 XREADGROUP 会报 NOGROUP，消费者一条消息都收不到
        initConsumerGroup();
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        // 一次最多获取多少条消息
                        .batchSize(10)
                        // 执行从 Stream 拉取到消息的任务流程
                        .executor(asyncStreamConsumer)
                        // 如果没有拉取到消息，需要阻塞的时间。不能大于 ${spring.data.redis.timeout}，否则会超时
                        .pollTimeout(Duration.ofSeconds(3))
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);
        // 手动 ACK：处理成功后由消费者显式 XACK；失败不 ACK，消息留在 PEL 由恢复任务重试
        streamMessageListenerContainer.receive(
                Consumer.from(group, statsStreamConsumerName.getName()),
                StreamOffset.create(topic, ReadOffset.lastConsumed()),
                shortLinkStatsSaveConsumer);
        return streamMessageListenerContainer;
    }

    /**
     * 幂等创建 Redis Stream 消费组。
     * <p>
     * 先检查 Stream 和消费组是否已存在，存在则直接跳过。
     * 不能每次启动都直接 XGROUP CREATE：组已存在时 Redis 返回 BUSYGROUP，
     * Redisson 会把它当成命令异常打 ERROR 日志并重试，导致启动日志出现一串报错。
     * <p>
     * 并发创建时仍可能返回 BUSYGROUP，捕获后忽略即可。
     */
    private void initConsumerGroup() {
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            if (Boolean.TRUE.equals(connection.keyCommands().exists(topicBytes))
                    && consumerGroupExists(connection, topicBytes)) {
                log.info("Redis Stream 消费组已存在，跳过创建，topic：{}，group：{}", topic, group);
                return;
            }
            // Stream 不存在时 MKSTREAM 会连带创建；组不存在时创建消费组
            connection.streamCommands().xGroupCreate(topicBytes, group, ReadOffset.latest(), true);
            log.info("Redis Stream 消费组创建成功，topic：{}，group：{}", topic, group);
        } catch (Exception ex) {
            if (isBusyGroup(ex)) {
                log.info("Redis Stream 消费组已存在（并发创建），topic：{}，group：{}", topic, group);
            } else {
                log.warn("Redis Stream 消费组创建失败，topic：{}，group：{}", topic, group, ex);
            }
        }
    }

    /**
     * 判断消费组是否已存在（调用前需确保 Stream 已存在）
     */
    private boolean consumerGroupExists(RedisConnection connection, byte[] topicBytes) {
        return connection.streamCommands().xInfoGroups(topicBytes).stream()
                .anyMatch(info -> group.equals(info.groupName()));
    }

    /**
     * 遍历异常链判断是否为 BUSYGROUP（Redisson 会包装成 CompletionException）
     */
    private boolean isBusyGroup(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }
}
