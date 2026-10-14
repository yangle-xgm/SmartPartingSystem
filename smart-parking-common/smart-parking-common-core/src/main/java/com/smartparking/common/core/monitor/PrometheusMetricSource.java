package com.smartparking.common.core.monitor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * 基于 Prometheus HTTP 查询 API 的真实指标源（维护阶段监控闭环的“读数入口”）。
 *
 * <p>实现 {@link MetricSource}：对每条 Band，用其 {@code metric} 名向 Prometheus 的
 * {@code /api/v1/query} 发起即时查询，解析返回向量中第一个样本的当前值。</p>
 *
 * <p><b>容错语义</b>：Prometheus 不可达 / 查询失败 / 该指标暂无数据（如尚未埋点）时，
 * 一律返回 {@link Double#NaN}。配合 {@link BandEvaluator} 中 {@code NaN < min} 与
 * {@code NaN > max} 均恒为 false 的特性，可保证“无数据不误报”，避免尚未埋点的服务
 * 因查询空而错误触发越界告警。</p>
 *
 * <p>不依赖任何额外框架，使用 JDK 11+ 自带的 {@link HttpClient}，便于在单测中注入
 * 假客户端验证解析逻辑。</p>
 */
public class PrometheusMetricSource implements MetricSource {

    private final String baseUrl;
    private final HttpClient httpClient;

    /**
     * @param baseUrl Prometheus 基础地址，如 {@code http://prometheus:9090} 或 {@code http://localhost:9090}
     */
    public PrometheusMetricSource(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    /**
     * 注入自定义 {@link HttpClient} 的构造（供单测注入假客户端，或生产环境传入带超时/代理配置的客户端）。
     */
    public PrometheusMetricSource(String baseUrl, HttpClient httpClient) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public double sample(Band band) {
        String metric = band.getMetric();
        if (metric == null || metric.isBlank()) {
            return Double.NaN;
        }
        String query = URLEncoder.encode(metric, StandardCharsets.UTF_8);
        String url = baseUrl + "/api/v1/query?query=" + query;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Double.NaN;
            }
            return parseFirstValue(response.body());
        } catch (Exception ignored) {
            // Prometheus 未就绪 / 超时 / 网络异常：保守返回 NaN，交给 BandEvaluator 视为“无数据不越界”
            return Double.NaN;
        }
    }

    /**
     * 从 Prometheus instant query 的响应体解析第一个样本的当前值。
     *
     * <p>响应形如：
     * <pre>{@code
     * {"status":"success","data":{"resultType":"vector",
     *   "result":[{"metric":{...},"value":[1700000000.123,"12.5"]}]}}
     * }</pre>
     * 提取 {@code value} 数组的第二个元素（指标当前读数）。</p>
     *
     * @param body Prometheus 响应 JSON
     * @return 解析到的数值；无数据 / 格式异常时返回 {@link Double#NaN}
     */
    static double parseFirstValue(String body) {
        if (body == null || body.isBlank()) {
            return Double.NaN;
        }
        int valueIdx = body.indexOf("\"value\":[");
        if (valueIdx < 0) {
            return Double.NaN;
        }
        int arrStart = body.indexOf('[', valueIdx);
        int comma = body.indexOf(',', arrStart);
        int arrEnd = body.indexOf(']', comma);
        if (comma < 0 || arrEnd < 0) {
            return Double.NaN;
        }
        String num = body.substring(comma + 1, arrEnd).trim().replace("\"", "");
        try {
            return Double.parseDouble(num);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
