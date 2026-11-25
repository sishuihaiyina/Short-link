package com.cdy.shortlink.project.service.Impl;

import cn.hutool.core.util.StrUtil;
import com.cdy.shortlink.project.common.convention.exception.ClientException;
import com.cdy.shortlink.project.service.UrlTitleService;
import com.cdy.shortlink.project.toolkit.UrlSafetyChecker;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * URL标题接口实现层
 * <p>
 * 安全说明：这个接口会让服务端去请求用户提交的地址，属于 SSRF 高发点。
 * 所以每次请求前都过一遍 {@link UrlSafetyChecker}，且手动跟随重定向 ——
 * 每一跳都重新校验，否则一个 302 就能把请求引到内网。
 */
@Slf4j
@Service
public class UrlTitleServiceImpl implements UrlTitleService {

    /**
     * 连接 + 读取超时，避免慢站点占住请求线程
     */
    private static final int TIMEOUT_MILLIS = 3000;

    /**
     * 只读取响应体前 512KB，标题在 head 里，没必要把整页拉下来
     */
    private static final int MAX_BODY_SIZE = 512 * 1024;

    /**
     * 最多跟随 3 次跳转
     */
    private static final int MAX_REDIRECTS = 3;

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ShortLinkTitleFetcher/1.0)";

    private static final String FETCH_FAILED = "Error while fetching title.";

    /**
     * 根据URL获取对应网站的标题
     *
     * @param url 目标URL
     * @return 目标网站的标题；抓不到时返回占位文案
     */
    @Override
    public String getTitleByUrl(String url) {
        String currentUrl = StrUtil.trim(url);
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            // 每一跳都校验：重定向目标同样可能是内网地址
            UrlSafetyChecker.check(currentUrl);

            Connection.Response response;
            try {
                response = Jsoup.connect(currentUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MILLIS)
                        .maxBodySize(MAX_BODY_SIZE)
                        .ignoreContentType(true)
                        .followRedirects(false)
                        .execute();
            } catch (Exception ex) {
                log.warn("获取网页标题失败，url：{}", currentUrl, ex);
                return FETCH_FAILED;
            }

            int statusCode = response.statusCode();
            if (isRedirect(statusCode)) {
                String location = response.header("Location");
                if (StrUtil.isBlank(location)) {
                    return FETCH_FAILED;
                }
                currentUrl = resolveLocation(currentUrl, location);
                continue;
            }
            if (statusCode < 200 || statusCode >= 300) {
                return FETCH_FAILED;
            }
            return Jsoup.parse(response.body(), currentUrl).title();
        }
        log.warn("网页重定向次数过多，url：{}", url);
        return FETCH_FAILED;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    /**
     * 把 Location（可能是相对路径）解析成绝对地址
     */
    private static String resolveLocation(String currentUrl, String location) {
        try {
            return URI.create(currentUrl).resolve(location.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new ClientException("链接跳转地址不合法");
        }
    }
}
