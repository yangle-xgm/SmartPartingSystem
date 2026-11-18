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

    /**
     * 直接读 AuthGlobalFilter 的真实常量，避免测试里再抄一份导致漂移
     * （历史坑：抄写版少了后续补充的模式，测试全绿但线上仍可越权）。
     */
    @SuppressWarnings("unchecked")
    private static List<String> readPatterns(String fieldName) {
        try {
            var field = AuthGlobalFilter.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (List<String>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 " + fieldName + " 失败", e);
        }
    }

    private static final List<String> WHITE_LIST = readPatterns("WHITE_LIST");

    private static final List<String> ADMIN_ONLY = readPatterns("ADMIN_ONLY_PATTERNS");

    private static final List<String> INTERNAL_PATTERNS = readPatterns("INTERNAL_PATTERNS");

    private final AntPathMatcher matcher = new AntPathMatcher();

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(p -> matcher.match(p, path));
    }

    private boolean isInternalPath(String path) {
        return INTERNAL_PATTERNS.stream().anyMatch(p -> matcher.match(p, path));
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
        // 头像图片：<img> 不携带 Authorization，必须白名单放行。
        // 目录穿越由服务端文件名校验兜底（AVATAR_FILE_PATTERN 不含分隔符）。
        assertTrue(isWhiteListed("/api/user/avatar/6f1c2f7e-1234-4abc-9def-0123456789ab.png"));
        // 但头像白名单不能顺带放开整个用户域
        assertFalse(isWhiteListed("/api/user/update"));
    }

    /**
     * 内部接口不再走白名单，而是要求 X-Internal-Token。
     * 原测试自己抄的 WHITE_LIST 里还留着已被移除的 {@code /api/**&#47;internal/**}，
     * 于是「内部接口已加固」这件事从来没被断言过 —— 与设备域越权是同一个漂移问题。
     */
    @Test
    void internalEndpoints_needInternalToken() {
        assertTrue(isInternalPath("/api/order/internal/payment-timeout"));
        assertTrue(isInternalPath("/api/payment/internal/refund"));
        assertFalse(isWhiteListed("/api/order/internal/payment-timeout"));
        assertFalse(isInternalPath("/api/order/list"));
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
