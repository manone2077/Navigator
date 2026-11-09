package com.kk.navigator.service;

import com.kk.navigator.dao.SiteInfoDao;
import com.kk.navigator.entity.SiteInfo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 站点测速。通过建立 TCP 连接测量延迟：ICMP(ping) 在容器内通常没有权限，结果不可靠，
 * 而 TCP 连接站点的 http/https 端口更贴近真实访问体验。
 * <p>
 * 使用独立的线程池，避免与获取图标等耗时任务互相阻塞。
 */
@Slf4j
@Service
public class SpeedtestService {
    private static final int TIMEOUT_MS = 5000;

    private final SiteInfoDao siteInfoDao;
    private final ExecutorService pool = Executors.newFixedThreadPool(8, named("speedtest"));
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SpeedtestService(SiteInfoDao siteInfoDao) {
        this.siteInfoDao = siteInfoDao;
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }

    /** 后台每 10 分钟自动测速一次 */
    @Scheduled(initialDelay = 10 * 1000, fixedRate = 10 * 60 * 1000)
    public void scheduled() {
        testAll();
    }

    /** 测试所有站点并等待完成；已有测速在进行时直接返回 */
    public void testAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("speedtest is already running, skip");
            return;
        }
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            siteInfoDao.findAll().forEach(site -> futures.add(CompletableFuture.runAsync(() -> test(site), pool)));
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            running.set(false);
        }
    }

    /** 后台测试单个站点（添加、导入站点后调用） */
    public void testAsync(SiteInfo site) {
        CompletableFuture.runAsync(() -> test(site), pool);
    }

    /** 测试单个站点并保存结果 */
    public void test(SiteInfo site) {
        long status;
        String msg = null;
        String host = null;
        try {
            URI uri = URI.create(site.getUrl().trim());
            host = uri.getHost();
            if (host == null) {
                throw new UnknownHostException(site.getUrl());
            }
            int port = uri.getPort() != -1 ? uri.getPort() : ("http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443);
            InetSocketAddress address = new InetSocketAddress(host, port);
            if (address.isUnresolved()) {
                throw new UnknownHostException(host);
            }
            try (Socket socket = new Socket()) {
                long start = System.nanoTime();
                socket.connect(address, TIMEOUT_MS);
                status = Math.max(1, (System.nanoTime() - start) / 1_000_000);
            }
            log.debug("speed: {} delay {}ms", host, status);
        } catch (SocketTimeoutException e) {
            status = SiteInfo.STATUS_TIMEOUT;
            msg = "连接超时（5 秒内无响应）";
        } catch (UnknownHostException e) {
            status = SiteInfo.STATUS_UNREACHABLE;
            msg = "域名无法解析";
        } catch (ConnectException e) {
            status = SiteInfo.STATUS_UNREACHABLE;
            msg = "连接被拒绝（端口未开放或服务未启动）";
        } catch (NoRouteToHostException e) {
            status = SiteInfo.STATUS_UNREACHABLE;
            msg = "无法路由到主机（主机离线或网络不通）";
        } catch (Exception e) {
            status = SiteInfo.STATUS_UNREACHABLE;
            msg = e.getMessage();
        }
        if (msg != null) {
            log.info("speed: {} {}", host, msg);
        }
        siteInfoDao.updateStatus(site.getId(), status, msg, LocalDateTime.now());
    }

    static java.util.concurrent.ThreadFactory named(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
