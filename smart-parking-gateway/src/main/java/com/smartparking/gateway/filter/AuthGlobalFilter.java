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

    /** 内部调用令牌 Header：/internal/** 仅允许携带该 Header 的请求通过（服务间约定，外部用户 token 无效） */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** 白名单路径（无需认证） */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/user/login",
            "/api/user/register",
            "/api/user/refresh",
            "/api/user/captcha",
            // 注意：原 /api/**/internal/** 白名单已移除（E2E 报告 P0-1：业主可经网关直接调内部接口退款/伪造支付）。
            // /internal/** 现要求 X-Internal-Token 校验，见 filter() 内部分支。
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**"
    );

    /** 内部接口路径模式：命中则必须携带 X-Internal-Token（服务间调用约定），不接受普通用户 JWT */
    private static final List<String> INTERNAL_PATTERNS = Arrays.asList(
            "/api/**/internal/**"
    );

    /** 仅管理端（物业 role=1 / 超管 role=2）可访问的路径。支持 "METHOD /path" 形式限定写操作。 */
    private static final List<String> ADMIN_ONLY_PATTERNS = Arrays.asList(
            "/api/audit/**",
            "/api/user/list",
            "/api/device/list",
            "/api/device/*",
            "/api/device/gate/**",
            "/api/stats/income",
            "/api/stats/occupancy",
            "/api/stats/peak",
            "/api/stats/export",
            // E2E 报告 P0-2/P0-3：以下管理端接口此前业主可直接调用
            "/api/notify/admin/**",            // 群发通知（原业主可调，越权）
            "POST /api/parking/space",         // 新增车位
            "PUT /api/parking/space/**",       // 修改车位（含 share 分享配置）
            "DELETE /api/parking/space/**",    // 删除车位
            "GET /api/notify/feedback/all",    // 读全量反馈
            "POST /api/notify/feedback/*/reply" // 回复反馈
            // 注意：/api/user/update 不在管理端白名单内 —— 业主也需要修改本人资料，
            // 服务端已按 X-User-Role=0 强制把 dto.id 覆盖为当前登录人，避免越权改他人。
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 内部令牌配置值：生产环境经 NACOS/环境变量覆盖；缺省为开发联调固定值 */
    @Value("${gateway.internal-token:SmartParking-Internal-2026}")
    private String internalToken;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 内部接口（/api/**/internal/**）：必须携带 X-Internal-Token 才放行。
        // 该 Header 仅服务间调用方知道，外部用户即使持有合法 JWT 也无法通过 —— E2E 报告 P0-1。
        if (isInternalPath(path)) {
            String headerToken = request.getHeaders().getFirst(INTERNAL_TOKEN_HEADER);
            if (StringUtils.hasText(headerToken) && internalToken.equals(headerToken)) {
                return chain.filter(exchange);
            }
            return forbiddenResponse(exchange, "内部接口禁止外部访问");
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
            if (isAdminOnly(request) && (role == null || role == 0)) {
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

    private boolean isInternalPath(String path) {
        return INTERNAL_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 管理端路径匹配：支持 "METHOD /path"（限定 HTTP 方法）与 "/path"（任意方法）两种形式。
     * 例如 "POST /api/parking/space" 只拦新建，不拦 GET 列表（业主也要看车位）。
     */
    private boolean isAdminOnly(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        String method = request.getMethod() == null ? "" : request.getMethod().name();
        return ADMIN_ONLY_PATTERNS.stream().anyMatch(pattern -> {
            int sp = pattern.indexOf(' ');
            if (sp > 0) {
                return method.equalsIgnoreCase(pattern.substring(0, sp))
                        && pathMatcher.match(pattern.substring(sp + 1), path);
            }
            return pathMatcher.match(pattern, path);
        });
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
        // 与 Result<T> 契约一致：补 timestamp 字段（E2E 报告 P2-7）
        String body = String.format("{\"code\":401,\"message\":\"%s\",\"data\":null,\"timestamp\":%d}",
                message, System.currentTimeMillis());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"code\":403,\"message\":\"%s\",\"data\":null,\"timestamp\":%d}",
                message, System.currentTimeMillis());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
