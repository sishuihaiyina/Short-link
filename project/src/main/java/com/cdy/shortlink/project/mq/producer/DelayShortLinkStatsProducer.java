package com.cdy.shortlink.project.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 延迟投递短链接统计消息
 * <p>
 * 用于「修改分组期间拿不到读锁」的场景：延迟 5 秒后重新投递到同一个 topic，
 * 由同一个消费者再处理一次（届时锁通常已经释放）。
 * <p>
 * 原来是 Redisson 的 {@code RDelayedQueue}，现在换成 RocketMQ 延迟消息，
 * 延迟等级由 {@code short-link.mq.stats.delay-level} 指定（2 = 5 秒）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayShortLinkStatsProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${short-link.mq.stats.topic}")
    private String topic;

    @Value("${short-link.mq.stats.delay-level:2}")
    private int delayLevel;

    /**
     * 发送延迟消费短链接统计
     *
     * @param statsRecord 短链接统计实体参数
     */
    public void send(ShortLinkStatsRecordDTO statsRecord) {
        if (statsRecord == null) {
            return;
        }
        Map<String, String> producerMap = new HashMap<>(4);
        producerMap.put("fullShortUrl", StrUtil.nullToEmpty(statsRecord.getFullShortUrl()));
        // 延迟重投时拿不到 gid，消费端会去 t_link_goto 反查
        producerMap.put("gid", "");
        producerMap.put("statsRecord", JSON.toJSONString(statsRecord));
        Message<String> message = MessageBuilder.withPayload(JSON.toJSONString(producerMap))
                .setHeader(RocketMQHeaders.KEYS, StrUtil.nullToEmpty(statsRecord.getFullShortUrl()))
                .build();
        try {
            rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("短链接统计消息延迟投递成功，fullShortUrl：{}，msgId：{}",
                            statsRecord.getFullShortUrl(), sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("短链接统计消息延迟投递失败，fullShortUrl：{}",
                            statsRecord.getFullShortUrl(), throwable);
                }
            }, 3000, delayLevel);
        } catch (Exception ex) {
            log.error("短链接统计消息延迟投递异常，fullShortUrl：{}", statsRecord.getFullShortUrl(), ex);
        }
    }
}
