package com.smartparking.common.core.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

import java.util.concurrent.TimeUnit;

/**
 * L1 本地缓存（Caffeine）：高频读、短 TTL、允许短暂不一致
 * - 车位可用统计、dashboard 指标等读多写少数据
 * - 击穿保护：本地缓存挡 10s，Redis 挡 5min，DB 兜底
 */
@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, Object> l1Cache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .maximumSize(500)
                .recordStats()
                .build();
    }
}
