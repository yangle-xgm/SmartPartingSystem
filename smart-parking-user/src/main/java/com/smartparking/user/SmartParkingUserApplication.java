package com.smartparking.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.user", "com.smartparking.common.core"})
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.smartparking.user.mapper")
public class SmartParkingUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingUserApplication.class, args);
    }
}
