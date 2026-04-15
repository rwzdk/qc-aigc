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
                // 连接超时：30秒
                .connectTimeout(30, TimeUnit.SECONDS)
                // 写入超时：60秒
                .writeTimeout(60, TimeUnit.SECONDS)
                // 【核心】读取超时：300秒（5分钟），适配长文本流式返回
                .readTimeout(300, TimeUnit.SECONDS)
                // 整个调用的超时时间：300秒
                .callTimeout(300, TimeUnit.SECONDS)
                .build();
    }
}