package com.cdy.shortlink.project.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdy.shortlink.project.common.constant.RedisKeyConstant;
import com.cdy.shortlink.project.dao.entity.LinkAccessLogsDO;
import com.cdy.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkBrowserStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkDeviceStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkLocaleStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkOsStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkStatsTodayDO;
import com.cdy.shortlink.project.dao.entity.ShortLinkDO;
import com.cdy.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.cdy.shortlink.project.dao.mapper.LinkAccessLogsMapper;
import com.cdy.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.cdy.shortlink.project.dao.mapper.LinkBrowserStatsMapper;
import com.cdy.shortlink.project.dao.mapper.LinkDeviceStatsMapper;
import com.cdy.shortlink.project.dao.mapper.LinkLocaleStatsMapper;
import com.cdy.shortlink.project.dao.mapper.LinkNetworkStatsMapper;
import com.cdy.shortlink.project.dao.mapper.LinkOsStatsMapper;
import com.cdy.shortlink.project.dao.mapper.LinkStatsTodayMapper;
import com.cdy.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.cdy.shortlink.project.dao.mapper.ShortLinkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 回收站过期数据清理任务
 * <p>
 * 移入回收站的链接，{@code del_flag = 1} 且 {@code del_time} 记录了移入时间。
 * 超过保留期（默认 30 天）后，把链接本身、跳转路由表、各统计表和访问日志一并物理删除，
 * 否则回收站会无限增长，统计表也会一直留着已经删掉的链接。
 * <p>
 * 默认每天凌晨 3 点跑一次，可用 {@code short-link.recycle-bin.cleanup-cron} 调整。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecycleBinCleanupTask {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${short-link.recycle-bin.cleanup.enabled:true}")
    private boolean enabled;

    /**
     * 回收站保留天数
     */
    @Value("${short-link.recycle-bin.retention-days:30}")
    private long retentionDays;

    /**
     * 单次最多清理多少条，避免一次删太多拖垮数据库
     */
    @Value("${short-link.recycle-bin.batch-size:200}")
    private long batchSize;

    @Scheduled(cron = "${short-link.recycle-bin.cleanup-cron:0 0 3 * * ?}")
    public void cleanup() {
        if (!enabled) {
            return;
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        List<ShortLinkDO> expiredLinks;
        try {
            expiredLinks = shortLinkMapper.selectList(Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getDelFlag, 1)
                    .lt(ShortLinkDO::getDelTime, cutoff)
                    .last("limit " + batchSize));
        } catch (Exception ex) {
            log.error("回收站清理：查询过期链接失败", ex);
            return;
        }
        if (expiredLinks == null || expiredLinks.isEmpty()) {
            return;
        }
        int removed = 0;
        for (ShortLinkDO link : expiredLinks) {
            try {
                removeLink(link);
                removed++;
            } catch (Exception ex) {
                log.error("回收站清理：删除链接失败，gid：{}，fullShortUrl：{}",
                        link.getGid(), link.getFullShortUrl(), ex);
            }
        }
        log.info("回收站清理完成，保留 {} 天，扫描 {} 条，删除 {} 条", retentionDays, expiredLinks.size(), removed);
    }

    /**
     * 物理删除一条链接及其全部关联数据
     */
    private void removeLink(ShortLinkDO link) {
        String gid = link.getGid();
        String fullShortUrl = link.getFullShortUrl();
        // t_link 按 gid 分片，必须带 gid 才能路由到正确分表
        shortLinkMapper.delete(Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, gid)
                .eq(ShortLinkDO::getFullShortUrl, fullShortUrl));
        // 跳转路由表按 full_short_url 分片
        shortLinkGotoMapper.delete(Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl));
        linkAccessLogsMapper.delete(Wrappers.lambdaQuery(LinkAccessLogsDO.class)
                .eq(LinkAccessLogsDO::getFullShortUrl, fullShortUrl));
        linkAccessStatsMapper.delete(Wrappers.lambdaQuery(LinkAccessStatsDO.class)
                .eq(LinkAccessStatsDO::getFullShortUrl, fullShortUrl));
        linkLocaleStatsMapper.delete(Wrappers.lambdaQuery(LinkLocaleStatsDO.class)
                .eq(LinkLocaleStatsDO::getFullShortUrl, fullShortUrl));
        linkOsStatsMapper.delete(Wrappers.lambdaQuery(LinkOsStatsDO.class)
                .eq(LinkOsStatsDO::getFullShortUrl, fullShortUrl));
        linkBrowserStatsMapper.delete(Wrappers.lambdaQuery(LinkBrowserStatsDO.class)
                .eq(LinkBrowserStatsDO::getFullShortUrl, fullShortUrl));
        linkDeviceStatsMapper.delete(Wrappers.lambdaQuery(LinkDeviceStatsDO.class)
                .eq(LinkDeviceStatsDO::getFullShortUrl, fullShortUrl));
        linkNetworkStatsMapper.delete(Wrappers.lambdaQuery(LinkNetworkStatsDO.class)
                .eq(LinkNetworkStatsDO::getFullShortUrl, fullShortUrl));
        // 今日统计按 gid 分片
        linkStatsTodayMapper.delete(Wrappers.lambdaQuery(LinkStatsTodayDO.class)
                .eq(LinkStatsTodayDO::getGid, gid)
                .eq(LinkStatsTodayDO::getFullShortUrl, fullShortUrl));
        // 清掉跳转缓存和空值缓存
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl));
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        log.info("回收站清理：已删除链接 {}", fullShortUrl);
    }
}
