package com.smartparking.common.core.monitor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 监控带评估器：判断某次指标读数是否越界，并在越界时生成规划需求 intent.md（维护闭环）。
 *
 * <p>纯逻辑、无外部依赖，可直接在单元测试或定时采集任务中调用。</p>
 */
public final class BandEvaluator {

    private BandEvaluator() {
    }

    /** 越界结果 */
    public static final class Violation {
        private final Band band;
        private final double value;
        private final String severity;
        private final String action;
        private final boolean writeIntent;

        public Violation(Band band, double value, String severity, String action, boolean writeIntent) {
            this.band = band;
            this.value = value;
            this.severity = severity;
            this.action = action;
            this.writeIntent = writeIntent;
        }

        public Band getBand() { return band; }
        public double getValue() { return value; }
        public String getSeverity() { return severity; }
        public String getAction() { return action; }
        public boolean isWriteIntent() { return writeIntent; }
    }

    /**
     * 判断单条指标是否越界：value < min 或 value > max 视为越界。
     */
    public static Violation evaluate(Band band, double value) {
        double min = band.getBand().get(0).doubleValue();
        double max = band.getBand().get(1).doubleValue();
        if (value < min || value > max) {
            Band.OnBreach ob = band.getOnBreach();
            return new Violation(band, value, ob.getSeverity(), ob.getAction(), ob.isWriteIntent());
        }
        return null;
    }

    /**
     * 在一组监控带中按指标名查找并评估。
     */
    public static Violation evaluateByMetric(List<Band> bands, String metric, double value) {
        for (Band b : bands) {
            if (b.getMetric().equals(metric)) {
                return evaluate(b, value);
            }
        }
        return null;
    }

    /**
     * 生成维护闭环用的 intent 文档内容（建议文件名 intent-&lt;date&gt;-&lt;metric&gt;.md）。
     */
    public static String generateIntent(Band band, double value) {
        Band.OnBreach ob = band.getOnBreach();
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return """
# intent.md
## Requested by
监控系统自动生成（维护阶段闭环）

## What they said
指标 `%s`（服务：%s）越界：实测值 %s 超出允许的 [%s, %s] 区间。

## What problem this solves
触发严重级别：%s；建议动作：%s。需进入下一轮规划-设计-构建迭代修复。

## Why now
监控告警触发，若不处理将持续影响服务质量。

## Constraints already known
指标阈值来自 bands.yaml，动作策略：%s。

## Explicitly out of scope
本次仅登记需求，具体修复方案由设计阶段确定。

> 本文件由 BandEvaluator 在监控越界时自动生成，回流至规划阶段（AI 原生 SDLC 维护闭环）。
""".formatted(band.getMetric(), band.getService(),
                value, band.getBand().get(0), band.getBand().get(1),
                ob.getSeverity(), ob.getAction(), ob.getAction());
    }
}
