package com.smartparking.vehicle.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.core.web.WebRequestUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class AuditClient {

    @Value("${smart-parking.audit-url:http://localhost:8081}")
    private String auditUrl;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate;

    public AuditClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(f);
    }

    /** 审计发送线程池：异步发送，避免拖慢入场/出场等主流程 */
    private static final ExecutorService AUDIT_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "audit-sender");
        t.setDaemon(true);
        return t;
    });

    /**
     * 记录审计日志。
     *
     * <p>DEF-05：IP 与耗时必须在业务线程（仍持有 RequestContextHolder）里取值，
     * 再交给异步线程发送；否则子线程拿不到当前请求，只能写 null。</p>
     */
    public void record(Long userId, String username, String operation, String method,
                       Object params, boolean success) {
        final String ip = WebRequestUtils.currentIp();
        final Long costTime = WebRequestUtils.elapsedMillis();
        AUDIT_POOL.execute(() -> doRecord(userId, username, operation, method, params, success, ip, costTime));
    }

    private void doRecord(Long userId, String username, String operation, String method,
                          Object params, boolean success, String ip, Long costTime) {
        try {
            String json = toJson(params);
            String url = auditUrl + "/api/audit/internal/record"
                    + "?operation=" + encode(operation)
                    + "&method=" + encode(method)
                    + "&isSuccess=" + (success ? 1 : 0);
            if (userId != null) url += "&userId=" + userId;
            if (StringUtils.hasText(username)) url += "&username=" + encode(username);
            if (StringUtils.hasText(json)) url += "&params=" + encode(json);
            if (StringUtils.hasText(ip)) url += "&ip=" + encode(ip);
            if (costTime != null) url += "&costTime=" + costTime;
            restTemplate.postForEntity(java.net.URI.create(url), HttpEntity.EMPTY, String.class);
        } catch (Exception e) {
            log.warn("记录操作日志失败：operation={}, err={}", operation, e.getMessage());
        }
    }

    private String toJson(Object params) {
        if (params == null) return null;
        if (params instanceof String s) return s;
        try {
            return MAPPER.writeValueAsString(params);
        } catch (Exception e) {
            return String.valueOf(params);
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
