package com.smartparking.notify.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 通知模块埋点。
 *
 * <p>暴露指标：</p>
 * <ul>
 *   <li>{@code notification_sent_total}：站内信发送成功累计次数（按 type 分 tag：1=订单/2=缴费/3=访客/4=系统）</li>
 *   <li>{@code notification_broadcast_total}：全体广播发送累计次数（userId=1）</li>
 * </ul>
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;
    private final Counter broadcast;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.broadcast = Counter.builder("notification_broadcast_total")
                .description("全体广播通知累计次数")
                .register(registry);
    }

    /** 按通知类型计数（get-or-create，幂等）。 */
    public void onSent(Integer type) {
        registry.counter("notification_sent_total", "type", String.valueOf(type)).increment();
    }

    public void onBroadcast() {
        broadcast.increment();
    }
}
