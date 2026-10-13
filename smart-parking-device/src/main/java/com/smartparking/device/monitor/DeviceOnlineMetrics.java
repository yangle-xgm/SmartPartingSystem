package com.smartparking.device.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 设备在线率埋点。
 *
 * <p>按 {@code device} 表中在线标记统计离线率：
 * {@code (1 - 在线设备数/总设备数) * 100}。暴露为 {@code device_offline_rate_pct}。</p>
 *
 * <p>采样容错：DB 未就绪时保持上一次值（初始 0），不误报。</p>
 */
@Configuration
@EnableScheduling
public class DeviceOnlineMetrics {

    private final AtomicReference<Double> offlineRate = new AtomicReference<>(0.0);
    private final JdbcTemplate jdbcTemplate;

    public DeviceOnlineMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("device_offline_rate_pct", offlineRate, r -> r.get())
                .description("设备离线率（%，离线设备/总设备）")
                .baseUnit("percent")
                .register(registry);
    }

    @Scheduled(fixedDelay = 60_000)
    public void sample() {
        try {
            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device", Integer.class);
            Integer online = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device WHERE online = 1", Integer.class);
            if (total != null && total > 0 && online != null) {
                double rate = (1 - (double) online / total) * 100.0;
                offlineRate.set(rate);
            }
        } catch (Exception ignored) {
            // DB 未就绪：保持上一次值
        }
    }

    /** 供设备心跳/状态更新主动上报离线率（自采样兜底之外）。 */
    public void setOfflineRate(double pct) {
        offlineRate.set(pct);
    }
}
