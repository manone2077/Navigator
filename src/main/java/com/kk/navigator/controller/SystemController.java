package com.kk.navigator.controller;

import com.kk.navigator.entity.R;
import com.kk.navigator.entity.SystemProxy;
import com.kk.navigator.entity.SystemUserAgent;
import com.kk.navigator.service.SystemProxyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api/system/")
public class SystemController {
    @Resource
    private SystemProxyService systemProxyService;

    @PostMapping("/setProxy")
    public R setProxy(@RequestBody SystemProxy proxy) {
        return systemProxyService.setProxy(proxy);
    }

    @PostMapping("/getProxy")
    public R getProxy(Integer id) {
        return systemProxyService.getSystemProxyInfo(id);
    }

    @PostMapping("/getUserAgent")
    public R getUserAgent(Integer id) {
        return systemProxyService.getSystemUserAgentInfo(id);
    }

    @PostMapping("/setUserAgent")
    public R setUserAgent(@RequestBody SystemUserAgent userAgent) {
        return systemProxyService.setUserAgent(userAgent);
    }
}
