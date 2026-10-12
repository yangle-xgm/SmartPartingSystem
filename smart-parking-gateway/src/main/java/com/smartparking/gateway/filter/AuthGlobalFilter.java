package com.smartparking.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 全局认证过滤器
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String SECRET = "SmartParkingSystemSecretKey2026VeryLongKey!!";
    private static final String TOKEN_PREFIX = "Bearer ";

    /** 白名单路径（无需认证） */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/user/login",
            "/api/user/register",
            "/api/user/refresh",
            "/api/user/captcha",
            "/api/**/internal/**",   // 服务间内部调用（微服务间通过网关互调时免鉴权，由调用方服务鉴权）
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**"
    );

    /** 仅管理端（物业 role=1 / 超管 role=2）可访问的路径 */
    private static final List<String> ADMIN_ONLY_PATTERNS = Arrays.asList(
            "/api/audit/**",
            "/api/user/list",
            "/api/user/update",
            "/api/device/list",
            "/api/device/*",
            "/api/device/gate/**",
            "/api/stats/income",
            "/api/stats/occupancy",
            "/api/stats/peak",
            "/api/stats/export"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 获取Token
        String token = getTokenFromRequest(request);
        if (!StringUtils.hasText(token)) {
            return unauthorizedResponse(exchange, "未提供认证令牌");
        }

        // 解析Token
        try {
            Claims claims = parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);
            Integer role = claims.get("role", Integer.class);
            Long communityId = claims.get("communityId", Long.class);

            // 将用户信息传递给下游服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Name", username)
                    .header("X-User-Role", String.valueOf(role))
                    .header("X-Community-Id", communityId == null ? "1" : String.valueOf(communityId))
                    .build();

            // 管理端接口角色校验：业主(role=0)不允许访问
            if (isAdminOnly(path) && (role == null || role == 0)) {
                return forbiddenResponse(exchange, "无权限访问管理端资源");
            }

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            return unauthorizedResponse(exchange, "认证令牌已过期");
        } catch (Exception e) {
            return unauthorizedResponse(exchange, "认证令牌无效");
        }
    }

    @Override
    public int getOrder() {
        return -100; // 最高优先级
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isAdminOnly(String path) {
        return ADMIN_ONLY_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String getTokenFromRequest(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"code\":403,\"message\":\"%s\",\"data\":null}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
