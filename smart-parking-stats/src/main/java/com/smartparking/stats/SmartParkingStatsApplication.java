package com.smartparking.stats;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 统计服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.stats", "com.smartparking.common.core"})
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.smartparking.stats.mapper")
public class SmartParkingStatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingStatsApplication.class, args);
    }
}
