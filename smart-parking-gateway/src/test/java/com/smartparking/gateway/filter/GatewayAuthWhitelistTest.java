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

    /**
     * 直接读 AuthGlobalFilter 的真实常量，避免测试里再抄一份导致漂移
     * （历史坑：抄写版少了后续补充的模式，测试全绿但线上仍可越权）。
     */
    @SuppressWarnings("unchecked")
    private static List<String> adminOnlyPatterns() {
        try {
            var field = AuthGlobalFilter.class.getDeclaredField("ADMIN_ONLY_PATTERNS");
            field.setAccessible(true);
            return (List<String>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 ADMIN_ONLY_PATTERNS 失败", e);
        }
    }

    private static final List<String> ADMIN_ONLY = adminOnlyPatterns();

    private final AntPathMatcher matcher = new AntPathMatcher();

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(p -> matcher.match(p, path));
    }

    /** 与 AuthGlobalFilter#isAdminOnly 同语义：支持 "METHOD /path" 限定方法 */
    private boolean isAdminOnly(String method, String path) {
        return ADMIN_ONLY.stream().anyMatch(pattern -> {
            int sp = pattern.indexOf(' ');
            if (sp > 0) {
                return method.equalsIgnoreCase(pattern.substring(0, sp))
                        && matcher.match(pattern.substring(sp + 1), path);
            }
            return matcher.match(pattern, path);
        });
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
        assertTrue(isAdminOnly("GET", "/api/audit/list"));
        assertTrue(isAdminOnly("GET", "/api/device/list"));
        assertTrue(isAdminOnly("GET", "/api/stats/income"));
        assertFalse(isAdminOnly("GET", "/api/parking/available"));
    }

    /**
     * 验收发现 P0 的回归：设备域原先写 `/api/device/*`（单段通配），
     * 漏了无子段的 POST /api/device 与两段路径 mock/status，业主可伪造抓拍入场。
     */
    @Test
    void deviceDomain_fullyAdminOnly() {
        assertTrue(isAdminOnly("POST", "/api/device"), "注册设备必须仅管理端");
        assertTrue(isAdminOnly("GET", "/api/device/1"), "设备详情必须仅管理端");
        assertTrue(isAdminOnly("POST", "/api/device/mock/camera"), "伪造抓拍必须仅管理端");
        assertTrue(isAdminOnly("POST", "/api/device/mock/exit-camera"));
        assertTrue(isAdminOnly("GET", "/api/device/status/overview"));
        assertTrue(isAdminOnly("POST", "/api/device/gate/open"));
    }

    /** 收紧必须精准：业主自助开关共享、查看自有车位不能被误伤 */
    @Test
    void ownerSelfService_notAdminOnly() {
        assertFalse(isAdminOnly("PUT", "/api/parking/space/1/share"));
        assertFalse(isAdminOnly("GET", "/api/parking/space/1"));
        assertFalse(isAdminOnly("GET", "/api/parking/space/my"));
        assertFalse(isAdminOnly("POST", "/api/parking/reserve"));
        // 同一路径的写操作仍归管理端
        assertTrue(isAdminOnly("PUT", "/api/parking/space/1"));
        assertTrue(isAdminOnly("DELETE", "/api/parking/space/1"));
        assertTrue(isAdminOnly("POST", "/api/parking/space"));
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
