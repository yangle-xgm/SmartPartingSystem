package com.smartparking.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 支付服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.smartparking.payment", "com.smartparking.common.core"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@EnableAsync
@MapperScan("com.smartparking.payment.mapper")
public class SmartParkingPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingPaymentApplication.class, args);
    }
}
