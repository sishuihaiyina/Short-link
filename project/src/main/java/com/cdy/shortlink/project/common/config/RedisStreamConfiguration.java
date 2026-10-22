package com.cdy.shortlink.project.common.config;

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
        streamMessageListenerContainer.receiveAutoAck(Consumer.from(group, "stats-consumer"),
                StreamOffset.create(topic, ReadOffset.lastConsumed()), shortLinkStatsSaveConsumer);
        return streamMessageListenerContainer;
    }

    /**
     * 幂等创建 Redis Stream 消费组。
     * <p>
     * 使用 XGROUP CREATE key group $ MKSTREAM：
     * - stream 不存在时会连带创建；
     * - 组已存在时 Redis 返回 BUSYGROUP，属于正常情况，捕获后忽略即可。
     * <p>
     * 这样换环境、Redis 被 flush 后都不需要再手动建组。
     */
    private void initConsumerGroup() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.streamCommands().xGroupCreate(
                    topic.getBytes(StandardCharsets.UTF_8),
                    group,
                    ReadOffset.latest(),
                    true);
            log.info("Redis Stream 消费组创建成功，topic：{}，group：{}", topic, group);
        } catch (Exception ex) {
            // BUSYGROUP 表示组已存在（含并发创建），属于正常情况；
            // 其它异常（如 Redis 连接失败）记录 warn，后续容器启动时会再次暴露
            if (ex.getMessage() != null && ex.getMessage().contains("BUSYGROUP")) {
                log.info("Redis Stream 消费组已存在，跳过创建，topic：{}，group：{}", topic, group);
            } else {
                log.warn("Redis Stream 消费组创建失败，topic：{}，group：{}", topic, group, ex);
            }
        }
    }
}
