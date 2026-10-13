package com.smartparking.order.monitor;

import com.alibaba.druid.pool.DruidDataSource;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 订单服务业务埋点：MQ 积压与 MySQL 活跃连接数。
 *
 * <ul>
 *   <li>{@code mq_order_backlog}：订单相关队列的待消费消息数（最终一致性积压水位）。</li>
 *   <li>{@code mysql_active_connections}：当前 Druid 连接池活跃连接数（安全水位监控）。</li>
 * </ul>
 *
 * <p>采样容错：RabbitMQ / Druid 未就绪时保持上一次值（初始 0），不误报。</p>
 */
@Configuration
@EnableScheduling
public class OrderBusinessMetrics {

    private final AtomicReference<Double> backlog = new AtomicReference<>(0.0);
    private final AtomicReference<Double> activeConns = new AtomicReference<>(0.0);
    private final RabbitTemplate rabbitTemplate;
    private final DataSource dataSource;

    public OrderBusinessMetrics(RabbitTemplate rabbitTemplate, DataSource dataSource, MeterRegistry registry) {
        this.rabbitTemplate = rabbitTemplate;
        this.dataSource = dataSource;
        Gauge.builder("mq_order_backlog", backlog, r -> r.get())
                .description("订单相关 MQ 队列积压消息数")
                .baseUnit("messages")
                .register(registry);
        Gauge.builder("mysql_active_connections", activeConns, r -> r.get())
                .description("MySQL 当前活跃连接数（Druid 连接池 activeCount）")
                .baseUnit("connections")
                .register(registry);
    }

    @Scheduled(fixedDelay = 30_000)
    public void sample() {
        try {
            ChannelCallback<Long> cb = ch -> ch.messageCount("order.queue");
            Long n = rabbitTemplate.execute(cb);
            backlog.set(n == null ? 0.0 : n.doubleValue());
        } catch (Exception ignored) {
            // RabbitMQ 未就绪：保持上一次值
        }
        try {
            if (dataSource instanceof DruidDataSource druid) {
                activeConns.set((double) druid.getActiveCount());
            }
        } catch (Exception ignored) {
            // Druid 未就绪：保持上一次值
        }
    }

    /** 供 MQ 生产者/消费者主动上报积压（自采样兜底之外）。 */
    public void setBacklog(double n) {
        backlog.set(n);
    }

    /** 供连接池监控主动上报活跃连接数（自采样兜底之外）。 */
    public void setActiveConns(double n) {
        activeConns.set(n);
    }
}
