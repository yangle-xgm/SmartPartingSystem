package com.smartparking.vehicle;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 车辆服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.vehicle", "com.smartparking.common.core"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
@MapperScan("com.smartparking.vehicle.mapper")
public class SmartParkingVehicleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingVehicleApplication.class, args);
    }
}
