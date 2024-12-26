package com.kk.navigator.controller;

import com.alibaba.fastjson2.JSONObject;
import com.kk.navigator.dao.SiteInfoDao;
import com.kk.navigator.entity.SiteInfo;
import com.kk.navigator.util.ScheduleTask;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Optional;

@RestController
@Slf4j
@RequestMapping("/api")
public class HomeController {
    @Resource
    private SiteInfoDao siteInfoDao;
    @Resource
    private ScheduleTask scheduleTask;

    @RequestMapping("/all")
    public JSONObject getAll(){
        log.info("get all site info");
        JSONObject result = new JSONObject();
        Iterable<SiteInfo> data=siteInfoDao.findAll();

        result.put("data", data);
        result.put("code", 200);
        result.put("msg", "success");

        return result;
    }

    @RequestMapping("/select")
    public JSONObject getSiteInfoById(@RequestBody SiteInfo siteInfo){
        JSONObject result = new JSONObject();
        Optional<SiteInfo> data=siteInfoDao.findById(siteInfo.getId());
        result.put("data", data);

        return result;
    }

    @RequestMapping("/add")
    public JSONObject addSiteInfo(@RequestBody SiteInfo siteInfo){
        siteInfoDao.save(siteInfo);
        return null;
    }

    @RequestMapping("/del")
    public JSONObject delSiteInfo(@RequestBody SiteInfo siteInfo){
        siteInfoDao.delete(siteInfo);
        return null;
    }

    @RequestMapping("/deleteAll")
    public JSONObject deleteAll(){
        siteInfoDao.deleteAll();

        JSONObject result = new JSONObject();
        result.put("code", 200);
        result.put("msg", "删除所有站点信息成功");
        return result;
    }

    @RequestMapping("/update")
    public JSONObject updateSiteInfo(@RequestBody SiteInfo siteInfo){
        siteInfoDao.save(siteInfo);
        return null;
    }

    @RequestMapping("/speedtest")
    public JSONObject speedtest(){
        Iterable<SiteInfo> data=siteInfoDao.findAll();
        if (data.iterator().hasNext()) {
            latency(data);
        }

        JSONObject result = new JSONObject();
        result.put("code", 200);
        result.put("msg", "测速完成");
        return result;
    }

    @Scheduled(initialDelay = 5*1000,fixedRate = 1000*60*10)
    public void scheduleTask(){
        Iterable<SiteInfo> data=siteInfoDao.findAll();
        if (data.iterator().hasNext()) {
            latency(data);
        }
    }

    private void latency(Iterable<SiteInfo> target){
        target.forEach((site) -> {
            String host=site.getUrl().split("/")[2];
            if (host.contains(":")){
                host=host.split(":")[0];
            }
            try {
                InetAddress inetAddress = InetAddress.getByName(host);
                long startTime=System.currentTimeMillis();
                if(inetAddress.isReachable(5000)){
                    long endTime=System.currentTimeMillis();
                    long delay=endTime-startTime;
                    log.info("speed: {} delay {}ms",host,delay);
                    site.setStatus(delay);

                }else {
                    log.warn("超时无法到达主机,{}",host);
                    site.setStatus(88888L);
                }
            }catch (Exception e){
                log.error("{}-->{}", e.getMessage(), host);
                site.setStatus(99999L);
            }finally {
                siteInfoDao.save(site);
            }
        });
    }
}
