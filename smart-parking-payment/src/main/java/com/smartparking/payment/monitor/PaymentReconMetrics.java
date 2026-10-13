package com.smartparking.payment.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 支付对账差额埋点。
 *
 * <p>聚合 {@code payment_reconciliation} 表中「未平账」记录的差额绝对值之和，暴露为
 * {@code payment_recon_diff_amount} Gauge。该值应恒为 0；非 0 即触发 {@code bands.yaml}
 * 中 payment_recon_diff_amount 越界告警（severity=critical，page_oncall）。</p>
 *
 * <p>采样容错：表/字段未就绪时保持上一次值（初始 0，落在正常区间内），避免误报。</p>
 */
@Configuration
@EnableScheduling
public class PaymentReconMetrics {

    private final AtomicReference<Double> reconDiff = new AtomicReference<>(0.0);
    private final JdbcTemplate jdbcTemplate;

    public PaymentReconMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("payment_recon_diff_amount", reconDiff, r -> r.get())
                .description("支付对账差额（未平账记录差额绝对值之和），应恒为 0")
                .baseUnit("amount")
                .register(registry);
    }

    @Scheduled(fixedDelay = 60_000)
    public void sample() {
        try {
            Double sum = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(ABS(diff_amount)), 0) FROM payment_reconciliation WHERE reconciled = 0",
                    Double.class);
            reconDiff.set(sum == null ? 0.0 : sum);
        } catch (Exception ignored) {
            // 表/字段未就绪：保持上一次值，不误报
        }
    }

    /** 供业务对账完成后主动上报差额（自采样兜底之外）。 */
    public void setReconDiff(double amount) {
        reconDiff.set(amount);
    }
}
