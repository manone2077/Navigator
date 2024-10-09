package com.kk.navigator.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpConfig;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.*;

@Component
@Slf4j
public class ScheduleTask {
    private String checkinUrl="https://www.baidu.com/?q=java+URL&PC=U316&FORM=CHROMN";
    private String ua="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    private String accept="text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";
    private String accept_encoding="gzip, deflate, br, zstd";
    private int timtout=10000;

    private String authorization="xxx";
    private String cookie="name=admin";

    private String proxyHost="10.19.9.30";
    private int proxyPort=7890;
    private Proxy.Type proxyType=Proxy.Type.HTTP;


    @Scheduled(initialDelay = 5000,fixedRate = 5000)
    private void checkin() {
        log.info("checkin");
        HttpConfig config = new HttpConfig();
        Proxy proxy = new Proxy(proxyType, new InetSocketAddress(proxyHost, proxyPort));
        config.setProxy(proxy);

        try {
            HttpResponse resp = HttpRequest.get(checkinUrl)
                    .setConfig(config)
                    .header("User-Agent", ua)
                    .header("authorization", authorization)
                    .header("cookie", cookie)
                    .header("accept", accept)
                    .header("accept-encoding", accept_encoding)
                    .header("Host", getHost())
                    .header("referer", getFullPath())
                    .setConnectionTimeout(timtout)
                    .execute();
            log.info("status code:{}", resp.getStatus());

        }catch (Exception exception){
            log.error(exception.getMessage());
        }


    }

    private String getHost() throws MalformedURLException {
        URL url = new URL(checkinUrl);
        return url.getHost();
    }

    private String getFullPath() throws MalformedURLException {
        URL url=new URL(checkinUrl);
        String protocol=url.getProtocol();
        String host=url.getHost();
        String path=url.getPath();
        int port = url.getPort();

        String fullPath;
        if (port == -1) {
            fullPath=protocol+"://"+host+path;
        }else{
            fullPath=protocol+"://"+host+":"+port+path;
        }

        log.info("getFullPath:{}",fullPath);
        return fullPath;
    }
}
