package com.kk.navigator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 站点图标文件的存储：保存、删除、导出为 data URL、清理未使用的文件。
 * 图标统一保存在 image.path 目录下，通过 /images/文件名 访问（由 nginx 提供）。
 */
@Slf4j
@Component
public class IconStore {
    public static final String URL_PREFIX = "/images/";
    /** 自动获取的图标文件名前缀，用于区分用户上传的图标 */
    public static final String AUTO_PREFIX = "auto-";
    /** 从备份导入的图标文件名前缀 */
    public static final String IMPORT_PREFIX = "up-";
    public static final int MAX_ICON_BYTES = 1024 * 1024;

    private static final Map<String, String> MIME = Map.of(
            ".png", "image/png", ".jpg", "image/jpeg", ".gif", "image/gif", ".ico", "image/x-icon",
            ".webp", "image/webp", ".svg", "image/svg+xml");

    private final Path dir;

    public IconStore(@Value("${image.path}") String imagePath) {
        this.dir = Paths.get(imagePath);
    }

    /** 是否为保存在本地图片目录中的图标（只允许 /images/文件名，不允许子目录和 ..） */
    public static boolean isLocal(String icon) {
        return icon != null && icon.startsWith(URL_PREFIX) && !icon.contains("..")
                && icon.indexOf('/', URL_PREFIX.length()) < 0 && icon.length() > URL_PREFIX.length();
    }

    public static boolean isAuto(String icon) {
        return isLocal(icon) && icon.startsWith(URL_PREFIX + AUTO_PREFIX);
    }

    public boolean exists(String icon) {
        return isLocal(icon) && Files.isRegularFile(path(icon));
    }

    /** 保存图片数据，返回访问路径；内容不是支持的图片格式时返回 null */
    public String save(byte[] data, String prefix) throws IOException {
        String ext = data == null || data.length > MAX_ICON_BYTES ? null : detectExtension(data);
        if (ext == null) {
            return null;
        }
        Files.createDirectories(dir);
        String name = prefix + UUID.randomUUID() + ext;
        Files.write(dir.resolve(name), data);
        return URL_PREFIX + name;
    }

    /** 保存用户上传的图片，扩展名由调用方根据 Content-Type 决定（不使用原始文件名，防止上传可执行内容） */
    public String saveUpload(InputStream in, String ext) throws IOException {
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + ext;
        Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return URL_PREFIX + name;
    }

    public void delete(String icon) {
        if (!isLocal(icon)) {
            return;
        }
        try {
            Files.deleteIfExists(path(icon));
        } catch (IOException e) {
            log.debug("delete icon failed: {}", e.getMessage());
        }
    }

    /** 本地图标转为 data URL，用于导出备份；非本地图标或文件不存在时原样返回 */
    public String toDataUrl(String icon) {
        if (!exists(icon)) {
            return icon;
        }
        try {
            byte[] data = Files.readAllBytes(path(icon));
            String ext = detectExtension(data);
            return ext == null ? icon : "data:" + MIME.get(ext) + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (IOException e) {
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
            return save(Base64.getDecoder().decode(dataUrl.substring(comma + 1)), IMPORT_PREFIX);
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }

    /**
     * 删除没有被任何站点引用、且修改时间早于 minAgeMs 的图标文件
     * （例如编辑时上传了图标但取消保存、旧版本替换图标后遗留的文件）
     */
    public int cleanup(Set<String> referenced, long minAgeMs) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        long deadline = System.currentTimeMillis() - minAgeMs;
        int count = 0;
        try (var files = Files.list(dir)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                if (!referenced.contains(URL_PREFIX + f.getFileName())
                        && Files.getLastModifiedTime(f).toMillis() <= deadline) {
                    Files.deleteIfExists(f);
                    count++;
                }
            }
        } catch (IOException e) {
            log.warn("cleanup icons failed: {}", e.getMessage());
        }
        if (count > 0) {
            log.info("removed {} unused icon files", count);
        }
        return count;
    }

    private Path path(String icon) {
        return dir.resolve(icon.substring(URL_PREFIX.length()));
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
