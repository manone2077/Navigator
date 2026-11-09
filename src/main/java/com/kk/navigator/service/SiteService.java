package com.kk.navigator.service;

import com.kk.navigator.controller.dto.SiteForm;
import com.kk.navigator.dao.SiteInfoDao;
import com.kk.navigator.entity.BizException;
import com.kk.navigator.entity.SiteInfo;
import com.kk.navigator.util.IconFetcher;
import com.kk.navigator.util.IconStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;

/**
 * 站点管理：增删改查、排序、导入导出，以及站点图标的获取与清理
 */
@Slf4j
@Service
public class SiteService {
    private static final long MAX_UPLOAD_SIZE = 5L * 1024 * 1024;
    /** 允许上传的图片类型 -> 保存时使用的扩展名 */
    private static final Map<String, String> UPLOAD_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "image/x-icon", ".ico",
            "image/vnd.microsoft.icon", ".ico"
    );

    private final SiteInfoDao siteInfoDao;
    private final IconStore iconStore;
    private final IconFetcher iconFetcher;
    private final SpeedtestService speedtestService;
    /** 获取图标可能对每个站点尝试多个地址，使用独立线程池，避免占用测速线程 */
    private final ExecutorService iconPool = Executors.newFixedThreadPool(4, SpeedtestService.named("icon"));

    public SiteService(SiteInfoDao siteInfoDao, IconStore iconStore, IconFetcher iconFetcher,
                       SpeedtestService speedtestService) {
        this.siteInfoDao = siteInfoDao;
        this.iconStore = iconStore;
        this.iconFetcher = iconFetcher;
        this.speedtestService = speedtestService;
    }

    @PreDestroy
    public void shutdown() {
        iconPool.shutdownNow();
    }

    /**
     * 启动时：为旧版本数据补齐排序号，为没有图标的站点在后台获取图标，清理未使用的图标文件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        List<SiteInfo> sites = findAll();
        sites.stream().filter(s -> !StringUtils.hasText(s.getIcon())).forEach(this::autoIconAsync);
        cleanupIcons();
        if (sites.stream().anyMatch(s -> s.getSortOrder() == null)) {
            for (int i = 0; i < sites.size(); i++) {
                siteInfoDao.updateSortOrder(sites.get(i).getId(), i);
            }
            log.info("initialized sort order for {} sites", sites.size());
        }
    }

    public List<SiteInfo> findAll() {
        return StreamSupport.stream(siteInfoDao.findAll().spliterator(), false)
                .sorted(Comparator.comparing(SiteInfo::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SiteInfo::getId))
                .toList();
    }

    public SiteInfo get(Integer id) {
        return id == null ? null : siteInfoDao.findById(id).orElse(null);
    }

    private SiteInfo require(Integer id) {
        SiteInfo site = get(id);
        if (site == null) {
            throw BizException.notFound();
        }
        return site;
    }

    public SiteInfo add(SiteForm form) {
        SiteInfo site = new SiteInfo();
        apply(site, form, false);
        site.setStatus(SiteInfo.STATUS_UNTESTED);
        site.setSortOrder(nextSortOrder());
        SiteInfo saved = siteInfoDao.save(site);
        afterCreate(saved);
        return saved;
    }

    public SiteInfo update(SiteForm form) {
        SiteInfo site = require(form.id());
        String oldIcon = site.getIcon();
        String oldUrl = site.getUrl();
        apply(site, form, false);
        SiteInfo saved = siteInfoDao.save(site);
        if (!Objects.equals(oldIcon, saved.getIcon())) {
            deleteIconIfUnused(oldIcon);
        }
        if (!Objects.equals(oldUrl, saved.getUrl())) {
            speedtestService.testAsync(saved);
        }
        return saved;
    }

    public void delete(Integer id) {
        SiteInfo site = require(id);
        siteInfoDao.deleteById(site.getId());
        deleteIconIfUnused(site.getIcon());
    }

    public void deleteAll() {
        siteInfoDao.deleteAll();
        iconStore.cleanup(Set.of(), 0);
    }

    public void updateIcon(Integer id, String icon) {
        SiteInfo site = require(id);
        String old = site.getIcon();
        site.setIcon(checkIcon(icon, false));
        siteInfoDao.save(site);
        if (!Objects.equals(old, site.getIcon())) {
            deleteIconIfUnused(old);
        }
    }

    /** 保存拖拽后的排序，参数为按顺序排列的站点 id 列表 */
    public void sort(List<Integer> ids) {
        for (int i = 0; i < ids.size(); i++) {
            siteInfoDao.updateSortOrder(ids.get(i), i);
        }
    }

    /** 导出备份：本地图标转为 data URL 一并导出，导入到其它实例时图标不会丢失 */
    public List<SiteInfo> export() {
        List<SiteInfo> sites = findAll();
        sites.forEach(s -> s.setIcon(iconStore.toDataUrl(s.getIcon())));
        return sites;
    }

    /** 导入站点（追加），无效的条目会被跳过，返回导入数量 */
    public int importSites(List<SiteForm> forms) {
        int order = nextSortOrder();
        List<SiteInfo> toSave = new ArrayList<>();
        for (SiteForm form : forms) {
            SiteInfo site = new SiteInfo();
            try {
                apply(site, form, true);
            } catch (BizException e) {
                continue;
            }
            site.setStatus(SiteInfo.STATUS_UNTESTED);
            site.setSortOrder(order++);
            toSave.add(site);
        }
        siteInfoDao.saveAll(toSave).forEach(this::afterCreate);
        return toSave.size();
    }

    public String upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException("文件为空");
        }
        String ext = UPLOAD_TYPES.get(String.valueOf(file.getContentType()).toLowerCase());
        if (ext == null) {
            throw new BizException("不支持的文件类型，仅支持 png/jpg/gif/webp/ico");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new BizException("图片不能超过 5MB");
        }
        try {
            String icon = iconStore.saveUpload(file.getInputStream(), ext);
            log.info("uploaded icon {}", icon);
            return icon;
        } catch (IOException e) {
            log.error("save upload failed: {}", e.getMessage());
            throw new BizException(500, "文件保存失败");
        }
    }

    /** 根据地址获取站点标题和图标，用于添加站点时自动填写 */
    public IconFetcher.SiteMeta fetchMeta(String url) {
        URI uri = parseUrl(url);
        checkFetchAllowed(uri);
        return iconFetcher.fetchMeta(uri.toString());
    }

    public String fetchIcon(String url) {
        String icon = fetchMeta(url).icon();
        if (icon == null) {
            throw new BizException("未能获取到该站点的图标，可手动上传");
        }
        return icon;
    }

    /** 为所有未手动上传图标的站点重新获取图标，返回成功数量和总数 */
    public int[] refreshIcons() {
        List<SiteInfo> targets = findAll().stream()
                .filter(s -> !StringUtils.hasText(s.getIcon()) || IconStore.isAuto(s.getIcon()))
                .toList();
        long ok = targets.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> autoIcon(s), iconPool))
                .toList().stream()
                .filter(CompletableFuture::join)
                .count();
        return new int[]{(int) ok, targets.size()};
    }

    /** 每天清理一次未被引用的图标文件 */
    @Scheduled(initialDelay = 60 * 60 * 1000, fixedRate = 24 * 60 * 60 * 1000)
    public void cleanupIcons() {
        Set<String> referenced = new HashSet<>();
        siteInfoDao.findAll().forEach(s -> {
            if (s.getIcon() != null) {
                referenced.add(s.getIcon());
            }
        });
        // 保留 1 小时内的新文件：可能是表单中刚上传/获取、尚未保存的图标
        iconStore.cleanup(referenced, 60 * 60 * 1000);
    }

    private void afterCreate(SiteInfo site) {
        speedtestService.testAsync(site);
        if (!StringUtils.hasText(site.getIcon())) {
            autoIconAsync(site);
        }
    }

    private void autoIconAsync(SiteInfo site) {
        CompletableFuture.runAsync(() -> autoIcon(site), iconPool);
    }

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

    private void deleteIconIfUnused(String icon) {
        if (IconStore.isLocal(icon) && siteInfoDao.countByIcon(icon) == 0) {
            iconStore.delete(icon);
        }
    }

    /** 把表单中允许修改的字段写入站点，校验失败时抛出 {@link BizException} */
    private void apply(SiteInfo site, SiteForm form, boolean importing) {
        if (form == null || !StringUtils.hasText(form.title())) {
            throw new BizException("站点名称不能为空");
        }
        URI uri = parseUrl(form.url());
        site.setTitle(limit(form.title().trim(), 100));
        site.setUrl(uri.toString());
        site.setContent(limit(form.content(), 500));
        site.setDescription(limit(form.description(), 500));
        site.setCheckinUrl(limit(form.checkinUrl(), 500));
        site.setIcon(checkIcon(form.icon(), importing));
    }

    /**
     * 校验图标：允许为空、本地已上传的图标、http(s) 外链；导入时额外允许 data URL（会保存为本地文件）。
     * 导入备份中引用的本地图标在本实例不存在时置空，之后会自动获取。
     */
    private String checkIcon(String icon, boolean importing) {
        if (!StringUtils.hasText(icon)) {
            return null;
        }
        String v = icon.trim();
        if (importing && v.startsWith("data:")) {
            return iconStore.saveDataUrl(v);
        }
        if (IconStore.isLocal(v)) {
            return importing && !iconStore.exists(v) ? null : v;
        }
        if (v.matches("(?i)^https?://\\S+") && v.length() <= 1000) {
            return v;
        }
        if (importing) {
            return null;
        }
        throw new BizException("图标地址不正确");
    }

    private static URI parseUrl(String url) {
        String v = url == null ? "" : url.trim();
        if (!v.matches("(?i)^https?://\\S+")) {
            throw new BizException("请输入完整的站点地址，须以 http:// 或 https:// 开头");
        }
        try {
            URI uri = URI.create(v);
            if (uri.getHost() == null) {
                throw new BizException("站点地址格式不正确");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new BizException("站点地址格式不正确");
        }
    }

    private static void checkFetchAllowed(URI uri) {
        try {
            IconFetcher.checkAllowed(uri);
        } catch (IOException e) {
            throw new BizException(e.getMessage());
        }
    }

    private int nextSortOrder() {
        return findAll().stream()
                .map(SiteInfo::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .map(o -> o + 1)
                .orElse(0);
    }

    private static String limit(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
