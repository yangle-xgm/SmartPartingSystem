package com.smartparking.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Sentinel 限流阻断处理：返回 HTTP 429 + JSON
 */
@Configuration
public class SentinelBlockConfig {

    @Bean
    public BlockRequestHandler blockRequestHandler() {
        return (exchange, t) -> ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}"), String.class);
    }
}
