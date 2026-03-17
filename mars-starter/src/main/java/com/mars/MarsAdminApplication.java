package com.mars;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mars Admin 启动类
 */
@SpringBootApplication
@EnableScheduling
public class MarsAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarsAdminApplication.class, args);
    }
}
