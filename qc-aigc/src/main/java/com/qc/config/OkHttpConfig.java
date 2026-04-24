package com.qc.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                // 连接超时：60秒
                .connectTimeout(60, TimeUnit.SECONDS)
                // 写入超时：120秒
                .writeTimeout(120, TimeUnit.SECONDS)
                // 【核心】读取超时：600秒（10分钟），适配超长流式返回
                .readTimeout(600, TimeUnit.SECONDS)
                // 整个调用的超时时间：600秒
                .callTimeout(600, TimeUnit.SECONDS)
                .build();
    }
}
