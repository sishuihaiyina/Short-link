package com.cdy.shortlink.project.dto.biz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

/**
 * 短链接统计实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsRecordDTO {

    /**
     * 访问事件 ID（全局唯一，用于消费端幂等去重）
     */
    private String eventId;

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 访问用户IP
     */
    private String remoteAddr;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作设备
     */
    private String device;

    /**
     * 网络
     */
    private String network;

    /**
     * UV
     */
    private String uv;

    /**
     * UV访问标识（链接维度，30 天内第一次访问，用于累计 UV）
     */
    private Boolean uvFirstFlag;

    /**
     * UIP访问标识（链接维度，30 天内第一次访问，用于累计 UIP）
     */
    private Boolean uipFirstFlag;

    /**
     * 当日 UV 首次访问标识（当天第一次访问，用于今日 UV）
     */
    private Boolean uvDailyFirstFlag;

    /**
     * 当日 UIP 首次访问标识（当天第一次访问，用于今日 UIP）
     */
    private Boolean uipDailyFirstFlag;
}