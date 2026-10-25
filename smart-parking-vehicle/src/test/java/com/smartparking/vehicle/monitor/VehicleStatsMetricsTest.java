package com.smartparking.vehicle.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleStatsMetricsTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void bindCounterIncrements() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VehicleStatsMetrics metrics = new VehicleStatsMetrics(jdbcTemplate, registry);

        metrics.onBind();
        metrics.onBind();

        Counter bind = registry.find("vehicle_bind_total").counter();
        assertThat(bind).isNotNull();
        assertThat(bind.count()).isEqualTo(2.0);
    }

    @Test
    void gaugeRegistersAndSamplesFromDb() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VehicleStatsMetrics metrics = new VehicleStatsMetrics(jdbcTemplate, registry);

        Gauge gauge = registry.find("vehicle_total").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(42);
        metrics.sample();
        assertThat(gauge.value()).isEqualTo(42.0);
    }

    @Test
    void sampleKeepsLastValueWhenDbFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VehicleStatsMetrics metrics = new VehicleStatsMetrics(jdbcTemplate, registry);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new RuntimeException("db down"));
        metrics.sample();

        Gauge gauge = registry.find("vehicle_total").gauge();
        assertThat(gauge.value()).isEqualTo(0.0);
    }
}
