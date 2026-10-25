package com.smartparking.ai.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallMetricsTest {

    @Test
    void realAndFallbackCountersAreTaggedByType() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiCallMetrics metrics = new AiCallMetrics(registry);

        metrics.onRealCall("plate");
        metrics.onMockFallback("plate");
        metrics.onMockFallback("plate");
        metrics.onRealCall("chat");
        metrics.onMockFallback("chat");

        Counter plateReal = registry.find("ai_real_call_total").tag("type", "plate").counter();
        Counter plateFallback = registry.find("ai_mock_fallback_total").tag("type", "plate").counter();
        Counter chatReal = registry.find("ai_real_call_total").tag("type", "chat").counter();
        Counter chatFallback = registry.find("ai_mock_fallback_total").tag("type", "chat").counter();

        assertThat(plateReal).isNotNull();
        assertThat(plateReal.count()).isEqualTo(1.0);
        assertThat(plateFallback.count()).isEqualTo(2.0);
        assertThat(chatReal.count()).isEqualTo(1.0);
        assertThat(chatFallback.count()).isEqualTo(1.0);
    }
}
