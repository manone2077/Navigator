package com.kk.navigator.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 由服务端获取站点标题和图标。
 * <p>
 * 浏览器直接加载 favicon 经常失败：图标不在根路径（如 qBittorrent、Transmission）、
 * 自签名 https 证书（如 Portainer、PVE）、http 页面加载 https 资源等。
 * 服务端解析页面中的 &lt;link rel="icon"&gt;，并忽略证书校验下载图标，可以解决大部分问题。
 * <p>
 * 为防止被用来探测服务器自身或云平台元数据接口（SSRF），每一跳请求（包括重定向）都会检查目标地址：
 * 禁止回环、链路本地（169.254.0.0/16，含云元数据地址）、任意地址和组播地址；
 * 局域网私有地址（192.168.x.x 等）是导航站的主要用途，允许访问。
 */
@Slf4j
@Component
public class IconFetcher {
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_HTML_BYTES = 512 * 1024;
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern REL_ICON = Pattern.compile("\\brel\\s*=\\s*[\"']?[^\"'>]*icon", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZES = Pattern.compile("sizes\\s*=\\s*[\"']?(\\d+)x\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final IconStore iconStore;

    public IconFetcher(IconStore iconStore) {
        this.iconStore = iconStore;
    }

    /** 站点信息：页面标题与图标路径，均可能为 null */
    public record SiteMeta(String title, String icon) {
    }

    /** 不允许服务端访问的地址 */
    public static class BlockedAddressException extends IOException {
        public BlockedAddressException(String message) {
            super(message);
        }
    }

    /** 获取并保存图标，返回访问路径；获取失败返回 null */
    public String fetch(String siteUrl) {
        return fetchMeta(siteUrl).icon();
    }

    /** 获取站点标题和图标（图标会保存到本地） */
    public SiteMeta fetchMeta(String siteUrl) {
        URI base;
        try {
            base = URI.create(siteUrl.trim());
            checkAllowed(base);
        } catch (IllegalArgumentException | IOException e) {
            log.info("skip fetching {}: {}", siteUrl, e.getMessage());
            return new SiteMeta(null, null);
        }
        String html = null;
        try {
            html = decode(download(base, MAX_HTML_BYTES));
        } catch (IOException e) {
            log.debug("load page failed {}: {}", base, e.getMessage());
        }
        return new SiteMeta(title(html), saveIcon(base, html));
    }

    /**
     * 检查服务端是否允许访问该地址，不允许时抛出 {@link BlockedAddressException}
     */
    public static void checkAllowed(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BlockedAddressException("只支持 http/https 地址");
        }
        if (uri.getHost() == null) {
            throw new BlockedAddressException("地址格式不正确");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new UnknownHostException("域名无法解析：" + uri.getHost());
        }
        for (InetAddress a : addresses) {
            if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress()) {
                throw new BlockedAddressException("不允许访问该地址：" + uri.getHost());
            }
        }
    }

    private String saveIcon(URI base, String html) {
        for (String candidate : candidates(base, html)) {
            try {
                byte[] data = download(URI.create(candidate), IconStore.MAX_ICON_BYTES);
                String icon = data == null ? null : iconStore.save(data, IconStore.AUTO_PREFIX);
                if (icon != null) {
                    log.info("icon fetched: {} -> {}", candidate, icon);
                    return icon;
                }
            } catch (IllegalArgumentException | IOException e) {
                log.debug("fetch icon failed {}: {}", candidate, e.getMessage());
            }
        }
        log.info("no icon found for {}", base);
        return null;
    }

    /**
     * 下载内容；手动处理重定向，确保每一跳都经过地址检查
     *
     * @return 内容，状态码非 2xx、内容为空或超过 maxBytes 时返回 null
     */
    private static byte[] download(URI uri, int maxBytes) throws IOException {
        URI current = uri;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            checkAllowed(current);
            try (HttpResponse resp = HttpRequest.get(current.toString())
                    .header("User-Agent", UA)
                    .setFollowRedirects(false)
                    .timeout(TIMEOUT_MS)
                    .execute()) {
                int status = resp.getStatus();
                if (status >= 300 && status < 400) {
                    String location = resp.header("Location");
                    if (location == null || location.isBlank()) {
                        return null;
                    }
                    current = current.resolve(location.trim());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    return null;
                }
                byte[] body = resp.bodyBytes();
                return body == null || body.length == 0 || body.length > maxBytes ? null : body;
            } catch (cn.hutool.http.HttpException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
        return null;
    }

    /** 候选图标地址：页面中声明的图标（按尺寸从大到小）+ 根路径 favicon.ico */
    private static List<String> candidates(URI base, String html) {
        Set<String> result = new LinkedHashSet<>();
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
                try {
                    icons.add(new String[]{base.resolve(href).toString(), String.valueOf(sizeOf(tag))});
                } catch (IllegalArgumentException ignored) {
                    // 无法解析的图标地址
                }
            }
            icons.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
            icons.forEach(i -> result.add(i[0]));
        }
        String port = base.getPort() == -1 ? "" : ":" + base.getPort();
        result.add(base.getScheme() + "://" + base.getHost() + port + "/favicon.ico");
        return new ArrayList<>(result);
    }

    private static int sizeOf(String tag) {
        Matcher m = SIZES.matcher(tag);
        // 未声明尺寸的按 32 处理；优先使用更清晰的大图标
        return m.find() ? Integer.parseInt(m.group(1)) : 32;
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
                return new String(data, Charset.forName(m.group(1)));
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
}
