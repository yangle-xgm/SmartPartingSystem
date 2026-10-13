package com.smartparking.stats.monitor;

import com.smartparking.common.core.monitor.Band;
import com.smartparking.common.core.monitor.BandsLoader;
import com.smartparking.common.core.monitor.MonitorLoop;
import com.smartparking.common.core.monitor.PrometheusMetricSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * 监控闭环自动装配：把 {@code bands.yaml} + {@link PrometheusMetricSource} 串成可周期驱动的 {@link MonitorLoop}。
 *
 * <p>装配条件：{@code monitor.enabled=true}（默认开）。启动时加载 bands 配置、构造基于 Prometheus 的指标源，
 * 之后由 {@link #tick()} 以固定间隔驱动一轮评估；越界且 {@code write_intent=true} 的指标会生成
 * {@code intent-<date>-<metric>.md} 落到 {@code monitor.output-dir}，回流至规划阶段形成 AI 原生 SDLC 维护闭环。</p>
 */
@Configuration
@EnableConfigurationProperties(MonitorProperties.class)
@ConditionalOnProperty(name = "monitor.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class MonitorAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MonitorAutoConfiguration.class);

    private final MonitorProperties properties;
    private final MonitorLoop monitorLoop;

    public MonitorAutoConfiguration(MonitorProperties properties) throws IOException {
        this.properties = properties;
        Resource resource = new DefaultResourceLoader().getResource(properties.getBandsLocation());
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "监控闭环启动失败：找不到 bands 配置 " + properties.getBandsLocation());
        }
        List<Band> bands = BandsLoader.load(resource.getInputStream());
        this.monitorLoop = new MonitorLoop(
                bands,
                new PrometheusMetricSource(properties.getPrometheusBaseUrl()),
                Paths.get(properties.getOutputDir()));
        log.info("监控闭环已装配：bands={} 条, prometheus={}, 输出目录={}, 间隔={}ms",
                bands.size(), properties.getPrometheusBaseUrl(),
                properties.getOutputDir(), properties.getFixedDelayMs());
    }

    @Bean
    public MonitorLoop monitorLoop() {
        return monitorLoop;
    }

    /**
     * 周期驱动一轮监控评估。首次在上下文刷新后立即执行一次，之后按 {@code monitor.fixed-delay-ms} 间隔。
     */
    @Scheduled(fixedDelayString = "${monitor.fixed-delay-ms:60000}")
    public void tick() {
        try {
            int generated = monitorLoop.evaluateAll();
            if (generated > 0) {
                log.warn("监控闭环本轮触发 {} 条越界，已生成 intent 文件至 {}",
                        generated, properties.getOutputDir());
            }
        } catch (IOException e) {
            log.error("监控闭环评估失败（磁盘写入 intent 异常）", e);
        }
    }
}
