package com.kk.navigator.controller;

import com.kk.navigator.controller.dto.SiteForm;
import com.kk.navigator.entity.BizException;
import com.kk.navigator.entity.R;
import com.kk.navigator.entity.SiteInfo;
import com.kk.navigator.service.SiteService;
import com.kk.navigator.service.SpeedtestService;
import com.kk.navigator.util.IconFetcher;
import com.kk.navigator.util.IconStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点接口。读取类接口使用 GET，所有修改类接口只接受 POST
 * （并由 RequestGuardFilter 校验请求头，防止跨站请求伪造）。
 */
@RestController
@RequestMapping("/api")
public class SiteController {
    private final SiteService siteService;
    private final SpeedtestService speedtestService;

    public SiteController(SiteService siteService, SpeedtestService speedtestService) {
        this.siteService = siteService;
        this.speedtestService = speedtestService;
    }

    @GetMapping("/all")
    public R<List<SiteInfo>> all() {
        return R.success("success", siteService.findAll());
    }

    @PostMapping("/select")
    public R<SiteInfo> select(@RequestBody SiteForm form) {
        SiteInfo site = siteService.get(form.id());
        if (site == null) {
            throw BizException.notFound();
        }
        return R.success("success", site);
    }

    /** 添加站点；兼容旧版本前端，带 id 时按编辑处理 */
    @PostMapping("/add")
    public R<SiteInfo> add(@RequestBody SiteForm form) {
        if (form.id() != null) {
            return R.success("站点保存成功", siteService.update(form));
        }
        return R.success("站点添加成功", siteService.add(form));
    }

    @PostMapping("/update")
    public R<SiteInfo> update(@RequestBody SiteForm form) {
        return R.success("更新站点成功", siteService.update(form));
    }

    @PostMapping({"/delete", "/del"})
    public R<Void> delete(@RequestBody SiteForm form) {
        siteService.delete(form.id());
        return R.success("站点删除成功");
    }

    @PostMapping("/deleteAll")
    public R<Void> deleteAll() {
        siteService.deleteAll();
        return R.success("删除所有站点信息成功");
    }

    /** 上传图标，返回文件名（访问路径为 /images/文件名） */
    @PostMapping("/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file) {
        String icon = siteService.upload(file);
        return R.success("上传成功", icon.substring(IconStore.URL_PREFIX.length()));
    }

    @PostMapping("/updateImage")
    public R<Void> updateImage(@RequestBody SiteForm form) {
        siteService.updateIcon(form.id(), form.icon());
        return R.success("更新图标成功");
    }

    /** 保存拖拽后的排序，参数为按顺序排列的站点 id 列表 */
    @PostMapping("/sort")
    public R<Void> sort(@RequestBody List<Integer> ids) {
        siteService.sort(ids);
        return R.success("排序已保存");
    }

    @GetMapping("/export")
    public R<List<SiteInfo>> export() {
        return R.success("success", siteService.export());
    }

    @PostMapping("/import")
    public R<Integer> importSites(@RequestBody List<SiteForm> forms) {
        int count = siteService.importSites(forms);
        return R.success("成功导入 " + count + " 个站点", count);
    }

    @PostMapping("/siteMeta")
    public R<Map<String, String>> siteMeta(@RequestBody SiteForm form) {
        IconFetcher.SiteMeta meta = siteService.fetchMeta(form.url());
        Map<String, String> data = new HashMap<>();
        data.put("title", meta.title());
        data.put("icon", meta.icon());
        return R.success("success", data);
    }

    @PostMapping("/fetchIcon")
    public R<String> fetchIcon(@RequestBody SiteForm form) {
        return R.success("图标获取成功", siteService.fetchIcon(form.url()));
    }

    @PostMapping("/refreshIcons")
    public R<List<SiteInfo>> refreshIcons() {
        int[] result = siteService.refreshIcons();
        return R.success("已更新 " + result[0] + "/" + result[1] + " 个站点的图标", siteService.findAll());
    }

    @PostMapping("/speedtest")
    public R<List<SiteInfo>> speedtest() {
        speedtestService.testAll();
        return R.success("测速完成", siteService.findAll());
    }

    @PostMapping("/speedtest/one")
    public R<SiteInfo> speedtestOne(@RequestBody SiteForm form) {
        SiteInfo site = siteService.get(form.id());
        if (site == null) {
            throw BizException.notFound();
        }
        speedtestService.test(site);
        return R.success("测速完成", siteService.get(form.id()));
    }
}
