package com.smartparking.common.core.monitor;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 监控带评估器单元测试（Phase D 维护闭环核心逻辑）。
 * 覆盖：区间判定、越界结果、intent 生成、以及从 bands.yaml 真实加载并评估。
 */
class BandEvaluatorTest {

    private Band band(String metric, double min, double max, String severity, String action, boolean writeIntent) {
        Band b = new Band();
        b.setMetric(metric);
        b.setService("svc");
        b.setBand(List.of(BigDecimal.valueOf(min), BigDecimal.valueOf(max)));
        Band.OnBreach ob = new Band.OnBreach();
        ob.setSeverity(severity);
        ob.setAction(action);
        ob.setWriteIntent(writeIntent);
        b.setOnBreach(ob);
        return b;
    }

    @Test
    void withinBand_noViolation() {
        Band b = band("latency", 0, 400, "high", "open_incident", true);
        assertNull(BandEvaluator.evaluate(b, 399));
        assertNull(BandEvaluator.evaluate(b, 0));
        assertNull(BandEvaluator.evaluate(b, 400));
    }

    @Test
    void aboveBand_violation() {
        Band b = band("latency", 0, 400, "high", "open_incident", true);
        BandEvaluator.Violation v = BandEvaluator.evaluate(b, 500);
        assertNotNull(v);
        assertEquals("high", v.getSeverity());
        assertEquals("open_incident", v.getAction());
        assertTrue(v.isWriteIntent());
        assertEquals(500.0, v.getValue(), 0.0001);
    }

    @Test
    void belowBand_violation() {
        Band b = band("rate", 0, 1.0, "critical", "page_oncall", true);
        BandEvaluator.Violation v = BandEvaluator.evaluate(b, -0.5);
        assertNotNull(v);
        assertEquals("critical", v.getSeverity());
    }

    @Test
    void generateIntent_containsContext() {
        Band b = band("p99", 0, 800, "high", "open_incident", true);
        String intent = BandEvaluator.generateIntent(b, 1234);
        assertTrue(intent.contains("intent.md"));
        assertTrue(intent.contains("p99"));
        assertTrue(intent.contains("1234"));
        assertTrue(intent.contains("high"));
        assertTrue(intent.contains("open_incident"));
    }

    @Test
    void loadBands_fromYaml_andEvaluateBreach() throws java.io.IOException {
        try (InputStream in = getClass().getResourceAsStream("/bands.yaml")) {
            assertNotNull(in, "bands.yaml 应位于 test resources");
            List<Band> bands = BandsLoader.load(in);
            assertFalse(bands.isEmpty());

            // gateway_error_rate_pct 区间 [0,1.0]，实测 2.5 应越界且 critical
            BandEvaluator.Violation v = BandEvaluator.evaluateByMetric(bands, "gateway_error_rate_pct", 2.5);
            assertNotNull(v);
            assertEquals("critical", v.getSeverity());
            assertEquals("page_oncall", v.getAction());
            assertTrue(v.isWriteIntent());

            // 正常范围内不越界
            assertNull(BandEvaluator.evaluateByMetric(bands, "gateway_error_rate_pct", 0.3));

            // payment_recon_diff_amount 区间 [0,0]，差额 > 0 即越界
            assertNotNull(BandEvaluator.evaluateByMetric(bands, "payment_recon_diff_amount", 0.01));
        }
    }
}
