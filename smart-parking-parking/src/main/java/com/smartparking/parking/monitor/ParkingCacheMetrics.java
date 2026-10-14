package com.smartparking.parking.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 车位状态缓存一致性埋点。
 *
 * <p>以 Redis 中车位缓存 key 的覆盖率近似估算「缓存未命中率」：
 * {@code (1 - 缓存key数/车位总数) * 100}。暴露为 {@code space_cache_miss_rate_pct}。</p>
 *
 * <p>采样容错：Redis / DB 未就绪时保持上一次值（初始 0），不误报。</p>
 */
@Configuration
@EnableScheduling
public class ParkingCacheMetrics {

    private final AtomicReference<Double> missRate = new AtomicReference<>(0.0);
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;

    public ParkingCacheMetrics(JdbcTemplate jdbcTemplate, StringRedisTemplate redis, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        Gauge.builder("space_cache_miss_rate_pct", missRate, r -> r.get())
                .description("车位状态缓存未命中率（%，基于 Redis 缓存覆盖比例估算）")
                .baseUnit("percent")
                .register(registry);
    }

    @Scheduled(fixedDelay = 60_000)
    public void sample() {
        try {
            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM parking_space", Integer.class);
            Set<String> keys = redis.keys("parking:space:*");
            int cached = (keys == null) ? 0 : keys.size();
            if (total != null && total > 0) {
                double miss = (1 - (double) cached / total) * 100.0;
                missRate.set(miss);
            }
        } catch (Exception ignored) {
            // Redis / DB 未就绪：保持上一次值
        }
    }

    /** 供缓存读写切面主动上报未命中率（自采样兜底之外）。 */
    public void setMissRate(double pct) {
        missRate.set(pct);
    }
}
