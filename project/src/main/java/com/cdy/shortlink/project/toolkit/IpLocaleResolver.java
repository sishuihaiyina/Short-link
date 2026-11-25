package com.cdy.shortlink.project.toolkit;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.cdy.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

/**
 * IP 归属地解析器
 * <p>
 * 把「IP → 省/市/区域编码」这件事从统计落库逻辑里抽出来，单独处理三件事：
 * 内网地址短路、结果缓存、接口失败降级。
 * 高德 IP 定位接口有 QPS 和每日配额限制，短链访问又是高频操作，
 * 所以每次请求都不能直接打过去，必须先过缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpLocaleResolver {

    /**
     * 地区缓存 key，一个 IP 一条
     */
    private static final String CACHE_KEY_TEMPLATE = "short-link:stats:locale:%s";

    /**
     * 缓存里表示「解析不出来」的占位值
     */
    private static final String CACHE_UNKNOWN = "UNKNOWN";

    /**
     * 解析成功缓存 1 天：同一个公网 IP 的地区基本不会变
     */
    private static final Duration RESOLVED_TTL = Duration.ofHours(24);

    /**
     * 失败只缓存 5 分钟：既避免故障期间反复打接口，也保证 key 恢复后能自愈
     */
    private static final Duration UNRESOLVED_TTL = Duration.ofMinutes(5);

    /**
     * 高德接口超时时间，避免慢请求把统计消费线程拖住
     */
    private static final int REQUEST_TIMEOUT_MILLIS = 3000;

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${short-link.stats.locale.amap-key}")
    private String amapKey;

    /**
     * 解析 IP 归属地
     *
     * @param ip 客户端 IP
     * @return 归属地结果，解析不出来时返回 {@link IpLocale#unknown()}
     */
    public IpLocale resolve(String ip) {
        String clientIp = LinkUtil.normalizeIp(ip);
        // 内网、回环、非法值、IPv6 都没有归属地可查，直接短路，一次第三方请求都不发
        if (!LinkUtil.isValidIp(clientIp) || LinkUtil.isInternalIp(clientIp)) {
            return IpLocale.unknown();
        }
        IpLocale cached = readCache(clientIp);
        if (cached != null) {
            return cached;
        }
        IpLocale ipLocale = queryAmap(clientIp);
        writeCache(clientIp, ipLocale);
        return ipLocale;
    }

    /**
     * 调用高德 IP 定位接口，任何异常都降级成未知，不影响统计落库
     */
    private IpLocale queryAmap(String ip) {
        try {
            Map<String, Object> paramMap = new HashMap<>(4);
            paramMap.put("key", amapKey);
            paramMap.put("ip", ip);
            String result = HttpUtil.get(AMAP_REMOTE_URL, paramMap, REQUEST_TIMEOUT_MILLIS);
            JSONObject resultObj = JSON.parseObject(result);
            if (resultObj == null || !StrUtil.equals("10000", resultObj.getString("infocode"))) {
                log.warn("高德 IP 定位返回异常，ip：{}，result：{}", ip, result);
                return IpLocale.unknown();
            }
            String province = resultObj.getString("province");
            // 内网、境外 IP 高德返回 "[]"，这种结果不能写进地区统计
            if (StrUtil.isBlank(province) || "[]".equals(province)) {
                return IpLocale.unknown();
            }
            return new IpLocale(province, resultObj.getString("city"), resultObj.getString("adcode"), true);
        } catch (Exception ex) {
            log.warn("高德 IP 定位失败，ip：{}", ip, ex);
            return IpLocale.unknown();
        }
    }

    /**
     * 读缓存，返回 null 表示没有缓存
     */
    private IpLocale readCache(String ip) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey(ip));
            if (StrUtil.isBlank(cached)) {
                return null;
            }
            if (CACHE_UNKNOWN.equals(cached)) {
                return IpLocale.unknown();
            }
            String[] parts = cached.split("\\|", -1);
            return parts.length == 3 ? new IpLocale(parts[0], parts[1], parts[2], true) : null;
        } catch (Exception ex) {
            // 缓存只是加速手段，Redis 异常时退化成直查接口，不能让统计链路跟着失败
            log.warn("读取 IP 归属地缓存失败，ip：{}", ip, ex);
            return null;
        }
    }

    /**
     * 写缓存：成功和失败分开设置过期时间
     */
    private void writeCache(String ip, IpLocale ipLocale) {
        try {
            String value = ipLocale.resolved()
                    ? StrUtil.join("|", ipLocale.province(), ipLocale.city(), ipLocale.adcode())
                    : CACHE_UNKNOWN;
            Duration ttl = ipLocale.resolved() ? RESOLVED_TTL : UNRESOLVED_TTL;
            stringRedisTemplate.opsForValue().set(cacheKey(ip), value, ttl);
        } catch (Exception ex) {
            log.warn("写入 IP 归属地缓存失败，ip：{}", ip, ex);
        }
    }

    private String cacheKey(String ip) {
        return String.format(CACHE_KEY_TEMPLATE, ip);
    }
}
