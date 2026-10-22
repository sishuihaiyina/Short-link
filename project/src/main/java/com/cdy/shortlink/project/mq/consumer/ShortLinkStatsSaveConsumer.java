package com.cdy.shortlink.project.mq.consumer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.cdy.shortlink.project.service.ShortLinkStatsSaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String stream = message.getStream();
        RecordId id = message.getId();
        Map<String, String> producerMap = message.getValue();
        String fullShortUrl = producerMap.get("fullShortUrl");

        // 缺少短链地址的消息无法处理，直接删除，避免堆积
        if (StrUtil.isBlank(fullShortUrl)) {
            log.warn("短链接统计消息缺少 fullShortUrl，直接丢弃，id：{}", id);
            deleteMessage(stream, id);
            return;
        }

        try {
            String gid = producerMap.get("gid");
            ShortLinkStatsRecordDTO statsRecord = JSON.parseObject(
                    producerMap.get("statsRecord"), ShortLinkStatsRecordDTO.class);
            if (statsRecord == null) {
                log.warn("短链接统计消息内容为空，直接丢弃，id：{}", id);
                deleteMessage(stream, id);
                return;
            }
            // 落库（或改分组期间转入延迟队列）成功后，才删除消息
            shortLinkStatsSaveService.saveShortLinkStats(fullShortUrl, gid, statsRecord);
            deleteMessage(stream, id);
        } catch (Throwable ex) {
            // 处理失败时保留消息，便于排查和后续补偿；生产环境建议改用手动 ACK + 重试/死信
            log.error("短链接统计消息处理失败，保留消息，id：{}", id, ex);
        }
    }

    private void deleteMessage(String stream, RecordId id) {
        stringRedisTemplate.opsForStream().delete(stream, id.getValue());
    }
}
