package com.smartparking.device;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 设备服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.device", "com.smartparking.common.core"})
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.smartparking.device.mapper")
public class SmartParkingDeviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingDeviceApplication.class, args);
    }
}
