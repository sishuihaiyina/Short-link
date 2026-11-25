package com.cdy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cdy.shortlink.project.common.constant.RedisKeyConstant;
import com.cdy.shortlink.project.common.convention.exception.ClientException;
import com.cdy.shortlink.project.common.convention.exception.ServiceException;
import com.cdy.shortlink.project.common.enums.VailDateTypeEnum;
import com.cdy.shortlink.project.dao.entity.*;
import com.cdy.shortlink.project.dao.mapper.*;
import com.cdy.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.cdy.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.cdy.shortlink.project.dto.resp.*;
import com.cdy.shortlink.project.mq.producer.ShortLinkStatsSaveProducer;
import com.cdy.shortlink.project.service.LinkStatsTodayService;
import com.cdy.shortlink.project.service.ShortLinkService;
import com.cdy.shortlink.project.toolkit.HashUtil;
import com.cdy.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 短链接服务层实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateRegisterCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final LinkStatsTodayService linkStatsTodayService;
    private final ShortLinkStatsSaveProducer shortLinkStatsSaveProducer;


    @Value("${short-link.domain.default}")
    private String createShortLinkDefaultDomain;


    /**
     * 创建短链接
     * @param shortLinkCreateReqDTO 短链接创建请求DTO
     * @return 短链接创建响应DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO shortLinkCreateReqDTO) {
        // shortUri数据库表使用utf8_bin,避免大小写查询到同一个短链接
        // 生成后缀
        String shortLinkSuffix = generateSuffix(shortLinkCreateReqDTO);
        // 生成完整短链接
        // 这里的域名是application.yaml里的默认域名，用户也可以自定义域名
        String fullShortUrl = createShortLinkDefaultDomain + "/" + shortLinkSuffix;
        // 构建短链接DO
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(createShortLinkDefaultDomain)
                .originUrl(shortLinkCreateReqDTO.getOriginUrl())
                .gid(shortLinkCreateReqDTO.getGid())
                .createdType(shortLinkCreateReqDTO.getCreatedType())
                .validDateType(shortLinkCreateReqDTO.getValidDateType())
                .validDate(shortLinkCreateReqDTO.getValidDate())
                .describe(shortLinkCreateReqDTO.getDescribe())
                .shortUri(shortLinkSuffix)
                .fullShortUrl(fullShortUrl)
                .enableStatus(0)
                .totalPv(0)
                .totalUv(0)
                .totalUip(0)
                .delTime(0L)
                .favicon(getFavicon(shortLinkCreateReqDTO.getOriginUrl()))
                .build();
        // 布隆过滤器误判，数据库兜底
        try {
            baseMapper.insert(shortLinkDO);
            // 插入短链接跳转记录
            shortLinkGotoMapper.insert(ShortLinkGotoDO.builder()
                    .gid(shortLinkCreateReqDTO.getGid())
                    .fullShortUrl(fullShortUrl)
                    .build());
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(String.format("短链接：%s 生成重复", fullShortUrl));
        }
        // 缓存预热
        stringRedisTemplate.opsForValue().set(
                String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl),
                shortLinkCreateReqDTO.getOriginUrl(),
                LinkUtil.getLinkCacheValidDate(shortLinkCreateReqDTO.getValidDate()),
                TimeUnit.MILLISECONDS);
        shortUriCreateRegisterCachePenetrationBloomFilter.add(fullShortUrl);

        return ShortLinkCreateRespDTO.builder()
                .gid(shortLinkCreateReqDTO.getGid())
                .originUrl(shortLinkCreateReqDTO.getOriginUrl())
                // 协议暂时先写死
                .fullShortUrl("http://" + shortLinkDO.getFullShortUrl())
                .build();
    }

    /**
     * 生成短链接后缀
     * @param shortLinkCreateReqDTO 短链接创建请求DTO
     * @return 短链接后缀
     */
    private String generateSuffix(ShortLinkCreateReqDTO shortLinkCreateReqDTO) {
        // 哈希冲突处理，避免重复生成相同的短链接后缀(重试)
        int customGenerateCount = 0;   // 自定义生成次数
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后重试");
            }
            String originUrl = shortLinkCreateReqDTO.getOriginUrl();
            originUrl += UUID.randomUUID().toString();
            shortUri = HashUtil.hashToBase62(originUrl);
            // 布隆过滤器判断是否存在短链接后缀
            // 与实际入库的完整短链保持一致（createShortLink 用的是默认域名）
            if (!shortUriCreateRegisterCachePenetrationBloomFilter.contains(createShortLinkDefaultDomain + "/" + shortUri)) {
                break;
            }
             customGenerateCount++;
        }
        return shortUri;
    }

    /**
     * 短链接分页
     * @param shortLinkPageReqDTO 短链接分页请求DTO
     * @return 短链接分页响应DTO
     */
    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO shortLinkPageReqDTO) {
        IPage<ShortLinkDO> resultPage = baseMapper.pageLink(shortLinkPageReqDTO);
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            // 协议暂时先写死
            result.setDomain("http://" + result.getDomain());
            return result;
        });
    }

    /**
     * 查询短连接分组内数量
     * @param requestIds 短链接请求ID列表
     * @return 短链接分组内数量列表
     */
    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestIds) {
        QueryWrapper<ShortLinkDO> queryWrapper = Wrappers.query(new ShortLinkDO())
                .select("gid as gid, count(*) as shortLinkCount")
                .in("gid", requestIds)
                .eq("enable_status", 0)
                .eq("del_flag", 0)
                .groupBy("gid");
        List<Map<String, Object>> shortLinkDOList = baseMapper.selectMaps(queryWrapper);
        return BeanUtil.copyToList(shortLinkDOList, ShortLinkGroupCountQueryRespDTO.class);
    }

    /**
     * 修改短链接
     * @param shortLinkUpdateReqDTO 短链接修改请求DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortLink(ShortLinkUpdateReqDTO shortLinkUpdateReqDTO) {
        // 查询短链接是否存在
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, shortLinkUpdateReqDTO.getOriginGid())
                .eq(ShortLinkDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);
        if (hasShortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }
        // 判断是否换了分组
        if (Objects.equals(hasShortLinkDO.getGid(), shortLinkUpdateReqDTO.getGid())) {
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, shortLinkUpdateReqDTO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .set(Objects.equals(shortLinkUpdateReqDTO.getValidDateType(), VailDateTypeEnum.PERMANENT.getType()), ShortLinkDO::getValidDate, null);
            ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                    .domain(hasShortLinkDO.getDomain())
                    .shortUri(hasShortLinkDO.getShortUri())
                    .favicon(hasShortLinkDO.getFavicon())
                    .createdType(hasShortLinkDO.getCreatedType())
                    .gid(shortLinkUpdateReqDTO.getGid())
                    .originUrl(shortLinkUpdateReqDTO.getOriginUrl())
                    .describe(shortLinkUpdateReqDTO.getDescribe())
                    .validDateType(shortLinkUpdateReqDTO.getValidDateType())
                    .validDate(shortLinkUpdateReqDTO.getValidDate())
                    .build();
            baseMapper.update(shortLinkDO, updateWrapper);
        } else {
            // 使用读写锁，因为读多写少
            RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(RedisKeyConstant.LOCK_GID_UPDATE_KEY, shortLinkUpdateReqDTO.getFullShortUrl()));
            RLock rLock = readWriteLock.writeLock();
            // 尝试获取写锁
            if (!rLock.tryLock()) {
                throw new ServiceException("短链接正在被访问，请稍后再试...");
            }
            try {
                LambdaUpdateWrapper<ShortLinkDO> linkUpdateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                        .eq(ShortLinkDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(ShortLinkDO::getGid, hasShortLinkDO.getGid())
                        .eq(ShortLinkDO::getDelFlag, 0)
                        .eq(ShortLinkDO::getDelTime, 0L)
                        .eq(ShortLinkDO::getEnableStatus, 0);
                ShortLinkDO delShortLinkDO = ShortLinkDO.builder()
                        .delTime(System.currentTimeMillis())
                        .build();
                // 逻辑删除，设置 del_flag 为 1
                delShortLinkDO.setDelFlag(1);
                baseMapper.update(delShortLinkDO, linkUpdateWrapper);
                ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                        // 沿用原域名，保证 domain 与 fullShortUrl 一致
                        .domain(hasShortLinkDO.getDomain())
                        .originUrl(shortLinkUpdateReqDTO.getOriginUrl())
                        .gid(shortLinkUpdateReqDTO.getGid())
                        .createdType(hasShortLinkDO.getCreatedType())
                        .validDateType(shortLinkUpdateReqDTO.getValidDateType())
                        .validDate(shortLinkUpdateReqDTO.getValidDate())
                        .describe(shortLinkUpdateReqDTO.getDescribe())
                        .shortUri(hasShortLinkDO.getShortUri())
                        .enableStatus(hasShortLinkDO.getEnableStatus())
                        .totalPv(hasShortLinkDO.getTotalPv())
                        .totalUv(hasShortLinkDO.getTotalUv())
                        .totalUip(hasShortLinkDO.getTotalUip())
                        .fullShortUrl(hasShortLinkDO.getFullShortUrl())
                        .favicon(getFavicon(shortLinkUpdateReqDTO.getOriginUrl()))
                        .delTime(0L)
                        .build();
                baseMapper.insert(shortLinkDO);
                // 更新今日访问统计表
                LambdaQueryWrapper<LinkStatsTodayDO> statsTodayQueryWrapper = Wrappers.lambdaQuery(LinkStatsTodayDO.class)
                        .eq(LinkStatsTodayDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(LinkStatsTodayDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkStatsTodayDO::getDelFlag, 0);
                List<LinkStatsTodayDO> linkStatsTodayDOList = linkStatsTodayMapper.selectList(statsTodayQueryWrapper);
                if (CollUtil.isNotEmpty(linkStatsTodayDOList)) {
                    // id 在各分片表里独立自增、会重复，deleteBatchIds 会广播并按 id 误删其他分片的数据；
                    // 必须按分片键 full_short_url 删除
                    /** 为什么不能直接 update ，要先 delete 再 insert ？
                        因为这张表有唯一联合索引 UNIQUE KEY uk_url_gid_date (full_short_url, gid, date)，直接更新会导致唯一键冲突，事务会回滚
                     */
                    linkStatsTodayMapper.delete(Wrappers.lambdaQuery(LinkStatsTodayDO.class)
                            .eq(LinkStatsTodayDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                            .eq(LinkStatsTodayDO::getGid, hasShortLinkDO.getGid()));
                    linkStatsTodayDOList.forEach(each -> each.setGid(shortLinkUpdateReqDTO.getGid()));
                    linkStatsTodayService.saveBatch(linkStatsTodayDOList);
                }
                // 直接按分片键更新路由表；不能用 deleteById，id 在分片间会重复，会误删其他分片的数据
                shortLinkGotoMapper.update(null, Wrappers.lambdaUpdate(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .set(ShortLinkGotoDO::getGid, shortLinkUpdateReqDTO.getGid()));
                // 更新访问统计表
                LambdaUpdateWrapper<LinkAccessStatsDO> linkAccessStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkAccessStatsDO.class)
                        .eq(LinkAccessStatsDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(LinkAccessStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkAccessStatsDO::getDelFlag, 0);
                LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                        .gid(shortLinkUpdateReqDTO.getGid())
                        .build();
                linkAccessStatsMapper.update(linkAccessStatsDO, linkAccessStatsUpdateWrapper);
                // 更新访问地区统计表
                LambdaUpdateWrapper<LinkLocaleStatsDO> linkLocaleStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkLocaleStatsDO.class)
                        .eq(LinkLocaleStatsDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(LinkLocaleStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkLocaleStatsDO::getDelFlag, 0);
                LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .gid(shortLinkUpdateReqDTO.getGid())
                        .build();
                linkLocaleStatsMapper.update(linkLocaleStatsDO, linkLocaleStatsUpdateWrapper);
                // 更新访问操作系统统计表
                LambdaUpdateWrapper<LinkOsStatsDO> linkOsStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkOsStatsDO.class)
                        .eq(LinkOsStatsDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(LinkOsStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkOsStatsDO::getDelFlag, 0);
                LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                        .gid(shortLinkUpdateReqDTO.getGid())
                        .build();
                linkOsStatsMapper.update(linkOsStatsDO, linkOsStatsUpdateWrapper);
                // 更新访问浏览器统计表
                LambdaUpdateWrapper<LinkBrowserStatsDO> linkBrowserStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkBrowserStatsDO.class)
                        .eq(LinkBrowserStatsDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(LinkBrowserStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkBrowserStatsDO::getDelFlag, 0);
                LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                        .gid(shortLinkUpdateReqDTO.getGid())
                        .build();
                linkBrowserStatsMapper.update(linkBrowserStatsDO, linkBrowserStatsUpdateWrapper);
                // 更新访问设备统计表
                LambdaUpdateWrapper<LinkDeviceStatsDO> linkDeviceStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkDeviceStatsDO.class)
                        .eq(LinkDeviceStatsDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(LinkDeviceStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkDeviceStatsDO::getDelFlag, 0);
                LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                        .gid(shortLinkUpdateReqDTO.getGid())
                        .build();
                linkDeviceStatsMapper.update(linkDeviceStatsDO, linkDeviceStatsUpdateWrapper);
                // 更新访问网络统计表
                LambdaUpdateWrapper<LinkNetworkStatsDO> linkNetworkStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkNetworkStatsDO.class)
                        .eq(LinkNetworkStatsDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(LinkNetworkStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkNetworkStatsDO::getDelFlag, 0);
                LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                        .gid(shortLinkUpdateReqDTO.getGid())
                        .build();
                linkNetworkStatsMapper.update(linkNetworkStatsDO, linkNetworkStatsUpdateWrapper);
                // 更新访问日志表
                LambdaUpdateWrapper<LinkAccessLogsDO> linkAccessLogsUpdateWrapper = Wrappers.lambdaUpdate(LinkAccessLogsDO.class)
                        .eq(LinkAccessLogsDO::getFullShortUrl, shortLinkUpdateReqDTO.getFullShortUrl())
                        .eq(LinkAccessLogsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkAccessLogsDO::getDelFlag, 0);
                LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                        .gid(shortLinkUpdateReqDTO.getGid())
                        .build();
                linkAccessLogsMapper.update(linkAccessLogsDO, linkAccessLogsUpdateWrapper);
            } finally {
                rLock.unlock();
            }
        }
        // 原始链接或有效期任一变化，都要让正向缓存（完整短链 → 原始链接）失效
        boolean originUrlChanged = !Objects.equals(hasShortLinkDO.getOriginUrl(), shortLinkUpdateReqDTO.getOriginUrl());
        boolean validDateChanged = !Objects.equals(hasShortLinkDO.getValidDateType(), shortLinkUpdateReqDTO.getValidDateType())
                || !Objects.equals(hasShortLinkDO.getValidDate(), shortLinkUpdateReqDTO.getValidDate());

        if (originUrlChanged || validDateChanged) {
            // 收集需要失效的 key，等事务提交后再删
            List<String> invalidKeys = new ArrayList<>();
            // 1. 删除正向缓存：如果不删，用户下一次访问短链，依然会被重定向到旧地址，或者过了新设定的有效期依然能访问
            invalidKeys.add(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, shortLinkUpdateReqDTO.getFullShortUrl()));

            // 2. 如果原本是"已过期"状态，判断更新后是否恢复有效（如果不把空值缓存删掉，那么用户访问这个短链，系统依然会命中空值缓存，提示“短链不存在或已过期”）
            Date now = new Date();
            boolean wasExpired = hasShortLinkDO.getValidDate() != null && hasShortLinkDO.getValidDate().before(now);
            if (wasExpired) {
                boolean nowPermanent = Objects.equals(
                        shortLinkUpdateReqDTO.getValidDateType(), VailDateTypeEnum.PERMANENT.getType());
                boolean newDateInFuture = shortLinkUpdateReqDTO.getValidDate() != null
                        && shortLinkUpdateReqDTO.getValidDate().after(now);
                if (nowPermanent || newDateInFuture) {
                    // 清掉之前写入的空值缓存，否则仍会被判为不存在
                    invalidKeys.add(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, shortLinkUpdateReqDTO.getFullShortUrl()));
                }
            }
            // 事务提交后再删，避免并发读在提交前把旧值写回缓存
            deleteCacheAfterCommit(invalidKeys);
        }

    }

    /**
     * 事务提交后再删除缓存。
     * <p>
     * 如果事务提交前就删缓存，可能出现：并发请求缓存未命中 → 读到未提交的旧值 → 把旧值写回缓存，
     * 事务提交后缓存就成了脏数据。放到 afterCommit 里删，至少能覆盖这个窗口。
     * 没有事务时直接删。
     */
    private void deleteCacheAfterCommit(List<String> keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            keys.forEach(stringRedisTemplate::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(stringRedisTemplate::delete);
            }
        });
    }

    /**
     * 短链接跳转
     * @param shortUri 短链接
     * @param response 响应
     * @param request 请求
     */
    @Override
    public void restoreUrl(String shortUri, ServletResponse response, ServletRequest request) throws IOException {
        String serverName = request.getServerName();
        String serverPort = Optional.of(request.getServerPort())
                .filter(each -> !Objects.equals(each, 80))
                .map(String::valueOf)
                .map(each -> ":" + each)
                .orElse("");
        String fullShortUrl = serverName + serverPort + "/" + shortUri;
        String originalLink = stringRedisTemplate.opsForValue().get(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl));
        // 缓存命中，直接跳转原始链接
        if (StrUtil.isNotBlank(originalLink)) {
            // 缓存命中：统计失败不能影响跳转，gid 未知传 null，由统计方法内部反查
            recordStatsSafely(fullShortUrl, null, request, response);
            ((HttpServletResponse) response).sendRedirect(originalLink);
            return;
        }
        // 防止缓存击穿，使用布隆过滤器判断是否存在短链接（防止不同短链请求)
        boolean contains = shortUriCreateRegisterCachePenetrationBloomFilter.contains(fullShortUrl);
        if (!contains) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }
        // 空值缓存，防止同一短链接被重复请求
        String gotoIsNullShortLink = stringRedisTemplate.opsForValue().get(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(gotoIsNullShortLink)) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }

        // 防止缓存击穿，使用分布式锁
        RLock lock = redissonClient.getLock(String.format(RedisKeyConstant.LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        // 必须带超时：持锁线程卡死时，不能让所有请求都堵在 lock() 上把 Tomcat 线程占满
        try {
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }
            // 线程在等待锁时被外部打断（比如服务停机），会抛这个异常 (InterruptedException)
        } catch (InterruptedException e) {
            // 恢复中断标记，不要吞掉中断信号
            Thread.currentThread().interrupt();
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }
        // 第二次检查
        try {
            // 线程A释放锁后线程B拿到锁直接命中缓存，避免重复查询数据库
            originalLink = stringRedisTemplate.opsForValue().get(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl));
            if (StrUtil.isNotBlank(originalLink)) {
                ((HttpServletResponse) response).sendRedirect(originalLink);
                return;
            }
            // 查询短链接是否存在
            // 通过 fullShortUrl 从路由表（ShortLinkGotoDO）拿到这个链接属于哪个分组（gid），才知道去哪个分片查真正的数据
            LambdaQueryWrapper<ShortLinkGotoDO> linkGotoQueryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(linkGotoQueryWrapper);
            if (shortLinkGotoDO == null) {
                stringRedisTemplate.opsForValue().set(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30L, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, shortLinkGotoDO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);
            // 短链接不存在或已过期
            if (shortLinkDO == null || (shortLinkDO.getValidDate() != null && shortLinkDO.getValidDate().before(new Date()))) {
                // 判断是否过期
                stringRedisTemplate.opsForValue().set(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30L, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }
            // 将短链接跳转目标缓存到 Redis
            stringRedisTemplate.opsForValue().set(
                    String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl),
                    shortLinkDO.getOriginUrl(),
                    LinkUtil.getLinkCacheValidDate(shortLinkDO.getValidDate()),
                    TimeUnit.MILLISECONDS);
            // 缓存未命中：gid 已知，统计失败不能影响跳转
            recordStatsSafely(fullShortUrl, shortLinkDO.getGid(), request, response);
            // 跳转短链接目标
            ((HttpServletResponse) response).sendRedirect(shortLinkDO.getOriginUrl());
        } finally {
            // 防止线程A将线程B的锁删除
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    /**
     * 获取短链接图标
     * @param url 短链接
     * @return 短链接图标
     */
    private String getFavicon(String url) {
        // 抓取图标是附加信息，失败/超时都不能影响短链接创建
        try {
            URL targetUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            Document document = Jsoup.connect(url).timeout(3000).get();
            Element faviconLink = document.select("link[rel~=(?i)^(shortcut )?icon]").first();
            return faviconLink == null ? null : faviconLink.attr("abs:href");
        } catch (Exception e) {
            log.warn("获取网站图标失败，url：{}", url, e);
            return null;
        }
    }

    /**
     * 短链接访问统计
     * @param request 请求
     * @param response 响应
     */
    // 通过生成UUID存入浏览器Cookie来实现访客类型统计，因为这样可以在无登录场景下统计匿名访客类型
    private ShortLinkStatsRecordDTO buildLinkStatsRecordAndSetUser(String fullShortUrl, ServletRequest request, ServletResponse response) {
        // Java的lambda不能直接给外面的boolean赋值，用AtomicBoolean可以绕过限制
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        AtomicReference<String> uv = new AtomicReference<>();
        Runnable addResponseCookieTask = () -> {
            uv.set(UUID.fastUUID().toString());
            Cookie uvCookie = new Cookie("uv", uv.get());
            uvCookie.setMaxAge(60 * 60 * 24 * 30);
            uvCookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
            ((HttpServletResponse) response).addCookie(uvCookie);
            uvFirstFlag.set(Boolean.TRUE);
            stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, uv.get());
        };
        if (ArrayUtil.isNotEmpty(cookies)) {
            Arrays.stream(cookies)
                    .filter(each -> Objects.equals(each.getName(), "uv"))
                    .findFirst()
                    .map(Cookie::getValue)
                    .ifPresentOrElse(each -> {
                        uv.set(each);
                        Long uvAdded = stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, each);
                        uvFirstFlag.set(uvAdded != null && uvAdded > 0L);
                    }, addResponseCookieTask);
        } else {
            addResponseCookieTask.run();
        }
        String remoteAddr = LinkUtil.getIp(((HttpServletRequest) request));
        String os = LinkUtil.getOs(((HttpServletRequest) request));
        String browser = LinkUtil.getBrowser(((HttpServletRequest) request));
        String device = LinkUtil.getDevice(((HttpServletRequest) request));
        String network = LinkUtil.getNetwork(((HttpServletRequest) request));
        Long uipAdded = stringRedisTemplate.opsForSet().add("short-link:stats:uip:" + fullShortUrl, remoteAddr);
        boolean uipFirstFlag = uipAdded != null && uipAdded > 0L;
        // UV/UIP 集合 30 天后过期，避免 Redis key 无限增长（与 UV Cookie 有效期一致）
        stringRedisTemplate.expire("short-link:stats:uv:" + fullShortUrl, 30, TimeUnit.DAYS);
        stringRedisTemplate.expire("short-link:stats:uip:" + fullShortUrl, 30, TimeUnit.DAYS);
        return ShortLinkStatsRecordDTO.builder()
                // 每次访问生成唯一事件 ID，消费端据此做幂等去重
                .eventId(UUID.fastUUID().toString())
                .fullShortUrl(fullShortUrl)
                .uv(uv.get())
                .uvFirstFlag(uvFirstFlag.get())
                .uipFirstFlag(uipFirstFlag)
                .remoteAddr(remoteAddr)
                .os(os)
                .browser(browser)
                .device(device)
                .network(network)
                .build();
    }

    /**
     * 短链接访问统计(消息生产者)
     * @param fullShortUrl 短链接
     * @param gid 分组
     * @param statsRecord 统计记录
     */
    @Override
    public void shortLinkStats(String fullShortUrl, String gid, ShortLinkStatsRecordDTO statsRecord) {
        // 缓存命中时 fullShortUrl / gid 可能为空，统一从统计记录里补全
        fullShortUrl = StrUtil.isBlank(fullShortUrl) ? statsRecord.getFullShortUrl() : fullShortUrl;
        Map<String, String> producerMap = new HashMap<>();
        // XADD 不接受 null 值：gid 为空时传空串，消费端会从 t_link_goto 反查
        producerMap.put("fullShortUrl", StrUtil.nullToEmpty(fullShortUrl));
        producerMap.put("gid", StrUtil.nullToEmpty(gid));
        producerMap.put("statsRecord", JSON.toJSONString(statsRecord));
        try {
            shortLinkStatsSaveProducer.send(producerMap);
        } catch (Exception ex) {
            // 统计属于旁路逻辑，发送失败只记录日志，不能影响短链跳转
            log.error("短链接统计消息发送失败，fullShortUrl：{}", fullShortUrl, ex);
        }
    }

    /**
     * 构造并发送统计记录，任何异常都不外抛，保证短链跳转不受统计链路影响
     */
    private void recordStatsSafely(String fullShortUrl, String gid, ServletRequest request, ServletResponse response) {
        try {
            ShortLinkStatsRecordDTO statsRecord = buildLinkStatsRecordAndSetUser(fullShortUrl, request, response);
            shortLinkStats(fullShortUrl, gid, statsRecord);
        } catch (Exception ex) {
            log.error("短链接统计记录失败，不影响跳转，fullShortUrl：{}", fullShortUrl, ex);
        }
    }


    /**
     * 批量创建短链接
     * @param requestParam 批量创建短链接请求参数
     * @return 批量创建短链接返回参数
     */
    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        List<String> originUrls = requestParam.getOriginUrls();
        List<String> describes = requestParam.getDescribes();
        List<ShortLinkBaseInfoRespDTO> result = new ArrayList<>();
        for (int i = 0; i < originUrls.size(); i++) {
            ShortLinkCreateReqDTO shortLinkCreateReqDTO = BeanUtil.toBean(requestParam, ShortLinkCreateReqDTO.class);
            shortLinkCreateReqDTO.setOriginUrl(originUrls.get(i));
            // describes 可能为空或比 originUrls 短，避免越界
            shortLinkCreateReqDTO.setDescribe(CollUtil.size(describes) > i ? describes.get(i) : null);
            try {
                ShortLinkCreateRespDTO shortLink = createShortLink(shortLinkCreateReqDTO);
                ShortLinkBaseInfoRespDTO linkBaseInfoRespDTO = ShortLinkBaseInfoRespDTO.builder()
                        .fullShortUrl(shortLink.getFullShortUrl())
                        .originUrl(shortLink.getOriginUrl())
                        .describe(describes.get(i))
                        .build();
                result.add(linkBaseInfoRespDTO);
            } catch (Throwable ex) {
                log.error("批量创建短链接失败，原始参数：{}", originUrls.get(i));
            }
        }
        return ShortLinkBatchCreateRespDTO.builder()
                .total(result.size())
                .baseLinkInfos(result)
                .build();
    }
}
