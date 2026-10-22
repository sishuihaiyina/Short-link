package com.cdy.shortlink.project.service.Impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdy.shortlink.project.dao.entity.LinkAccessLogsDO;
import com.cdy.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkBrowserStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkDeviceStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkLocaleStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkOsStatsDO;
import com.cdy.shortlink.project.dao.entity.LinkStatsTodayDO;
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
import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.cdy.shortlink.project.mq.producer.DelayShortLinkStatsProducer;
import com.cdy.shortlink.project.service.ShortLinkStatsSaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.cdy.shortlink.project.common.constant.RedisKeyConstant.LOCK_GID_UPDATE_KEY;
import static com.cdy.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

/**
 * 短链接监控数据落库服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkStatsSaveServiceImpl implements ShortLinkStatsSaveService {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final DelayShortLinkStatsProducer delayShortLinkStatsProducer;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    /**
     * 保存一条访问统计
     * <p>
     * 幂等设计：以 t_link_access_logs.event_id 唯一索引作为去重闸门。
     * 只有日志插入成功（首次消费）才继续累加各统计表；
     * 插入返回 0 说明是重复消息（网络重试、ACK 失败重投等），直接跳过。
     * <p>
     * 不吞异常：落库失败会抛出，@Transactional 回滚，由消费者决定消息是否保留。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveShortLinkStats(String fullShortUrl, String gid, ShortLinkStatsRecordDTO statsRecord) {
        // 延迟队列重试时不会带 fullShortUrl，从统计记录里补
        fullShortUrl = StrUtil.isBlank(fullShortUrl) ? statsRecord.getFullShortUrl() : fullShortUrl;

        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();
        // 正在修改分组：此时写统计会落到旧 gid 的分片，转入延迟队列 5 秒后重试
        if (!rLock.tryLock()) {
            delayShortLinkStatsProducer.send(statsRecord);
            return;
        }
        try {
            // 缓存命中时拿不到 gid，从路由表反查
            if (StrUtil.isBlank(gid)) {
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(
                        Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                                .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl));
                if (shortLinkGotoDO == null) {
                    // 路由记录不存在（短链已被彻底删除），重试也没有意义，直接结束
                    log.warn("短链接路由记录不存在，fullShortUrl：{}", fullShortUrl);
                    return;
                }
                gid = shortLinkGotoDO.getGid();
            }

            // 先查地区：访问日志需要把地区一起写进去；高德失败时用"未知"兜底
            String actualProvince = "未知";
            String actualCity = "未知";
            String actualAdcode = "未知";
            boolean localeResolved = false;
            try {
                Map<String, Object> localeParamMap = new HashMap<>();
                localeParamMap.put("key", statsLocaleAmapKey);
                localeParamMap.put("ip", statsRecord.getRemoteAddr());
                String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
                JSONObject localeResultObj = JSON.parseObject(localeResultStr);
                String infoCode = localeResultObj.getString("infocode");
                if (StrUtil.isNotBlank(infoCode) && StrUtil.equals(infoCode, "10000")) {
                    localeResolved = true;
                    String province = localeResultObj.getString("province");
                    boolean unknownFlag = StrUtil.equals(province, "[]");
                    if (!unknownFlag) {
                        actualProvince = province;
                        actualCity = localeResultObj.getString("city");
                        actualAdcode = localeResultObj.getString("adcode");
                    }
                }
            } catch (Exception ex) {
                log.warn("高德 IP 定位失败，ip：{}", statsRecord.getRemoteAddr(), ex);
            }

            // ===== 幂等闸门：先写访问日志 =====
            // event_id 唯一索引冲突时 insertIgnore 返回 0，说明这条消息已经处理过
            if (StrUtil.isBlank(statsRecord.getEventId())) {
                log.warn("统计消息缺少 eventId，本次无法幂等去重，fullShortUrl：{}", fullShortUrl);
            }
            LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                    .eventId(statsRecord.getEventId())
                    .user(statsRecord.getUv())
                    .ip(statsRecord.getRemoteAddr())
                    .browser(statsRecord.getBrowser())
                    .os(statsRecord.getOs())
                    .network(statsRecord.getNetwork())
                    .device(statsRecord.getDevice())
                    .locale(StrUtil.join("-", "中国", actualProvince, actualCity))
                    .gid(gid)
                    .fullShortUrl(fullShortUrl)
                    .build();
            if (linkAccessLogsMapper.insertIgnore(linkAccessLogsDO) == 0) {
                log.info("短链接统计重复消费，跳过，eventId：{}", statsRecord.getEventId());
                return;
            }

            // ===== 以下聚合统计只在首次消费时执行 =====
            int hour = DateUtil.hour(new Date(), true);
            Week week = DateUtil.dayOfWeekEnum(new Date());
            int weekValue = week.getIso8601Value();
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1)
                    .uv(statsRecord.getUvFirstFlag() ? 1 : 0)
                    .uip(statsRecord.getUipFirstFlag() ? 1 : 0)
                    .hour(hour)
                    .weekday(weekValue)
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(new Date())
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);

            if (localeResolved) {
                linkLocaleStatsMapper.shortLinkLocaleStats(LinkLocaleStatsDO.builder()
                        .province(actualProvince)
                        .city(actualCity)
                        .adcode(actualAdcode)
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .country("中国")
                        .gid(gid)
                        .date(new Date())
                        .build());
            }

            linkOsStatsMapper.shortLinkOsStats(LinkOsStatsDO.builder()
                    .os(statsRecord.getOs())
                    .cnt(1).gid(gid).fullShortUrl(fullShortUrl).date(new Date())
                    .build());
            linkBrowserStatsMapper.shortLinkBrowserState(LinkBrowserStatsDO.builder()
                    .browser(statsRecord.getBrowser())
                    .cnt(1).gid(gid).fullShortUrl(fullShortUrl).date(new Date())
                    .build());
            linkDeviceStatsMapper.shortLinkDeviceStats(LinkDeviceStatsDO.builder()
                    .device(statsRecord.getDevice())
                    .cnt(1).gid(gid).fullShortUrl(fullShortUrl).date(new Date())
                    .build());
            linkNetworkStatsMapper.shortLinkNetworkState(LinkNetworkStatsDO.builder()
                    .network(statsRecord.getNetwork())
                    .cnt(1).gid(gid).fullShortUrl(fullShortUrl).date(new Date())
                    .build());
            shortLinkMapper.incrementStats(gid, fullShortUrl, 1,
                    statsRecord.getUvFirstFlag() ? 1 : 0,
                    statsRecord.getUipFirstFlag() ? 1 : 0);
            linkStatsTodayMapper.shortLinkTodayStats(LinkStatsTodayDO.builder()
                    .todayPv(1)
                    .todayUv(statsRecord.getUvFirstFlag() ? 1 : 0)
                    .todayUip(statsRecord.getUipFirstFlag() ? 1 : 0)
                    .gid(gid)
                    .fullShortUrl(fullShortUrl)
                    .date(new Date())
                    .build());
        } finally {
            rLock.unlock();
        }
    }
}
