package com;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@MapperScan("com.qc.mapper")
@Slf4j
public class SpringAiApplication {
    public static void main(String[] args) throws UnknownHostException {
        //1. 创建并运行 Spring 应用
        SpringApplication app = new SpringApplicationBuilder(SpringAiApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();
        //2. 确定访问协议（HTTP/HTTPS）
        String protocol = "http";
        //检查是否配置了 SSL 证书,如果配置了 SSL 证书，说明应用启用了 HTTPS，将协议改为 https
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }
        //3. 打印启动成功日志
        log.info("--/\n---------------------------------------------------------------------------------------\n\t" +
                        "Application '{}' is running! Access URLs:\n\t" +
                        "Local: \t\t{}://localhost:{}\n\t" +
                        "External: \t{}://{}:{}" +
                        "\n---------------------------------------------------------------------------------------",
                env.getProperty("spring.application.name"),
                protocol,
                env.getProperty("server.port"),
                protocol,
                InetAddress.getLocalHost().getHostAddress(),
                env.getProperty("server.port"))
               ;
    }

}
