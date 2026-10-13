package com.smartparking.common.core.monitor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorLoopTest {

    /** 假数据源：对 gateway_error_rate_pct 返回越界值，其余返回 0.0（区间内）。 */
    static class FakeHighErrorRateSource implements MetricSource {
        @Override
        public double sample(Band band) {
            return "gateway_error_rate_pct".equals(band.getMetric()) ? 2.5 : 0.0;
        }
    }

    private Band gatewayErrorRateBand() {
        Band b = new Band();
        b.setMetric("gateway_error_rate_pct");
        b.setService("smart-parking-gateway");
        b.setBand(List.of(java.math.BigDecimal.ZERO, new java.math.BigDecimal("1.0")));
        Band.OnBreach ob = new Band.OnBreach();
        ob.setSeverity("critical");
        ob.setAction("page_oncall");
        ob.setWriteIntent(true);
        b.setOnBreach(ob);
        return b;
    }

    private Band safeBand() {
        Band b = new Band();
        b.setMetric("order_latency_ms");
        b.setService("smart-parking-order");
        b.setBand(List.of(java.math.BigDecimal.ZERO, new java.math.BigDecimal("400")));
        Band.OnBreach ob = new Band.OnBreach();
        ob.setWriteIntent(true);
        b.setOnBreach(ob);
        return b;
    }

    @Test
    void evaluateAll_writesIntentOnBreach_onlyForBreachedMetric() throws Exception {
        Path dir = Files.createTempDirectory("monitor-loop-test");
        List<Band> bands = List.of(gatewayErrorRateBand(), safeBand());

        MonitorLoop loop = new MonitorLoop(bands, new FakeHighErrorRateSource(), dir);
        int generated = loop.evaluateAll();

        assertEquals(1, generated, "仅越界的那条应生成 intent");
        Path intentFile = dir.resolve("intent-*-gateway_error_rate_pct.md".replace("*",
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)));
        assertTrue(Files.exists(intentFile), "应生成 gateway_error_rate_pct 越界的 intent 文件");
        String content = Files.readString(intentFile);
        assertTrue(content.contains("2.5"), "intent 应记录实测越界值 2.5");
        assertTrue(content.contains("gateway_error_rate_pct"), "intent 应标注越界指标名");
    }

    @Test
    void evaluateAll_noBreach_writesNothing() throws Exception {
        Path dir = Files.createTempDirectory("monitor-loop-noop");
        List<Band> bands = List.of(gatewayErrorRateBand(), safeBand());

        // DemoMetricSource 恒定返回 0.0，均在区间内
        MonitorLoop loop = new MonitorLoop(bands, new DemoMetricSource(), dir);
        int generated = loop.evaluateAll();

        assertEquals(0, generated, "无越界时不应生成 intent");
    }
}
