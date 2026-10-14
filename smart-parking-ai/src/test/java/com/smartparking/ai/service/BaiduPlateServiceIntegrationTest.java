package com.smartparking.ai.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用 JDK 内置 HttpServer 伪造百度「token 接口」与「车牌识别接口」，验证完整调用链路：
 * 1) 正常流程：换 token -> 带 token 识别 -> 返回正确车牌；
 * 2) 异常流程：token 接口返回 error -> recognize 抛 RuntimeException（由上层降级）。
 */
public class BaiduPlateServiceIntegrationTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void recognizeSuccess() throws Exception {
        server.createContext("/oauth/2.0/token", exchange -> {
            String body = "{\"access_token\":\"fake-token-123\",\"expires_in\":2592000}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/rest/2.0/ocr/v1/license_plate", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            assertTrue(query.contains("access_token=fake-token-123"), "请求应携带 access_token");
            byte[] req = exchange.getRequestBody().readAllBytes();
            String reqStr = new String(req, StandardCharsets.UTF_8);
            assertTrue(reqStr.contains("image="), "请求体应包含 image 字段");
            String body = "{\"words_result\":[{\"number\":\"京C66666\",\"color\":\"green\"," +
                    "\"probability\":[0.999,0.998,0.999,0.997,0.999,0.998,0.999]}],\"log_id\":1}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        BaiduPlateService service = new BaiduPlateService("ak", "sk",
                base + "/oauth/2.0/token", base + "/rest/2.0/ocr/v1/license_plate", new RestTemplate());
        assertTrue(service.isConfigured());

        PlateRecognition r = service.recognize("fake-image-bytes".getBytes(StandardCharsets.UTF_8));
        assertEquals("京C66666", r.number());
        assertEquals("green", r.color());
        assertEquals(99.7, r.confidence(), 0.001);
    }

    @Test
    void tokenErrorPropagatesAsRuntime() {
        server.createContext("/oauth/2.0/token", exchange -> {
            String body = "{\"error\":\"invalid_client\",\"error_description\":\"unknown client\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        BaiduPlateService service = new BaiduPlateService("ak", "sk",
                base + "/oauth/2.0/token", base + "/rest/2.0/ocr/v1/license_plate", new RestTemplate());
        assertThrows(RuntimeException.class,
                () -> service.recognize("img".getBytes(StandardCharsets.UTF_8)),
                "鉴权失败应抛 RuntimeException 供上层降级");
    }
}
