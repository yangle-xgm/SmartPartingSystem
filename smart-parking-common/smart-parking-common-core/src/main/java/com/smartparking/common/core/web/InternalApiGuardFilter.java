package com.smartparking.common.core.web;

import com.smartparking.common.core.constant.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部接口守门器：每个微服务自己校验 {@code /api/**&#47;internal/**} 的 {@code X-Internal-Token}。
 *
 * <p>报告 P1-2：网关虽然拦了内部接口，但 8081-8090 各端口可被直接访问，
 * 绕过网关即可伪造支付、抬杆、改订单归属。网关校验 + 服务端校验＝双保险。
 */
public class InternalApiGuardFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public InternalApiGuardFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.contains("/internal/")) {
            String provided = request.getHeader(Constants.INTERNAL_TOKEN_HEADER);
            if (!expectedToken.equals(provided)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"内部接口禁止直接访问\",\"data\":null}");
                response.getWriter().flush();
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 便于单测构造固定 token */
    public static InternalApiGuardFilter withToken(String token) {
        return new InternalApiGuardFilter(token);
    }
}
