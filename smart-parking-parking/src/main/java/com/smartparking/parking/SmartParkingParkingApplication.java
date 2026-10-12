package com.smartparking.parking;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 车位服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.parking", "com.smartparking.common.core"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@MapperScan("com.smartparking.parking.mapper")
public class SmartParkingParkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingParkingApplication.class, args);
    }
}
