package com.smartparking.order.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OrderBusinessMetricsTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private DataSource dataSource;

    @Test
    void gaugesRegisterAndReflectReportedValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderBusinessMetrics metrics = new OrderBusinessMetrics(rabbitTemplate, dataSource, registry);

        Gauge backlog = registry.find("mq_order_backlog").gauge();
        Gauge conns = registry.find("mysql_active_connections").gauge();
        assertThat(backlog).isNotNull();
        assertThat(conns).isNotNull();

        metrics.setBacklog(7);
        metrics.setActiveConns(3);
        assertThat(backlog.value()).isEqualTo(7.0);
        assertThat(conns.value()).isEqualTo(3.0);
    }
}
