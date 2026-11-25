package com.cdy.shortlink.project.toolkit;

import cn.hutool.core.util.StrUtil;
import com.cdy.shortlink.project.common.convention.exception.ClientException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 外链安全校验
 * <p>
 * 服务端会主动去请求用户提交的地址（抓标题、抓图标），如果不做校验，
 * 这个能力就会被拿去打内网，也就是典型的 SSRF：{@code ?url=http://169.254.169.254/...}、
 * {@code ?url=http://127.0.0.1:3306}、十进制/十六进制 IP 变形、DNS 指向内网 等等。
 * <p>
 * 这里只放行 http/https 的公网地址，其余一律拒绝。
 */
public final class UrlSafetyChecker {

    /**
     * URL 最大长度，避免超长串拖慢解析
     */
    private static final int MAX_URL_LENGTH = 2048;

    /**
     * 只允许这两类协议
     */
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    /**
     * 明显指向本机/内网的域名后缀
     */
    private static final String[] INTERNAL_HOST_SUFFIXES = {".localhost", ".local", ".internal", ".home.arpa"};

    private UrlSafetyChecker() {
    }

    /**
     * 校验目标地址是否可以安全请求，不安全直接抛客户端异常
     *
     * @param url 目标地址
     */
    public static void check(String url) {
        if (StrUtil.isBlank(url)) {
            throw new ClientException("URL 不能为空");
        }
        String targetUrl = url.trim();
        if (targetUrl.length() > MAX_URL_LENGTH) {
            throw new ClientException("URL 长度超出限制");
        }
        URI uri;
        try {
            uri = new URI(targetUrl);
        } catch (URISyntaxException ex) {
            throw new ClientException("URL 格式不合法");
        }

        String scheme = uri.getScheme();
        if (!HTTP_SCHEME.equalsIgnoreCase(scheme) && !HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
            throw new ClientException("仅支持 http/https 协议");
        }
        // 形如 http://127.0.0.1@evil.com 的地址会隐藏真实主机，直接拒绝
        if (StrUtil.isNotBlank(uri.getUserInfo())) {
            throw new ClientException("URL 不允许包含用户信息");
        }

        String host = uri.getHost();
        if (StrUtil.isBlank(host)) {
            throw new ClientException("URL 缺少主机名");
        }
        // IPv6 字面量在 URI 里带方括号，交给 normalizeIp 去掉
        String literalIp = LinkUtil.normalizeIp(host);
        if (LinkUtil.isValidIp(literalIp)) {
            if (LinkUtil.isInternalIp(literalIp)) {
                throw new ClientException("不允许请求内网地址");
            }
            return;
        }

        String lowerHost = host.toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        if ("localhost".equals(lowerHost) || "0.0.0.0".equals(lowerHost)) {
            throw new ClientException("不允许请求内网地址");
        }
        for (String suffix : INTERNAL_HOST_SUFFIXES) {
            if (lowerHost.endsWith(suffix)) {
                throw new ClientException("不允许请求内网地址");
            }
        }
        if (lowerHost.chars().anyMatch(c -> c <= 0x20 || c == '/' || c == '\\' || c == '@' || c == ':')) {
            throw new ClientException("主机名不合法");
        }

        // 域名本身可能解析到内网（含 2130706433 这类十进制 IP 变形），逐个校验解析结果
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(lowerHost);
        } catch (UnknownHostException ex) {
            throw new ClientException("域名无法解析");
        }
        for (InetAddress address : addresses) {
            if (LinkUtil.isInternalIp(address.getHostAddress())) {
                throw new ClientException("不允许请求内网地址");
            }
        }
    }
}
