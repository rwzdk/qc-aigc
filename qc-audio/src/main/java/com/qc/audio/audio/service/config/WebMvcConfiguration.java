package com.qc.audio.audio.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 【优化1】使用 allowedOriginPatterns 替代 allowedOrigins
                // 这样兼容性更好，且支持后续如果需要带 cookie 的请求
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 【优化2】显式开启允许预检请求缓存 (可选，提升性能)
                .maxAge(3600)
                .exposedHeaders("Content-Disposition"); // 注意大小写，通常首字母大写
    }
}