package com.smartparking.common.core.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前 HTTP 请求上下文工具：取真实客户端 IP、请求耗时。
 *
 * <p>前端功能测试 DEF-05：审计日志的 ip / costTime 恒为 null，安全审计没法定位来源与性能。
 * 这里统一从 {@code RequestContextHolder} 取当前请求：IP 优先 X-Forwarded-For（网关透传），
 * 耗时取 {@link RequestTimingFilter} 写入的请求开始时间。</p>
 */
public final class WebRequestUtils {

    /** 请求开始时间戳（毫秒），由 RequestTimingFilter 写入 */
    public static final String START_ATTR = "SP_REQUEST_START_MS";

    private WebRequestUtils() {
    }

    public static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    /**
     * 客户端 IP：X-Forwarded-For 首段 → X-Real-IP → remoteAddr，IPv6 本机地址归一为 127.0.0.1
     */
    public static String currentIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded) && !"unknown".equalsIgnoreCase(forwarded)) {
            int comma = forwarded.indexOf(',');
            return normalize((comma > 0 ? forwarded.substring(0, comma) : forwarded).trim());
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp) && !"unknown".equalsIgnoreCase(realIp)) {
            return normalize(realIp.trim());
        }
        return normalize(request.getRemoteAddr());
    }

    /**
     * 归一化 IP：IPv6 回环（{@code ::1 / 0:0:0:0:0:0:0:1}）与 IPv4 映射地址
     * （{@code ::ffff:127.0.0.1}）统一成 {@code 127.0.0.1}。
     *
     * <p>D11：原实现只归一 remoteAddr，经网关透传的 X-Forwarded-For 原样落库，
     * 导致同一台机器出现 127.0.0.1 与 0:0:0:0:0:0:0:1 两种记录。</p>
     */
    static String normalize(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        String value = ip.trim();
        // [::1]:8080 这类带端口/方括号的写法先剥壳
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) {
                value = value.substring(1, end);
            }
        }
        String lower = value.toLowerCase();
        if ("::1".equals(lower) || "0:0:0:0:0:0:0:1".equals(lower)) {
            return "127.0.0.1";
        }
        if (lower.startsWith("::ffff:")) {
            String v4 = value.substring(7);
            if (v4.indexOf(':') < 0) {
                return v4;
            }
        }
        return value;
    }

    /**
     * 请求已耗时（毫秒）；不在请求线程内返回 null
     */
    public static Long elapsedMillis() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object start = request.getAttribute(START_ATTR);
        if (start instanceof Long startMs) {
            return Math.max(0L, System.currentTimeMillis() - startMs);
        }
        return null;
    }
}
