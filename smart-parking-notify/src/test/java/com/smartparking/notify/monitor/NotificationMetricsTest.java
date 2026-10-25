package com.smartparking.notify.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMetricsTest {

    @Test
    void sentCounterIsTaggedByType() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(registry);

        metrics.onSent(1);
        metrics.onSent(1);
        metrics.onSent(2);

        Counter type1 = registry.find("notification_sent_total").tag("type", "1").counter();
        Counter type2 = registry.find("notification_sent_total").tag("type", "2").counter();
        assertThat(type1).isNotNull();
        assertThat(type1.count()).isEqualTo(2.0);
        assertThat(type2).isNotNull();
        assertThat(type2.count()).isEqualTo(1.0);
    }

    @Test
    void broadcastCounterIncrements() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(registry);

        metrics.onBroadcast();

        Counter broadcast = registry.find("notification_broadcast_total").counter();
        assertThat(broadcast).isNotNull();
        assertThat(broadcast.count()).isEqualTo(1.0);
    }
}
