package com.kk.navigator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ResourceConfig implements WebMvcConfigurer {

    @Value("${image.path}")
    private  String imagePath;

    @Value("${assets.path}")
    private String assetsPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:"+imagePath);

        registry.addResourceHandler("/assets/**")
                .addResourceLocations("file:" + assetsPath);
    }
}
