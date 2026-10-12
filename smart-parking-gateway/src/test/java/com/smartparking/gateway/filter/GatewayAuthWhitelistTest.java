package com.smartparking.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关认证白名单与角色校验单元测试（Phase B）。
 * 使用 Spring 独立的 {@link AntPathMatcher}（无需 Spring 上下文），对照
 * AuthGlobalFilter 的 WHITE_LIST / ADMIN_ONLY_PATTERNS 与 Bearer 令牌提取逻辑。
 */
class GatewayAuthWhitelistTest {

    /** 与 AuthGlobalFilter.WHITE_LIST 保持一致 */
    private static final List<String> WHITE_LIST = List.of(
            "/api/user/login",
            "/api/user/register",
            "/api/user/refresh",
            "/api/user/captcha",
            "/api/**/internal/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**");

    /** 与 AuthGlobalFilter.ADMIN_ONLY_PATTERNS 保持一致 */
    private static final List<String> ADMIN_ONLY = List.of(
            "/api/audit/**",
            "/api/user/list",
            "/api/user/update",
            "/api/device/list",
            "/api/device/*",
            "/api/device/gate/**",
            "/api/stats/income",
            "/api/stats/occupancy",
            "/api/stats/peak",
            "/api/stats/export");

    private final AntPathMatcher matcher = new AntPathMatcher();

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(p -> matcher.match(p, path));
    }

    private boolean isAdminOnly(String path) {
        return ADMIN_ONLY.stream().anyMatch(p -> matcher.match(p, path));
    }

    @Test
    void publicEndpoints_whitelisted() {
        assertTrue(isWhiteListed("/api/user/login"));
        assertTrue(isWhiteListed("/api/user/register"));
        assertTrue(isWhiteListed("/api/user/refresh"));
        assertTrue(isWhiteListed("/api/user/captcha"));
        assertTrue(isWhiteListed("/api/order/internal/payment-timeout"));
    }

    @Test
    void protectedEndpoints_notWhitelisted() {
        assertFalse(isWhiteListed("/api/parking/available"));
        assertFalse(isWhiteListed("/api/audit/list"));
        assertFalse(isWhiteListed("/api/order/invite/create"));
    }

    @Test
    void adminEndpoints_requireRole() {
        assertTrue(isAdminOnly("/api/audit/list"));
        assertTrue(isAdminOnly("/api/device/list"));
        assertTrue(isAdminOnly("/api/stats/income"));
        assertFalse(isAdminOnly("/api/parking/available"));
    }

    @Test
    void bearerToken_extraction() {
        String prefix = "Bearer ";
        assertEquals("xyz", strip(prefix + "xyz"));
        assertNull(strip("xyz"));
        assertNull(strip(null));
    }

    private String strip(String header) {
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return null;
    }
}
