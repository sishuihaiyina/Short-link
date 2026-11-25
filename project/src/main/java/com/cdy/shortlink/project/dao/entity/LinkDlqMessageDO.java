package com.cdy.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cdy.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统计消息死信
 * <p>
 * RocketMQ 重试次数耗尽后会把消息投递到 {@code %DLQ%short-link-stats-consumer-group}，
 * 消费者把原始消息落到这张表，运维可以先排障、再手动重放。
 */
@Data
@TableName("t_link_dlq_message")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkDlqMessageDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件 ID，用于幂等重放
     */
    private String eventId;

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 原始消息体
     */
    private String payload;

    /**
     * 是否已重放 0：未重放 1：已重放
     */
    private Integer replayed;
}
