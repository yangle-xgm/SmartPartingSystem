package com.smartparking.ai.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DoubaoChatServiceIntegrationTest {

    private static RestTemplate utf8RestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.getMessageConverters().stream()
                .filter(c -> c instanceof StringHttpMessageConverter)
                .forEach(c -> ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8));
        return rt;
    }

    @Test
    void chat_success_usesEndpointIdAsModel() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        boolean[] sawModel = {false};
        server.createContext("/api/v3/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"model\":\"ep-test-123\"")) sawModel[0] = true;
            String resp = "{\"choices\":[{\"message\":{\"content\":\"您可以在缴费页面支付\"}}]}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            DoubaoChatService svc = new DoubaoChatService(
                    utf8RestTemplate(), "test-key", "ep-test-123",
                    "http://localhost:" + port + "/api/v3/chat/completions", "sys");
            String ans = svc.chat(List.of(), "怎么缴费");
            assertTrue(sawModel[0], "请求体应包含 model=ep-test-123（接入点 ID，而非模型名）");
            assertEquals("您可以在缴费页面支付", ans);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chat_non2xx_throwsForDowngrade() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v3/chat/completions", exchange -> {
            String resp = "{\"error\":{\"message\":\"invalid api key\"}}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            DoubaoChatService svc = new DoubaoChatService(
                    utf8RestTemplate(), "bad", "ep-x",
                    "http://localhost:" + port + "/api/v3/chat/completions", "sys");
            assertThrows(RuntimeException.class, () -> svc.chat("你好"));
        } finally {
            server.stop(0);
        }
    }
}
