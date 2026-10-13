package com.smartparking.parking.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ParkingCacheMetricsTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private StringRedisTemplate redis;

    @Test
    void gaugeRegistersAndReflectsReportedValue() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ParkingCacheMetrics metrics = new ParkingCacheMetrics(jdbcTemplate, redis, registry);

        Gauge gauge = registry.find("space_cache_miss_rate_pct").gauge();
        assertThat(gauge).isNotNull();

        metrics.setMissRate(15.0);
        assertThat(gauge.value()).isEqualTo(15.0);
    }
}
