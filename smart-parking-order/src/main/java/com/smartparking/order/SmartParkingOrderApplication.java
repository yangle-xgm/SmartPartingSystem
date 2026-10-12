package com.smartparking.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 订单服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.order", "com.smartparking.common.core"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
@MapperScan("com.smartparking.order.mapper")
public class SmartParkingOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingOrderApplication.class, args);
    }
}
