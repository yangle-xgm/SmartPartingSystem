package com.smartparking.ai.monitor;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * AI 模块调用埋点：真实 API 调用 vs mock 降级。
 *
 * <p>暴露指标（type=plate 车牌识别 / type=chat 智能客服）：</p>
 * <ul>
 *   <li>{@code ai_real_call_total}：真实 API 调用成功累计次数</li>
 *   <li>{@code ai_mock_fallback_total}：mock 兜底累计次数（未配置 provider、或真实调用异常降级）</li>
 * </ul>
 *
 * <p>业务意义：默认 mock 零费用；切换真实 provider 后，
 * {@code rate(ai_mock_fallback_total[5m]) / (rate(ai_real_call_total[5m]) + rate(ai_mock_fallback_total[5m]))}
 * 即降级率，持续 >0 说明 Key/配额/网络异常，同时 real_call 速率可直接估算 API 费用量级。</p>
 */
@Component
public class AiCallMetrics {

    private final MeterRegistry registry;

    public AiCallMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 真实 API 调用成功。 */
    public void onRealCall(String type) {
        registry.counter("ai_real_call_total", "type", type).increment();
    }

    /** mock 兜底（未配置或降级）。 */
    public void onMockFallback(String type) {
        registry.counter("ai_mock_fallback_total", "type", type).increment();
    }
}
