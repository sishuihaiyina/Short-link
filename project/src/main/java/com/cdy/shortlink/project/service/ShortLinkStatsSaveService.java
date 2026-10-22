package com.cdy.shortlink.project.service;

import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;

/**
 * 短链接监控数据落库服务
 */
public interface ShortLinkStatsSaveService {

    /**
     * 保存一条短链接访问统计
     * <p>
     * 修改分组期间拿不到读锁时，会自动转入延迟队列，5 秒后重试。
     *
     * @param fullShortUrl 完整短链接，为空时从 statsRecord 中取
     * @param gid          分组标识，为空时从 t_link_goto 反查
     * @param statsRecord  统计记录
     */
    void saveShortLinkStats(String fullShortUrl, String gid, ShortLinkStatsRecordDTO statsRecord);
}
