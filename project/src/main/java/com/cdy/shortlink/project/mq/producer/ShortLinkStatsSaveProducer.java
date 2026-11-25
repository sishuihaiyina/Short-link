package com.cdy.shortlink.project.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
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

import java.util.Map;

/**
 * 短链接监控状态保存消息队列生产者（RocketMQ 版）
 * <p>
 * 统计属于旁路逻辑：用异步发送，发送失败只记日志，绝不阻塞短链跳转。
 * 消息体是 {@code {fullShortUrl, gid, statsRecord}} 的 JSON，消费端按同样的结构解析。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsSaveProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${short-link.mq.stats.topic}")
    private String topic;

    /**
     * 发送短链接统计消息
     *
     * @param producerMap 包含 fullShortUrl、gid、statsRecord 的消息体
     */
    public void send(Map<String, String> producerMap) {
        String fullShortUrl = producerMap.get("fullShortUrl");
        Message<String> message = MessageBuilder.withPayload(JSON.toJSONString(producerMap))
                // 用完整短链接做业务 key，排查时可以直接按 key 查消息
                .setHeader(RocketMQHeaders.KEYS, StrUtil.nullToEmpty(fullShortUrl))
                .build();
        try {
            rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("短链接统计消息发送成功，fullShortUrl：{}，msgId：{}",
                            fullShortUrl, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("短链接统计消息发送失败，fullShortUrl：{}", fullShortUrl, throwable);
                }
            });
        } catch (Exception ex) {
            // 发送失败不能影响短链跳转，只记录日志
            log.error("短链接统计消息发送异常，fullShortUrl：{}", fullShortUrl, ex);
        }
    }
}
