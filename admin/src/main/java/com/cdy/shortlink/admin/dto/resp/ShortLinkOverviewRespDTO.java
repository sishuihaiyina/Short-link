package com.cdy.shortlink.admin.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全站总览响应实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkOverviewRespDTO {

    /**
     * 链接总数
     */
    private Long totalLinks;

    /**
     * 分组数
     */
    private Integer groupCount;

    /**
     * 访问次数
     */
    private Long pv;

    /**
     * 访问人数
     */
    private Long uv;

    /**
     * 访问 IP 数
     */
    private Long uip;

    /**
     * 统计失败的分组数（某个分组查不到数据时不影响整体）
     */
    private Integer failedGroups;

    private String startDate;

    private String endDate;
}
