package com.cdy.shortlink.project.toolkit;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.cdy.shortlink.project.common.constant.ShortLinkConstant;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

/**
 * 链接工具类
 */
public class LinkUtil {

    /**
     * 代理层透传客户端 IP 的请求头（X-Forwarded-For 单独处理，它是逗号分隔的链路）
     */
    private static final String[] PROXY_IP_HEADERS = {"X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};

    /**
     * 获取链接缓存有效时间
     *
     * @param validDate 链接有效时间
     * @return 链接缓存有效时间
     */
    public static long getLinkCacheValidDate(Date validDate) {
        return Optional.ofNullable(validDate)
                .map(each -> DateUtil.between(new Date(), each, DateUnit.MS))
                .orElse(ShortLinkConstant.DEFAULT_CACHE_VALID_TIME);

    }

    /**
     * 获取请求的客户端 IP
     * <p>
     * 代理链路里 X-Forwarded-For 的格式是 {@code 客户端IP, 一级代理IP, 二级代理IP}，
     * 直接把这整串拿去查归属地必然失败，所以这里按逗号拆开逐个校验，
     * 取第一个“合法且不是内网”的地址作为客户端 IP；全链路都是内网时退回第一个合法地址，
     * 让上层按内网处理，而不是拿一串拼接值去请求第三方接口。
     *
     * @param request 请求
     * @return 客户端 IP；解析不出来时返回空串
     */
    public static String getIp(HttpServletRequest request) {
        String clientIp = findClientIp(request.getHeader("X-Forwarded-For"));
        if (StrUtil.isNotBlank(clientIp)) {
            return clientIp;
        }
        for (String header : PROXY_IP_HEADERS) {
            String candidate = normalizeIp(request.getHeader(header));
            if (isValidIp(candidate)) {
                return candidate;
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    /**
     * 从 X-Forwarded-For 中挑出客户端 IP
     *
     * @param forwardedFor X-Forwarded-For 原始值，可能为 {@code 1.2.3.4, 10.0.0.1}
     * @return 第一个合法的公网地址；没有公网地址时返回第一个合法地址；都不合法返回 null
     */
    private static String findClientIp(String forwardedFor) {
        if (StrUtil.isBlank(forwardedFor)) {
            return null;
        }
        String firstValidIp = null;
        for (String segment : StrUtil.split(forwardedFor, ',')) {
            String candidate = normalizeIp(segment);
            if (!isValidIp(candidate)) {
                continue;
            }
            if (firstValidIp == null) {
                firstValidIp = candidate;
            }
            if (!isInternalIp(candidate)) {
                return candidate;
            }
        }
        return firstValidIp;
    }

    /**
     * 规整 IP 文本：去空白、去引号、去端口、去掉 unknown 这类占位值
     *
     * @param raw 原始值
     * @return 规整后的 IP 文本，无法规整时返回空串
     */
    public static String normalizeIp(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        String ip = raw.trim();
        if (ip.length() > 1 && ip.startsWith("\"") && ip.endsWith("\"")) {
            ip = ip.substring(1, ip.length() - 1).trim();
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            return "";
        }
        // IPv6 带端口：[::1]:1234
        if (ip.startsWith("[")) {
            int end = ip.indexOf(']');
            return end > 0 ? ip.substring(1, end) : "";
        }
        // IPv4 带端口：10.0.0.1:1234；IPv6 本身含冒号，不能按冒号切
        int colon = ip.indexOf(':');
        if (colon > 0 && ip.indexOf(':', colon + 1) < 0 && isValidIpv4(ip.substring(0, colon))) {
            return ip.substring(0, colon);
        }
        return ip;
    }

    /**
     * 是否为合法的 IPv4 / IPv6 文本
     *
     * @param ip IP 文本
     * @return 是否合法
     */
    public static boolean isValidIp(String ip) {
        return isValidIpv4(ip) || isValidIpv6(ip);
    }

    /**
     * 是否为内网 / 回环 / 链路本地地址
     * <p>
     * 这类地址查不到归属地，调用第三方接口纯属浪费配额，应该在发请求之前就短路掉。
     *
     * @param ip IP 文本
     * @return 是否为内网地址
     */
    public static boolean isInternalIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return true;
        }
        if (!isValidIp(ip)) {
            // 解析不了的地址一律按不安全处理
            return true;
        }
        if (isValidIpv6(ip)) {
            // IPv6 形态太多（::1、0:0:0:0:0:0:0:1、::ffff:127.0.0.1），交给 JDK 判断，
            // 再补上 JDK 不覆盖的唯一本地地址段 fc00::/7
            String lower = ip.toLowerCase(Locale.ROOT);
            if (lower.startsWith("fc") || lower.startsWith("fd")) {
                return true;
            }
            try {
                InetAddress address = InetAddress.getByName(ip);
                return address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress();
            } catch (UnknownHostException ex) {
                return true;
            }
        }
        if ("0.0.0.0".equals(ip)
                || ip.startsWith("10.")
                || ip.startsWith("127.")
                || ip.startsWith("192.168.")
                || ip.startsWith("169.254.")) {
            return true;
        }
        // 172.16.0.0 ~ 172.31.255.255
        String[] parts = ip.split("\\.");
        int second = Integer.parseInt(parts[1]);
        return "172".equals(parts[0]) && second >= 16 && second <= 31;
    }

    /**
     * 校验 IPv4：四段，每段 0~255
     */
    private static boolean isValidIpv4(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验 IPv6：只做字面形态校验（含冒号、由十六进制字符和点号组成），够用来区分 IP 和垃圾值
     */
    private static boolean isValidIpv6(String ip) {
        if (StrUtil.isBlank(ip) || ip.indexOf(':') < 0) {
            return false;
        }
        int colonCount = 0;
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            if (c == ':') {
                colonCount++;
                continue;
            }
            boolean hexChar = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == '.';
            if (!hexChar) {
                return false;
            }
        }
        return colonCount >= 2;
    }

    /**
     * 获取用户访问操作系统
     * @param request 请求
     * @return 访问操作系统
     */
    public static String getOs(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.toLowerCase().contains("windows")) {
            return "Windows";
        } else if (userAgent.toLowerCase().contains("mac")) {
            return "Mac OS";
        } else if (userAgent.toLowerCase().contains("linux")) {
            return "Linux";
        } else if (userAgent.toLowerCase().contains("unix")) {
            return "Unix";
        } else if (userAgent.toLowerCase().contains("android")) {
            return "Android";
        } else if (userAgent.toLowerCase().contains("iphone")) {
            return "iOS";
        } else {
            return "Unknown";
        }
    }

    /**
     * 获取用户访问浏览器
     * @param request 请求
     * @return 访问浏览器
     */
    public static String getBrowser(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.toLowerCase().contains("edg")) {
            return "Microsoft Edge";
        } else if (userAgent.toLowerCase().contains("chrome")) {
            return "Google Chrome";
        } else if (userAgent.toLowerCase().contains("firefox")) {
            return "Mozilla Firefox";
        } else if (userAgent.toLowerCase().contains("safari")) {
            return "Apple Safari";
        } else if (userAgent.toLowerCase().contains("opera")) {
            return "Opera";
        } else if (userAgent.toLowerCase().contains("msie") || userAgent.toLowerCase().contains("trident")) {
            return "Internet Explorer";
        } else {
            return "Unknown";
        }
    }

    /**
     * 获取用户访问设备
     * @param request 请求
     * @return 访问设备
     */
    public static String getDevice(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.toLowerCase().contains("mobile")) {
            return "Mobile";
        }
        return "PC";
    }

    /**
     * 获取用户访问网络
     * @param request 请求
     * @return 访问设备
     */
    public static String getNetwork(HttpServletRequest request) {
        String actualIp = getIp(request);
        // 这里简单判断IP地址范围，您可能需要更复杂的逻辑
        // 例如，通过调用IP地址库或调用第三方服务来判断网络类型
        return actualIp.startsWith("192.168.") || actualIp.startsWith("10.") ? "WIFI" : "Mobile";
    }
}
