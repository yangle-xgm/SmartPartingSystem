package com.smartparking.common.core.config;

import com.smartparking.common.core.constant.Constants;
import com.smartparking.common.core.http.InternalHttp;
import com.smartparking.common.core.web.InternalApiGuardFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * 内部接口守门器装配：仅对 Servlet 栈的服务生效（网关是 WebFlux，不加载本配置）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class InternalApiGuardConfig {

    @Value("${SP_INTERNAL_TOKEN:${smart-parking.internal-token:" + Constants.DEFAULT_INTERNAL_TOKEN + "}}")
    private String internalToken;

    @Bean
    public FilterRegistrationBean<InternalApiGuardFilter> internalApiGuardFilter() {
        String token = StringUtils.hasText(internalToken) ? internalToken : Constants.DEFAULT_INTERNAL_TOKEN;
        // 直连下游 /internal 的调用方统一从 InternalHttp 取令牌，保证两侧永远同源
        InternalHttp.setToken(token);

        FilterRegistrationBean<InternalApiGuardFilter> registration =
                new FilterRegistrationBean<>(new InternalApiGuardFilter(token));
        registration.addUrlPatterns("/*");
        registration.setName("internalApiGuardFilter");
        // 排在鉴权/编码等框架过滤器之前，内部接口不走任何业务逻辑
        registration.setOrder(Integer.MIN_VALUE + 100);
        return registration;
    }
}
