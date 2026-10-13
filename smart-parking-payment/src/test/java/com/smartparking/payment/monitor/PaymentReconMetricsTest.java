package com.smartparking.payment.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PaymentReconMetricsTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void gaugeRegistersAndReflectsReportedValue() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentReconMetrics metrics = new PaymentReconMetrics(jdbcTemplate, registry);

        Gauge gauge = registry.find("payment_recon_diff_amount").gauge();
        assertThat(gauge).isNotNull();

        metrics.setReconDiff(12.5);
        assertThat(gauge.value()).isEqualTo(12.5);

        metrics.setReconDiff(0.0);
        assertThat(gauge.value()).isEqualTo(0.0);
    }
}
