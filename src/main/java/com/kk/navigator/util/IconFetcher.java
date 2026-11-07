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
import java.util.Base64;
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

    private static final java.util.Map<String, String> MIME = java.util.Map.of(
            ".png", "image/png", ".jpg", "image/jpeg", ".gif", "image/gif", ".ico", "image/x-icon",
            ".webp", "image/webp", ".svg", "image/svg+xml");
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE);

    @Value("${image.path}")
    private String imagePath;

    /** 站点信息：页面标题与图标路径，均可能为 null */
    public record SiteMeta(String title, String icon) {
    }

    /**
     * 获取并保存图标
     *
     * @return 图标访问路径（/images/xxx），获取失败返回 null
     */
    public String fetch(String siteUrl) {
        return fetchMeta(siteUrl).icon();
    }

    /** 获取站点标题和图标（图标会保存到本地） */
    public SiteMeta fetchMeta(String siteUrl) {
        URI base;
        try {
            base = URI.create(siteUrl.trim());
            if (base.getHost() == null) {
                return new SiteMeta(null, null);
            }
        } catch (IllegalArgumentException e) {
            return new SiteMeta(null, null);
        }
        String html = null;
        try {
            html = decode(download(base.toString(), MAX_HTML_BYTES));
        } catch (Exception e) {
            log.debug("load page failed {}: {}", base, e.getMessage());
        }
        return new SiteMeta(title(html), saveIcon(base, html));
    }

    private String saveIcon(URI base, String html) {
        for (String candidate : candidates(base, html)) {
            try {
                byte[] data = download(candidate, MAX_ICON_BYTES);
                String ext = data == null ? null : detectExtension(data);
                if (ext == null) {
                    continue;
                }
                String icon = write(data, AUTO_PREFIX, ext);
                log.info("icon fetched: {} -> {}", candidate, icon);
                return icon;
            } catch (Exception e) {
                log.debug("fetch icon failed {}: {}", candidate, e.getMessage());
            }
        }
        log.info("no icon found for {}", base);
        return null;
    }

    /** 根据页面中声明的编码解码（兼容 GBK 等非 UTF-8 页面） */
    private static String decode(byte[] data) {
        if (data == null) {
            return null;
        }
        String head = new String(data, 0, Math.min(data.length, 4096), StandardCharsets.ISO_8859_1);
        Matcher m = CHARSET.matcher(head);
        if (m.find()) {
            try {
                return new String(data, java.nio.charset.Charset.forName(m.group(1)));
            } catch (Exception ignored) {
                // 不支持的编码按 UTF-8 处理
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String title(String html) {
        if (html == null) {
            return null;
        }
        Matcher m = TITLE.matcher(html);
        if (!m.find()) {
            return null;
        }
        String t = m.group(1).replaceAll("\\s+", " ").trim()
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > 50 ? t.substring(0, 50) : t;
    }

    public static boolean isAuto(String icon) {
        return isLocal(icon) && icon.startsWith("/images/" + AUTO_PREFIX);
    }

    /** 是否为保存在本地图片目录中的图标 */
    public static boolean isLocal(String icon) {
        return icon != null && icon.startsWith("/images/") && !icon.contains("..") && icon.indexOf('/', 8) < 0;
    }

    private Path localPath(String icon) {
        return Paths.get(imagePath).resolve(icon.substring("/images/".length()));
    }

    public boolean exists(String icon) {
        return isLocal(icon) && Files.isRegularFile(localPath(icon));
    }

    /** 删除本地图标文件 */
    public void delete(String icon) {
        if (!isLocal(icon)) {
            return;
        }
        try {
            Files.deleteIfExists(localPath(icon));
        } catch (Exception e) {
            log.debug("delete icon failed: {}", e.getMessage());
        }
    }

    /** 本地图标转为 data URL，用于导出备份；非本地图标或文件不存在时原样返回 */
    public String toDataUrl(String icon) {
        if (!exists(icon)) {
            return icon;
        }
        try {
            byte[] data = Files.readAllBytes(localPath(icon));
            String ext = detectExtension(data);
            if (ext == null) {
                return icon;
            }
            return "data:" + MIME.get(ext) + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (Exception e) {
            return icon;
        }
    }

    /** 保存导入备份中的 data URL 图标，返回本地路径；内容不是图片时返回 null */
    public String saveDataUrl(String dataUrl) {
        int comma = dataUrl == null ? -1 : dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.startsWith("data:image/") || !dataUrl.substring(0, comma).endsWith(";base64")) {
            return null;
        }
        try {
            byte[] data = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            String ext = data.length > MAX_ICON_BYTES ? null : detectExtension(data);
            return ext == null ? null : write(data, "up-", ext);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 删除没有被任何站点引用、且修改时间早于 minAgeMs 的图标文件
     * （例如编辑时上传了图标但取消保存、旧版本替换图标后遗留的文件）
     */
    public int cleanup(Set<String> referenced, long minAgeMs) {
        Path dir = Paths.get(imagePath);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        long deadline = System.currentTimeMillis() - minAgeMs;
        int count = 0;
        try (var files = Files.list(dir)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                if (!referenced.contains("/images/" + f.getFileName())
                        && Files.getLastModifiedTime(f).toMillis() <= deadline) {
                    Files.deleteIfExists(f);
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("cleanup icons failed: {}", e.getMessage());
        }
        if (count > 0) {
            log.info("removed {} unused icon files", count);
        }
        return count;
    }

    private String write(byte[] data, String prefix, String ext) throws java.io.IOException {
        Path dir = Paths.get(imagePath);
        Files.createDirectories(dir);
        String name = prefix + UUID.randomUUID() + ext;
        Files.write(dir.resolve(name), data);
        return "/images/" + name;
    }

    /** 候选图标地址：页面中声明的图标（按尺寸从大到小）+ 根路径 favicon.ico */
    private List<String> candidates(URI base, String html) {
        Set<String> result = new LinkedHashSet<>();
        try {
            if (html != null) {
                List<String[]> icons = new ArrayList<>();
                Matcher m = LINK_TAG.matcher(html);
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
    public static String detectExtension(byte[] d) {
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
