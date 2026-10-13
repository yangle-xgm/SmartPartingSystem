package com.smartparking.common.core.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 监控闭环驱动器：把“阈值配置(bands.yaml) + 实时读数(MetricSource)”串成闭环。
 *
 * <p>每调用一次 {@link #evaluateAll()} 即跑一轮：逐条读取指标当前值 → 用 {@link BandEvaluator} 判定越界 →
 * 越界且 {@code writeIntent=true} 时，把生成的 {@code intent-<date>-<metric>.md} 写入 {@code outputDir}。
 * 生成的 intent 文件即下一轮「规划阶段」的需求输入，从而完成 维护 → 规划 的自动回流。</p>
 *
 * <p>本类不依赖 Spring / 调度框架，可放进任何模块的 {@code @Scheduled} 任务中周期执行，也可直接单测。</p>
 */
public class MonitorLoop {

    private final List<Band> bands;
    private final MetricSource source;
    private final Path outputDir;

    public MonitorLoop(List<Band> bands, MetricSource source, Path outputDir) {
        this.bands = bands;
        this.source = source;
        this.outputDir = outputDir;
    }

    /**
     * 执行一轮全量评估。
     *
     * @return 本次新生成的 intent 文件数量（未越界或 {@code writeIntent=false} 的不计数）
     * @throws IOException 写入 intent 文件失败时抛出
     */
    public int evaluateAll() throws IOException {
        int generated = 0;
        for (Band band : bands) {
            double value = source.sample(band);
            BandEvaluator.Violation v = BandEvaluator.evaluate(band, value);
            if (v != null && v.isWriteIntent()) {
                String content = BandEvaluator.generateIntent(band, value);
                String fileName = "intent-" +
                        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) +
                        "-" + band.getMetric() + ".md";
                Files.createDirectories(outputDir);
                Files.writeString(outputDir.resolve(fileName), content);
                generated++;
            }
        }
        return generated;
    }
}
