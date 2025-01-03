package com.kk.navigator.service;

import com.kk.navigator.dao.SystemProxyDao;
import com.kk.navigator.dao.SystemUserAgentDao;
import com.kk.navigator.entity.R;
import com.kk.navigator.entity.SystemProxy;
import com.kk.navigator.entity.SystemUserAgent;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SystemProxyService {

    @Resource
    private SystemProxyDao systemProxyDao;
    @Autowired
    private SystemUserAgentDao systemUserAgentDao;

    public R getSystemProxyInfo(Integer id) {
        Optional<SystemProxy> systemProxy = systemProxyDao.findById(id);

        if (systemProxy.isPresent()) {
            // 处理找到的对象
            return R.success("成功获取到代理", systemProxy.get());
        } else {
            // 处理未找到的情况
            return R.fail("未找到代理");
        }
    }

    public R setProxy(SystemProxy proxy) {
        SystemProxy systemProxy = systemProxyDao.save(proxy);
        return R.success("设置成功", systemProxy);
    }

    public R getSystemUserAgentInfo(Integer id) {
        return systemUserAgentDao.findById(id)
                .map(systemUserAgent -> R.success("成功获取到代理", systemUserAgent))
                .orElseGet(()->R.fail("未找到代理"));
    }

    public R setUserAgent(SystemUserAgent userAgent) {
        SystemUserAgent systemUserAgent = systemUserAgentDao.save(userAgent);
        return R.success("设置成功", systemUserAgent);
    }
}