package com.cdy.shortlink.project.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cdy.shortlink.project.dao.entity.LinkDlqMessageDO;
import com.cdy.shortlink.project.dao.mapper.LinkDlqMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 统计消息死信消费者
 * <p>
 * 正常消费者重试 {@code maxReconsumeTimes} 次仍失败后，RocketMQ 会把消息投递到
 * {@code %DLQ%short-link-stats-consumer-group}。这里把原始消息落到 {@code t_link_dlq_message}，
 * 便于事后排障和手动重放（接口见 {@code /api/short-link/v1/stats/dlq}）。
 * <p>
 * 落库失败只记日志，不再抛出：死信消息本身已经不可处理，重试只会让它进入死信的死信。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "%DLQ%short-link-stats-consumer-group",
        consumerGroup = "short-link-stats-dlq-consumer"
)
public class StatsDlqConsumer implements RocketMQListener<String> {

    private final LinkDlqMessageMapper linkDlqMessageMapper;

    @Override
    public void onMessage(String message) {
        try {
            JSONObject payload = JSON.parseObject(message);
            LinkDlqMessageDO record = LinkDlqMessageDO.builder()
                    .eventId(resolveEventId(payload))
                    .fullShortUrl(payload == null ? null : payload.getString("fullShortUrl"))
                    .payload(message)
                    .replayed(0)
                    .build();
            linkDlqMessageMapper.insert(record);
            log.error("短链接统计消息进入死信队列，已落库，fullShortUrl：{}", record.getFullShortUrl());
        } catch (Exception ex) {
            log.error("死信消息落库失败，message：{}", message, ex);
        }
    }

    /**
     * 统计事件 ID 藏在 statsRecord 这个 JSON 字符串里
     */
    private String resolveEventId(JSONObject payload) {
        if (payload == null) {
            return null;
        }
        String statsRecord = payload.getString("statsRecord");
        if (statsRecord == null) {
            return null;
        }
        JSONObject record = JSON.parseObject(statsRecord);
        return record == null ? null : record.getString("eventId");
    }
}
