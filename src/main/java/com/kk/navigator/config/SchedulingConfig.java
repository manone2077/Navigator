package com.kk.navigator.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务（自动测速、签到、清理图标），测试时可通过 navigator.scheduling.enabled=false 关闭
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "navigator.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
