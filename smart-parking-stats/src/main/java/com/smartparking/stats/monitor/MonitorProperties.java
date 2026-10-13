package com.smartparking.stats.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监控闭环运行时配置（对应 application.yml 的 {@code monitor.*} 节点）。
 *
 * <p>通过 Nacos / 本地 application.yml 即可调整 Prometheus 地址、bands 配置位置、
 * intent 输出目录与轮询间隔，无需改代码。</p>
 */
@ConfigurationProperties(prefix = "monitor")
public class MonitorProperties {

    /** 是否启用监控闭环调度；设为 false 可彻底关闭。 */
    private boolean enabled = true;

    /** Prometheus 基础地址，供 {@code PrometheusMetricSource} 查询。 */
    private String prometheusBaseUrl = "http://prometheus:9090";

    /** bands.yaml 位置，支持 {@code classpath:/bands.yaml} 或 {@code file:/config/bands.yaml}。 */
    private String bandsLocation = "classpath:/bands.yaml";

    /** 越界时生成的 intent 文件输出目录。 */
    private String outputDir = "./monitor-intents";

    /** 轮询间隔（毫秒）。 */
    private long fixedDelayMs = 60000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrometheusBaseUrl() {
        return prometheusBaseUrl;
    }

    public void setPrometheusBaseUrl(String prometheusBaseUrl) {
        this.prometheusBaseUrl = prometheusBaseUrl;
    }

    public String getBandsLocation() {
        return bandsLocation;
    }

    public void setBandsLocation(String bandsLocation) {
        this.bandsLocation = bandsLocation;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }
}
