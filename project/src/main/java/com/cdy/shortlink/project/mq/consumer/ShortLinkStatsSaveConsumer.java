package com.cdy.shortlink.project.mq.consumer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.cdy.shortlink.project.service.ShortLinkStatsSaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 短链接监控状态保存消息队列消费者（RocketMQ 版）
 * <p>
 * 与原来的 Redis Stream 版本相比，三件事交给 broker 处理，不用自己写：
 * <ul>
 *     <li>消费失败重试：抛异常即可，RocketMQ 按退避策略重新投递；</li>
 *     <li>超次数转死信：重试 {@code maxReconsumeTimes} 次后自动进入
 *         {@code %DLQ%short-link-stats-consumer-group}；</li>
 *     <li>多实例分担：同一个消费组自动做负载均衡。</li>
 * </ul>
 * 幂等仍由 {@code t_link_access_logs.event_id} 唯一索引兜底，重复投递不会重复计数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${short-link.mq.stats.topic}",
        consumerGroup = "${short-link.mq.stats.consumer-group}",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadNumber = 4,
        maxReconsumeTimes = 5
)
public class ShortLinkStatsSaveConsumer implements RocketMQListener<String> {

    private static final TypeReference<Map<String, String>> MESSAGE_TYPE = new TypeReference<Map<String, String>>() {
    };

    private final ShortLinkStatsSaveService shortLinkStatsSaveService;

    @Override
    public void onMessage(String message) {
        Map<String, String> producerMap;
        try {
            producerMap = JSON.parseObject(message, MESSAGE_TYPE);
        } catch (Exception ex) {
            // 格式都不对的消息重试多少次都没用，直接丢弃
            log.error("短链接统计消息格式错误，直接丢弃，message：{}", message, ex);
            return;
        }
        if (producerMap == null || producerMap.isEmpty()) {
            log.warn("短链接统计消息为空，直接丢弃");
            return;
        }
        String fullShortUrl = producerMap.get("fullShortUrl");
        if (StrUtil.isBlank(fullShortUrl)) {
            log.warn("短链接统计消息缺少 fullShortUrl，直接丢弃，message：{}", message);
            return;
        }
        ShortLinkStatsRecordDTO statsRecord;
        try {
            statsRecord = JSON.parseObject(producerMap.get("statsRecord"), ShortLinkStatsRecordDTO.class);
        } catch (Exception ex) {
            log.error("短链接统计消息内容解析失败，直接丢弃，fullShortUrl：{}", fullShortUrl, ex);
            return;
        }
        if (statsRecord == null) {
            log.warn("短链接统计消息内容为空，直接丢弃，fullShortUrl：{}", fullShortUrl);
            return;
        }
        // 这里不吞异常：落库失败就抛出去，交给 RocketMQ 重投/进死信队列
        shortLinkStatsSaveService.saveShortLinkStats(fullShortUrl, producerMap.get("gid"), statsRecord);
    }
}
