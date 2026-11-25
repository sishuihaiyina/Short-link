package com.cdy.shortlink.admin.remote.dto.resp;

import lombok.Data;

import java.util.Date;

/**
 * 统计消息死信响应实体
 */
@Data
public class ShortLinkDlqMessageRespDTO {

    private Long id;

    private String eventId;

    private String fullShortUrl;

    private Integer replayed;

    private Date createTime;
}
