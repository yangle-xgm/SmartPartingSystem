package com.smartparking.common.core.http;

import com.smartparking.common.core.constant.Constants;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 服务间 HTTP 调用的内部令牌注入器。
 *
 * <p>报告 P1-2：网关对 {@code /api/**&#47;internal/**} 已强制 {@code X-Internal-Token}，
 * 但各微服务端口（8081-8090）本身完全裸奔，直连 {@code :8085/api/payment/internal/success}
 * 就能伪造支付、直连 {@code :8087/api/device/gate/open} 就能抬杆。现在服务侧由
 * {@code InternalApiGuardFilter} 校验同一令牌，因此所有**直连**下游 /internal 的调用方
 * 都必须带上该 Header —— 统一走 {@link #attach(RestTemplate)}，避免各处手抄。
 */
public final class InternalHttp {

    /** 与网关 gateway.internal-token 的缺省值一致；生产统一由 SP_INTERNAL_TOKEN 覆盖 */
    private static final String DEFAULT_TOKEN = "SmartParking-Internal-2026";

    private static volatile String token = DEFAULT_TOKEN;

    private InternalHttp() {
    }

    /** 启动期由 SecuritySecretsConfig 注入真实令牌 */
    public static void setToken(String newToken) {
        if (StringUtils.hasText(newToken)) {
            token = newToken;
        }
    }

    public static String currentToken() {
        return token;
    }

    /**
     * 给 RestTemplate 挂上内部令牌拦截器，返回同一实例便于链式使用。
     */
    public static RestTemplate attach(RestTemplate template) {
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(Constants.INTERNAL_TOKEN_HEADER, token);
            return execution.execute(request, body);
        });
        return template;
    }
}
