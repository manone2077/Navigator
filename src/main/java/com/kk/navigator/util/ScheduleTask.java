package com.kk.navigator.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpConfig;
import cn.hutool.http.HttpResponse;
import com.kk.navigator.entity.Setting; // 修改引用的类名
import com.kk.navigator.repository.SettingRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.*;

@Component
@Slf4j
public class ScheduleTask {
    @Resource
    private SettingRepository settingRepository;

    @Scheduled(initialDelay = 5000, fixedRate = 5000)
    private void checkin() {
        log.info("checkin");
        Setting setting = settingRepository.findFirstByOrderByLastModifiedDateDesc(); // 修改引用的类名
        if (setting == null) {
            log.error("No configuration found in the database.");
            return;
        }

        HttpConfig httpConfig = new HttpConfig();
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(setting.getProxyHost(), setting.getProxyPort()));
        httpConfig.setProxy(proxy);

        try {
            HttpResponse resp = HttpRequest.get(setting.getCheckinUrl())
                    .setConfig(httpConfig)
                    .header("User-Agent", setting.getUa())
                    .header("authorization", setting.getAuthToken())
                    .header("cookie", setting.getCookie())
                    .header("accept", setting.getAccept())
                    .header("accept-encoding", setting.getAcceptEncoding())
                    .header("Host", getHost(setting.getCheckinUrl()))
                    .header("referer", getFullPath(setting.getCheckinUrl()))
                    .setConnectionTimeout(setting.getTimeout())
                    .execute();
            log.info("status code:{}", resp.getStatus());

        } catch (Exception exception) {
            log.error(exception.getMessage());
        }
    }

    private String getHost(String urlStr) throws MalformedURLException {
        URL url = new URL(urlStr);
        return url.getHost();
    }

    private String getFullPath(String urlStr) throws MalformedURLException {
        URL url = new URL(urlStr);
        String protocol = url.getProtocol();
        String host = url.getHost();
        String path = url.getPath();
        int port = url.getPort();

        String fullPath;
        if (port == -1) {
            fullPath = protocol + "://" + host + path;
        } else {
            fullPath = protocol + "://" + host + ":" + port + path;
        }

        log.info("getFullPath:{}", fullPath);
        return fullPath;
    }
}