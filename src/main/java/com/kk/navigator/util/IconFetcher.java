package com.kk.navigator.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 由服务端获取站点图标并保存到本地。
 * <p>
 * 浏览器直接加载 favicon 经常失败：图标不在根路径（如 qBittorrent、Transmission）、
 * 自签名 https 证书（如 Portainer、PVE）、http 页面加载 https 资源等。
 * 服务端解析页面中的 &lt;link rel="icon"&gt;，并忽略证书校验下载图标，可以解决大部分问题。
 */
@Slf4j
@Component
public class IconFetcher {
    /** 自动获取的图标文件名前缀，用于区分用户上传的图标 */
    public static final String AUTO_PREFIX = "auto-";

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_HTML_BYTES = 512 * 1024;
    private static final int MAX_ICON_BYTES = 1024 * 1024;
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern REL_ICON = Pattern.compile("\\brel\\s*=\\s*[\"']?[^\"'>]*icon", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.CASE_INSENSITIVE);

    @Value("${image.path}")
    private String imagePath;

    /**
     * 获取并保存图标
     *
     * @return 图标访问路径（/images/xxx），获取失败返回 null
     */
    public String fetch(String siteUrl) {
        URI base;
        try {
            base = URI.create(siteUrl.trim());
            if (base.getHost() == null) {
                return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (String candidate : candidates(base)) {
            try {
                byte[] data = download(candidate, MAX_ICON_BYTES);
                String ext = data == null ? null : detectExtension(data);
                if (ext == null) {
                    continue;
                }
                Path dir = Paths.get(imagePath);
                Files.createDirectories(dir);
                String name = AUTO_PREFIX + UUID.randomUUID() + ext;
                Files.write(dir.resolve(name), data);
                log.info("icon fetched: {} -> {}", candidate, name);
                return "/images/" + name;
            } catch (Exception e) {
                log.debug("fetch icon failed {}: {}", candidate, e.getMessage());
            }
        }
        log.info("no icon found for {}", siteUrl);
        return null;
    }

    /** 删除之前自动获取的图标文件 */
    public void deleteAuto(String icon) {
        if (!isAuto(icon)) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(imagePath).resolve(icon.substring("/images/".length())));
        } catch (Exception e) {
            log.debug("delete icon failed: {}", e.getMessage());
        }
    }

    public static boolean isAuto(String icon) {
        return icon != null && icon.startsWith("/images/" + AUTO_PREFIX) && !icon.contains("..");
    }

    /** 候选图标地址：页面中声明的图标（按尺寸从大到小）+ 根路径 favicon.ico */
    private List<String> candidates(URI base) {
        Set<String> result = new LinkedHashSet<>();
        try {
            byte[] html = download(base.toString(), MAX_HTML_BYTES);
            if (html != null) {
                List<String[]> icons = new ArrayList<>();
                Matcher m = LINK_TAG.matcher(new String(html, StandardCharsets.UTF_8));
                while (m.find()) {
                    String tag = m.group();
                    if (!REL_ICON.matcher(tag).find()) {
                        continue;
                    }
                    Matcher h = HREF.matcher(tag);
                    if (!h.find()) {
                        continue;
                    }
                    String href = h.group(1) != null ? h.group(1) : h.group(2) != null ? h.group(2) : h.group(3);
                    href = href.trim().replace("&amp;", "&");
                    if (href.isEmpty() || href.startsWith("data:")) {
                        continue;
                    }
                    icons.add(new String[]{base.resolve(href).toString(), String.valueOf(sizeOf(tag))});
                }
                icons.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
                icons.forEach(i -> result.add(i[0]));
            }
        } catch (Exception e) {
            log.debug("parse page failed {}: {}", base, e.getMessage());
        }
        String port = base.getPort() == -1 ? "" : ":" + base.getPort();
        result.add(base.getScheme() + "://" + base.getHost() + port + "/favicon.ico");
        return new ArrayList<>(result);
    }

    private static int sizeOf(String tag) {
        Matcher m = Pattern.compile("sizes\\s*=\\s*[\"']?(\\d+)x\\d+", Pattern.CASE_INSENSITIVE).matcher(tag);
        // 未声明尺寸的按 32 处理；优先使用更清晰的大图标
        return m.find() ? Integer.parseInt(m.group(1)) : 32;
    }

    private static byte[] download(String url, int maxBytes) {
        try (HttpResponse resp = HttpRequest.get(url)
                .header("User-Agent", UA)
                .setFollowRedirects(true)
                .setMaxRedirectCount(5)
                .timeout(TIMEOUT_MS)
                .execute()) {
            if (!resp.isOk()) {
                return null;
            }
            byte[] body = resp.bodyBytes();
            return body == null || body.length == 0 || body.length > maxBytes ? null : body;
        }
    }

    /** 根据文件头判断图片类型，非图片返回 null */
    static String detectExtension(byte[] d) {
        if (d.length < 4) {
            return null;
        }
        if ((d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return ".png";
        if ((d[0] & 0xff) == 0xff && (d[1] & 0xff) == 0xd8) return ".jpg";
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F') return ".gif";
        if (d[0] == 0 && d[1] == 0 && d[2] == 1 && d[3] == 0) return ".ico";
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return ".webp";
        String head = new String(d, 0, Math.min(d.length, 1024), StandardCharsets.UTF_8).trim().toLowerCase();
        if (head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"))) return ".svg";
        return null;
    }
}
