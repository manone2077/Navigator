package com.kk.navigator.controller;

import com.kk.navigator.dao.SiteInfoDao;
import com.kk.navigator.entity.R;
import com.kk.navigator.entity.SiteInfo;
import com.kk.navigator.util.ScheduleTask;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api")
public class HomeController {
    @Resource
    private SiteInfoDao siteInfoDao;
    @Resource
    private ScheduleTask scheduleTask;
    @Value("${image.path}")
    private String imagePath;

    @RequestMapping("/all")
    public R getAll() {
        log.info("get all site info");
        Iterable<SiteInfo> data = siteInfoDao.findAll();
        return R.success("success", data);
    }

    @RequestMapping("/select")
    public R getSiteInfoById(@RequestBody SiteInfo siteInfo) {
        Optional<SiteInfo> data = siteInfoDao.findById(siteInfo.getId());
        if (data.isPresent()) {
            return R.success("success", data.get());
        } else {
            return R.fail("无法找到对应站点");
        }
    }

    @RequestMapping("/add")
    public R addSiteInfo(@RequestBody SiteInfo siteInfo) {
        siteInfoDao.save(siteInfo);
        return R.success("站点添加成功");
    }

    @RequestMapping("/upload")
    public R uploadImage(@RequestParam("file") MultipartFile file) {
        // 文件为空检查
        if (file.isEmpty()) {
            log.info("上传失败：文件为空");
            return R.fail("File is empty");
        }

        // 文件类型和大小验证
        String contentType = file.getContentType();
        long fileSize = file.getSize();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            log.info("上传失败：不支持的文件类型");
            return R.fail("Unsupported file type: " + contentType);
        }
        if (fileSize > 50 * 1024 * 1024) { // 限制文件大小为5MB
            log.info("上传失败：文件过大");
            return R.fail("File too large");
        }

        try {
            // 确保目录存在
            Path uploadDir = Paths.get(imagePath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
            Path uploadedFilePath = uploadDir.resolve(uniqueFilename);

            // 将文件保存到指定路径
            Files.copy(file.getInputStream(), uploadedFilePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("上传成功：{}", uniqueFilename);

            return R.success("成功", uniqueFilename);

        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage());
            return R.fail("File upload failed");
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            return R.error("Internal server error");
        }
    }

    @RequestMapping("/del")
    public R delSiteInfo(@RequestBody SiteInfo siteInfo) {
        siteInfoDao.delete(siteInfo);
        return R.success("Site info deleted successfully");
    }

    @RequestMapping("/deleteAll")
    public R deleteAll() {
        siteInfoDao.deleteAll();
        return R.success("删除所有站点信息成功");
    }

    @RequestMapping("/update")
    public R updateSiteInfo(@RequestBody SiteInfo siteInfo) {
        siteInfoDao.save(siteInfo);
        return R.success("更新站点成功");
    }

    @RequestMapping("/updateImage")
    public R updateImage(@RequestBody SiteInfo siteInfo) {
        SiteInfo siteInfoById = siteInfoDao.findSiteInfoById(siteInfo.getId());
        if (siteInfoById != null) {
            siteInfoById.setIcon(siteInfo.getIcon());
            siteInfoDao.save(siteInfoById);
            return R.success("更新头像成功");
        } else {
            return R.fail("无法找到站点信息");
        }
    }

    @RequestMapping("/speedtest")
    public R speedtest() {
        Iterable<SiteInfo> data = siteInfoDao.findAll();
        if (data.iterator().hasNext()) {
            latency(data);
        }
        return R.success("测速完成");
    }

    @Scheduled(initialDelay = 5 * 1000, fixedRate = 1000 * 60 * 10)
    public void scheduleTask() {
        Iterable<SiteInfo> data = siteInfoDao.findAll();
        if (data.iterator().hasNext()) {
            latency(data);
        }
    }

    private void latency(Iterable<SiteInfo> target) {
        target.forEach((site) -> {
            String host = site.getUrl().split("/")[2];
            if (host.contains(":")) {
                host = host.split(":")[0];
            }
            try {
                InetAddress inetAddress = InetAddress.getByName(host);
                long startTime = System.currentTimeMillis();
                if (inetAddress.isReachable(5000)) {
                    long endTime = System.currentTimeMillis();
                    long delay = endTime - startTime;
                    log.info("speed: {} delay {}ms", host, delay);
                    site.setStatus(delay);
                } else {
                    log.warn("超时无法到达主机,{}", host);
                    site.setStatus(88888L);
                }
            } catch (Exception e) {
                log.error("{}-->{}", e.getMessage(), host);
                site.setStatus(99999L);
            } finally {
                siteInfoDao.save(site);
            }
        });
    }
}
