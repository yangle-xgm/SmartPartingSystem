package com.smartparking.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.ai", "com.smartparking.common.core"},
        exclude = {DataSourceAutoConfiguration.class})
@EnableDiscoveryClient
@EnableFeignClients
public class SmartParkingAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartParkingAiApplication.class, args);
    }
}
