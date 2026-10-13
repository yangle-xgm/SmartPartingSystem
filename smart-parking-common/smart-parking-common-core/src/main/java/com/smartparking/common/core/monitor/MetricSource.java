package com.smartparking.common.core.monitor;

/**
 * 指标数据源：维护阶段监控闭环的“读数入口”。
 *
 * <p>实现方负责从真实监控（Prometheus / Micrometer / 日志 / 数据库）读取某条 Band 对应的当前值。
 * 例如基于 {@code MeterRegistry} 读 Gauge、或用 HTTP 调 Prometheus 的 {@code /api/v1/query}。</p>
 *
 * <p>该接口仅声明“取一个 double 值”，与具体监控栈解耦，便于在单元测试中用假数据驱动闭环。</p>
 */
@FunctionalInterface
public interface MetricSource {

    /**
     * 返回指定监控带对应指标的当前读数。
     *
     * @param band 当前被评估的监控带（含 metric / service / 阈值区间）
     * @return 当前数值（如 p99 延迟毫秒、错误率百分比等）
     */
    double sample(Band band);
}
