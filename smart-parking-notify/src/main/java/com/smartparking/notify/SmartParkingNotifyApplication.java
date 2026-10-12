package com.smartparking.notify;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 通知服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.notify", "com.smartparking.common.core"})
@EnableDiscoveryClient
@MapperScan("com.smartparking.notify.mapper")
public class SmartParkingNotifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingNotifyApplication.class, args);
    }
}
