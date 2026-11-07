package com.kk.navigator.controller;

import com.kk.navigator.dao.SiteInfoDao;
import com.kk.navigator.entity.R;
import com.kk.navigator.entity.SiteInfo;
import com.kk.navigator.util.IconFetcher;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

@RestController
@Slf4j
@RequestMapping("/api")
public class HomeController {
    private static final int SPEEDTEST_TIMEOUT_MS = 5000;
    private static final long MAX_UPLOAD_SIZE = 5L * 1024 * 1024;
    /** 允许上传的图片类型 -> 保存时使用的扩展名（不使用原始文件名的扩展名，防止上传可执行内容） */
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "image/x-icon", ".ico",
            "image/vnd.microsoft.icon", ".ico"
    );

    @Resource
    private SiteInfoDao siteInfoDao;
    @Resource
    private IconFetcher iconFetcher;
    @Value("${image.path}")
    private String imagePath;

    private final ExecutorService speedtestPool = Executors.newFixedThreadPool(8);
    private final AtomicBoolean speedtestRunning = new AtomicBoolean(false);

    /**
     * 兼容旧版本数据：为没有排序号的站点按 id 补齐排序号
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initSortOrder() {
        List<SiteInfo> sites = findAllSorted();
        // 旧版本升级或之前获取失败的站点，启动后在后台自动获取图标
        sites.stream()
                .filter(s -> !StringUtils.hasText(s.getIcon()))
                .forEach(s -> CompletableFuture.runAsync(() -> autoIcon(s), speedtestPool));
        cleanupIcons();
        if (sites.stream().noneMatch(s -> s.getSortOrder() == null)) {
            return;
        }
        for (int i = 0; i < sites.size(); i++) {
            siteInfoDao.updateSortOrder(sites.get(i).getId(), i);
        }
        log.info("initialized sort order for {} sites", sites.size());
    }

    @PreDestroy
    public void shutdown() {
        speedtestPool.shutdownNow();
    }

    private List<SiteInfo> findAllSorted() {
        return StreamSupport.stream(siteInfoDao.findAll().spliterator(), false)
                .sorted(Comparator.comparing(SiteInfo::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SiteInfo::getId))
                .toList();
    }

    @RequestMapping("/all")
    public R getAll() {
        return R.success("success", findAllSorted());
    }

    @RequestMapping("/select")
    public R getSiteInfoById(@RequestBody SiteInfo siteInfo) {
        Optional<SiteInfo> data = siteInfoDao.findById(siteInfo.getId());
        return data.<R>map(info -> R.success("success", info)).orElseGet(() -> R.fail("无法找到对应站点"));
    }

    @RequestMapping("/add")
    public R addSiteInfo(@RequestBody SiteInfo siteInfo) {
        String error = validate(siteInfo);
        if (error != null) {
            return R.fail(error);
        }
        boolean isNew = siteInfo.getId() == null;
        if (isNew) {
            siteInfo.setStatus(SiteInfo.STATUS_UNTESTED);
            if (siteInfo.getSortOrder() == null) {
                siteInfo.setSortOrder(nextSortOrder());
            }
        }
        SiteInfo saved = siteInfoDao.save(siteInfo);
        CompletableFuture.runAsync(() -> testSite(saved), speedtestPool);
        if (!StringUtils.hasText(saved.getIcon())) {
            CompletableFuture.runAsync(() -> autoIcon(saved), speedtestPool);
        }
        return R.success(isNew ? "站点添加成功" : "站点保存成功", saved);
    }

    @RequestMapping("/update")
    public R updateSiteInfo(@RequestBody SiteInfo siteInfo) {
        if (siteInfo.getId() == null || !siteInfoDao.existsById(siteInfo.getId())) {
            return R.fail("无法找到站点信息");
        }
        String error = validate(siteInfo);
        if (error != null) {
            return R.fail(error);
        }
        String oldIcon = siteInfoDao.findById(siteInfo.getId()).map(SiteInfo::getIcon).orElse(null);
        SiteInfo saved = siteInfoDao.save(siteInfo);
        if (oldIcon != null && !oldIcon.equals(saved.getIcon())) {
            deleteIconIfUnused(oldIcon);
        }
        return R.success("更新站点成功", saved);
    }

    @RequestMapping("/upload")
    public R uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail("文件为空");
        }
        String extension = IMAGE_TYPES.get(String.valueOf(file.getContentType()).toLowerCase());
        if (extension == null) {
            return R.fail("不支持的文件类型，仅支持 png/jpg/gif/webp/ico");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            return R.fail("图片不能超过 5MB");
        }

        try {
            Path uploadDir = Paths.get(imagePath);
            Files.createDirectories(uploadDir);
            String uniqueFilename = UUID.randomUUID() + extension;
            Files.copy(file.getInputStream(), uploadDir.resolve(uniqueFilename), StandardCopyOption.REPLACE_EXISTING);
            log.info("上传成功：{}", uniqueFilename);
            return R.success("上传成功", uniqueFilename);
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage());
            return R.error("文件保存失败");
        }
    }

    @RequestMapping({"/delete", "/del"})
    public R delSiteInfo(@RequestBody SiteInfo siteInfo) {
        if (siteInfo.getId() == null || !siteInfoDao.existsById(siteInfo.getId())) {
            return R.fail("无法找到站点信息");
        }
        String icon = siteInfoDao.findById(siteInfo.getId()).map(SiteInfo::getIcon).orElse(null);
        siteInfoDao.deleteById(siteInfo.getId());
        deleteIconIfUnused(icon);
        return R.success("站点删除成功");
    }

    @RequestMapping("/deleteAll")
    public R deleteAll() {
        siteInfoDao.deleteAll();
        iconFetcher.cleanup(Set.of(), 0);
        return R.success("删除所有站点信息成功");
    }

    @RequestMapping("/updateImage")
    public R updateImage(@RequestBody SiteInfo siteInfo) {
        SiteInfo siteInfoById = siteInfoDao.findSiteInfoById(siteInfo.getId());
        if (siteInfoById == null) {
            return R.fail("无法找到站点信息");
        }
        siteInfoById.setIcon(siteInfo.getIcon());
        siteInfoDao.save(siteInfoById);
        return R.success("更新图标成功");
    }

    /**
     * 保存拖拽后的排序，参数为按顺序排列的站点 id 列表
     */
    @RequestMapping("/sort")
    public R sort(@RequestBody List<Integer> ids) {
        for (int i = 0; i < ids.size(); i++) {
            siteInfoDao.updateSortOrder(ids.get(i), i);
        }
        return R.success("排序已保存");
    }

    /**
     * 导出备份：本地图标会转为 data URL 一并导出，导入到其它实例时图标不会丢失
     */
    @RequestMapping("/export")
    public R export() {
        List<SiteInfo> sites = findAllSorted();
        sites.forEach(s -> s.setIcon(iconFetcher.toDataUrl(s.getIcon())));
        return R.success("success", sites);
    }

    /**
     * 导入站点（追加），会忽略导入数据中的 id
     */
    @RequestMapping("/import")
    public R importSites(@RequestBody List<SiteInfo> sites) {
        int order = nextSortOrder();
        List<SiteInfo> toSave = new ArrayList<>();
        for (SiteInfo site : sites) {
            if (validate(site) != null) {
                continue;
            }
            site.setId(null);
            site.setStatus(SiteInfo.STATUS_UNTESTED);
            site.setStatusMsg(null);
            site.setCheckedAt(null);
            site.setSortOrder(order++);
            site.setIcon(importIcon(site.getIcon()));
            toSave.add(site);
        }
        siteInfoDao.saveAll(toSave).forEach(saved -> {
            CompletableFuture.runAsync(() -> testSite(saved), speedtestPool);
            if (!StringUtils.hasText(saved.getIcon())) {
                CompletableFuture.runAsync(() -> autoIcon(saved), speedtestPool);
            }
        });
        return R.success("成功导入 " + toSave.size() + " 个站点", toSave.size());
    }

    /**
     * 由服务端获取指定地址的图标，返回图标路径（不修改站点数据，供编辑表单使用）
     */
    @RequestMapping("/fetchIcon")
    public R fetchIcon(@RequestBody SiteInfo siteInfo) {
        if (siteInfo.getUrl() == null || !siteInfo.getUrl().trim().matches("(?i)^https?://.+")) {
            return R.fail("请先填写正确的站点地址");
        }
        String icon = iconFetcher.fetch(siteInfo.getUrl());
        return icon == null ? R.fail("未能获取到该站点的图标，可手动上传") : R.success("图标获取成功", icon);
    }

    /**
     * 根据地址获取站点标题和图标，用于添加站点时自动填写
     */
    @RequestMapping("/siteMeta")
    public R siteMeta(@RequestBody SiteInfo siteInfo) {
        if (siteInfo.getUrl() == null || !siteInfo.getUrl().trim().matches("(?i)^https?://.+")) {
            return R.fail("请先填写正确的站点地址");
        }
        IconFetcher.SiteMeta meta = iconFetcher.fetchMeta(siteInfo.getUrl());
        Map<String, String> data = new java.util.HashMap<>();
        data.put("title", meta.title());
        data.put("icon", meta.icon());
        return R.success("success", data);
    }

    /**
     * 为所有未手动上传图标的站点重新获取图标
     */
    @RequestMapping("/refreshIcons")
    public R refreshIcons() {
        List<SiteInfo> targets = findAllSorted().stream()
                .filter(s -> !StringUtils.hasText(s.getIcon()) || IconFetcher.isAuto(s.getIcon()))
                .toList();
        List<CompletableFuture<Boolean>> futures = targets.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> autoIcon(s), speedtestPool))
                .toList();
        long ok = futures.stream().filter(CompletableFuture::join).count();
        return R.success("已更新 " + ok + "/" + targets.size() + " 个站点的图标", findAllSorted());
    }

    @RequestMapping("/speedtest")
    public R speedtest() {
        runSpeedtest();
        return R.success("测速完成", findAllSorted());
    }

    /**
     * 测试单个站点
     */
    @RequestMapping("/speedtest/one")
    public R speedtestOne(@RequestBody SiteInfo siteInfo) {
        Optional<SiteInfo> site = siteInfo.getId() == null ? Optional.empty() : siteInfoDao.findById(siteInfo.getId());
        if (site.isEmpty()) {
            return R.fail("无法找到站点信息");
        }
        testSite(site.get());
        return R.success("测速完成", siteInfoDao.findById(siteInfo.getId()).orElse(null));
    }

    /** 每天清理一次未被引用的图标文件 */
    @Scheduled(initialDelay = 60 * 60 * 1000, fixedRate = 24 * 60 * 60 * 1000)
    public void cleanupIcons() {
        Set<String> referenced = new java.util.HashSet<>();
        siteInfoDao.findAll().forEach(s -> {
            if (s.getIcon() != null) {
                referenced.add(s.getIcon());
            }
        });
        // 保留 1 小时内的新文件：可能是表单中刚上传/获取、尚未保存的图标
        iconFetcher.cleanup(referenced, 60 * 60 * 1000);
    }

    @Scheduled(initialDelay = 10 * 1000, fixedRate = 1000 * 60 * 10)
    public void scheduleTask() {
        runSpeedtest();
    }

    private void runSpeedtest() {
        if (!speedtestRunning.compareAndSet(false, true)) {
            log.info("speedtest is already running, skip");
            return;
        }
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            siteInfoDao.findAll().forEach(site -> futures.add(CompletableFuture.runAsync(() -> testSite(site), speedtestPool)));
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            speedtestRunning.set(false);
        }
    }

    /**
     * 通过建立 TCP 连接测量延迟。ICMP(ping) 在容器内通常没有权限，结果不可靠，
     * 而 TCP 连接站点的 http/https 端口更贴近真实访问体验。
     */
    private void testSite(SiteInfo site) {
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
                socket.connect(address, SPEEDTEST_TIMEOUT_MS);
                status = Math.max(1, (System.nanoTime() - start) / 1_000_000);
            }
            log.info("speed: {} delay {}ms", host, status);
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
            log.warn("speed: {} {}", host, msg);
        }
        siteInfoDao.updateStatus(site.getId(), status, msg, LocalDateTime.now());
    }

    /** 自动获取图标并保存，返回是否成功 */
    private boolean autoIcon(SiteInfo site) {
        String icon = iconFetcher.fetch(site.getUrl());
        if (icon == null) {
            return false;
        }
        String old = site.getIcon();
        siteInfoDao.updateIcon(site.getId(), icon);
        deleteIconIfUnused(old);
        return true;
    }

    /** 删除不再被任何站点使用的本地图标 */
    private void deleteIconIfUnused(String icon) {
        if (IconFetcher.isLocal(icon) && siteInfoDao.countByIcon(icon) == 0) {
            iconFetcher.delete(icon);
        }
    }

    /** 处理导入的图标：data URL 保存为本地文件；引用的本地文件不存在时置空，之后自动获取 */
    private String importIcon(String icon) {
        if (!StringUtils.hasText(icon)) {
            return null;
        }
        if (icon.startsWith("data:")) {
            return iconFetcher.saveDataUrl(icon);
        }
        if (icon.startsWith("/images/") && !iconFetcher.exists(icon)) {
            return null;
        }
        return icon;
    }

    private int nextSortOrder() {
        return findAllSorted().stream()
                .map(SiteInfo::getSortOrder)
                .filter(o -> o != null)
                .max(Integer::compare)
                .map(o -> o + 1)
                .orElse(0);
    }

    private String validate(SiteInfo siteInfo) {
        if (!StringUtils.hasText(siteInfo.getTitle())) {
            return "站点名称不能为空";
        }
        String url = siteInfo.getUrl() == null ? "" : siteInfo.getUrl().trim();
        if (!url.matches("(?i)^https?://.+")) {
            return "请输入完整的站点地址，须以 http:// 或 https:// 开头";
        }
        try {
            if (URI.create(url).getHost() == null) {
                return "站点地址格式不正确";
            }
        } catch (IllegalArgumentException e) {
            return "站点地址格式不正确";
        }
        siteInfo.setUrl(url);
        siteInfo.setTitle(siteInfo.getTitle().trim());
        return null;
    }
}
