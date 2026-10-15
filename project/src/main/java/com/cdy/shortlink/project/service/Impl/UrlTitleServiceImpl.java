package com.cdy.shortlink.project.service.Impl;

import com.cdy.shortlink.project.service.UrlTitleService;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * URL标题接口实现层
 */
@Service
public class UrlTitleServiceImpl implements UrlTitleService {

    /**
     * 根据URL获取对应网站的标题
     * @param url 目标URL
     * @return 目标网站的标题
     */
    @SneakyThrows
    @Override
    public String getTitleByUrl(String url) {
        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Document document = Jsoup.connect(url).get();
            return document.title();
        } else {
            return "Error while fetching title.";
        }
    }

}
