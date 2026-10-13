package com.smartparking.common.core.monitor;

/**
 * 演示用指标源：恒定返回 0.0（落在所有阈值区间内，不触发越界）。
 *
 * <p>仅用于说明 {@link MetricSource} 的接线方式，或本地无监控环境时跑通闭环流程。
 * 生产环境请替换为读取 Prometheus / Micrometer / 日志的真实实现。</p>
 */
public class DemoMetricSource implements MetricSource {

    @Override
    public double sample(Band band) {
        return 0.0;
    }
}
