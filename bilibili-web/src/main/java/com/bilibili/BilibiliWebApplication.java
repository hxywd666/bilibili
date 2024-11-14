package com.bilibili;

import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

// 启用 x-file-storage
@EnableFileStorage
@SpringBootApplication
@EnableAsync
public class BilibiliWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(BilibiliWebApplication.class, args);
    }
}
