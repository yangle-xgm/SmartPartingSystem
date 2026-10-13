package com.smartparking.stats.monitor;

import com.smartparking.common.core.monitor.Band;
import com.smartparking.common.core.monitor.PrometheusMetricSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PrometheusMetricSource} 纯逻辑单测：用 JDK 自带 {@link HttpServer} 模拟 Prometheus 查询接口，
 * 验证指标解析、空结果与连接失败的容错语义（不加载 Spring 上下文）。
 */
class PrometheusMetricSourceTest {

    private HttpServer server;
    private String baseUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", exchange -> {
            String resp = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\","
                    + "\"result\":[{\"metric\":{\"__name__\":\"gateway_p99_latency_ms\"},"
                    + "\"value\":[1700000000.123,\"812.5\"]}]}}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private Band band(String metric) {
        Band b = new Band();
        b.setMetric(metric);
        return b;
    }

    @Test
    void parsesFirstSampleValue() {
        PrometheusMetricSource src = new PrometheusMetricSource(baseUrl, httpClient);
        assertEquals(812.5, src.sample(band("gateway_p99_latency_ms")), 0.001);
    }

    @Test
    void emptyResultReturnsNaN() throws Exception {
        stop();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", exchange -> {
            String resp = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        PrometheusMetricSource src = new PrometheusMetricSource(baseUrl, httpClient);
        assertTrue(Double.isNaN(src.sample(band("unknown_metric"))));
    }

    @Test
    void connectionFailureReturnsNaN() {
        // 指向未监听的端口，触发连接异常
        PrometheusMetricSource src = new PrometheusMetricSource("http://127.0.0.1:1", httpClient);
        assertTrue(Double.isNaN(src.sample(band("any"))));
    }

    @Test
    void non200ResponseReturnsNaN() throws Exception {
        stop();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", exchange ->
                exchange.sendResponseHeaders(503, -1));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        PrometheusMetricSource src = new PrometheusMetricSource(baseUrl, httpClient);
        assertTrue(Double.isNaN(src.sample(band("any"))));
    }
}
