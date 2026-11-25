package com.cdy.shortlink.project.mq.consumer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.cdy.shortlink.project.service.ShortLinkStatsSaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 短链接监控状态保存消息队列消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsSaveConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final ShortLinkStatsSaveService shortLinkStatsSaveService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${spring.data.redis.channel-topic.short-link-stats-group}")
    private String group;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String stream = message.getStream();
        RecordId id = message.getId();
        Map<String, String> producerMap = message.getValue();

        // 无法处理的消息直接 ACK + 删除，避免一直占用 pending
        if (producerMap == null || producerMap.isEmpty() || StrUtil.isBlank(producerMap.get("fullShortUrl"))) {
            log.warn("短链接统计消息缺少 fullShortUrl，直接丢弃，id：{}", id);
            ackAndDelete(stream, id);
            return;
        }

        try {
            String fullShortUrl = producerMap.get("fullShortUrl");
            String gid = producerMap.get("gid");
            ShortLinkStatsRecordDTO statsRecord = JSON.parseObject(
                    producerMap.get("statsRecord"), ShortLinkStatsRecordDTO.class);
            if (statsRecord == null) {
                log.warn("短链接统计消息内容为空，直接丢弃，id：{}", id);
                ackAndDelete(stream, id);
                return;
            }
            // 落库成功后 ACK；失败不 ACK，消息留在 PEL，由恢复任务 XCLAIM 重试
            shortLinkStatsSaveService.saveShortLinkStats(fullShortUrl, gid, statsRecord);
            ackAndDelete(stream, id);
        } catch (Exception ex) {
            // 不 ACK 也不删除：消息保留在 PEL，等待 ShortLinkStatsPendingRecoveryTask 重试
            log.error("短链接统计消息处理失败，保留在 PEL 等待重试，id：{}", id, ex);
        }
    }

    /**
     * 先 ACK 再从 stream 中删除，避免已消费消息无限堆积
     */
    private void ackAndDelete(String stream, RecordId id) {
        stringRedisTemplate.opsForStream().acknowledge(stream, group, id.getValue());
        stringRedisTemplate.opsForStream().delete(stream, id.getValue());
    }
}
