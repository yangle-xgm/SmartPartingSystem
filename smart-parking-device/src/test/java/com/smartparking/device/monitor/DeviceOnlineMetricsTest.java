package com.smartparking.device.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DeviceOnlineMetricsTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void gaugeRegistersAndReflectsReportedValue() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeviceOnlineMetrics metrics = new DeviceOnlineMetrics(jdbcTemplate, registry);

        Gauge gauge = registry.find("device_offline_rate_pct").gauge();
        assertThat(gauge).isNotNull();

        metrics.setOfflineRate(1.5);
        assertThat(gauge.value()).isEqualTo(1.5);
    }
}
