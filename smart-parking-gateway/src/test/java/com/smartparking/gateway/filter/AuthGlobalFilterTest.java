package com.smartparking.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关鉴权过滤器集成测试：直接驱动 {@link AuthGlobalFilter}（无需启动 Spring 上下文 / Nacos / Redis）。
 * 覆盖：白名单放行、缺 Token 401、合法 Token 注入下游头、业主访问管理端 403。
 */
class AuthGlobalFilterTest {

    private static final String SECRET = "SmartParkingSystemSecretKey2026VeryLongKey!!";

    private final AuthGlobalFilter filter = new AuthGlobalFilter();

    private String token(Long userId, String username, Integer role, Long communityId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .claim("communityId", communityId)
                .signWith(key)
                .compact();
    }

    @Test
    void whitelistedPath_passesWithoutToken() {
        AtomicBoolean passed = new AtomicBoolean(false);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/login"));
        filter.filter(exchange, e -> {
            passed.set(true);
            return Mono.empty();
        }).block();
        assertTrue(passed.get(), "白名单路径应直接放行");
    }

    @Test
    void protectedPath_withoutToken_returns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/info?userId=1"));
        filter.filter(exchange, e -> Mono.empty()).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedPath_withValidToken_injectsUserHeaders() {
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        String tk = token(1L, "admin", 1, 1L);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/info?userId=1")
                        .header("Authorization", "Bearer " + tk));
        filter.filter(exchange, e -> {
            downstream.set(e);
            return Mono.empty();
        }).block();
        assertNotNull(downstream.get(), "合法 Token 应通过过滤器");
        assertEquals("1", downstream.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("1", downstream.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void adminPath_withOwnerRole_returns403() {
        String tk = token(2L, "owner", 0, 1L); // role=0 业主
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/list")
                        .header("Authorization", "Bearer " + tk));
        filter.filter(exchange, e -> Mono.empty()).block();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }
}
