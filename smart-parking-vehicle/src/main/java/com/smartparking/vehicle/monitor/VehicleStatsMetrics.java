package com.smartparking.vehicle.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 车辆模块埋点。
 *
 * <p>暴露指标：</p>
 * <ul>
 *   <li>{@code vehicle_bind_total}：车辆绑定成功累计次数（Counter，业务驱动）</li>
 *   <li>{@code vehicle_total}：车辆总数（Gauge，60s 采样，DB 未就绪保持上一次值）</li>
 * </ul>
 */
@Component
@EnableScheduling
public class VehicleStatsMetrics {

    private final AtomicReference<Double> vehicleTotal = new AtomicReference<>(0.0);
    private final Counter bindTotal;
    private final JdbcTemplate jdbcTemplate;

    public VehicleStatsMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        this.bindTotal = Counter.builder("vehicle_bind_total")
                .description("车辆绑定成功累计次数")
                .register(registry);
        Gauge.builder("vehicle_total", vehicleTotal, r -> r.get())
                .description("车辆总数（未删除）")
                .baseUnit("vehicles")
                .register(registry);
    }

    public void onBind() {
        bindTotal.increment();
    }

    @Scheduled(fixedDelay = 60_000)
    public void sample() {
        try {
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle WHERE is_deleted = 0", Integer.class);
            if (total != null) {
                vehicleTotal.set(total.doubleValue());
            }
        } catch (Exception ignored) {
            // DB 未就绪：保持上一次值，不误报
        }
    }
}
