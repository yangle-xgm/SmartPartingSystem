package com.smartparking.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

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

    @org.springframework.scheduling.annotation.Async
    public void record(Long userId, String username, String operation, String method,
                       Object params, boolean success) {
        try {
            String json = toJson(params);
            String url = auditUrl + "/api/audit/internal/record"
                    + "?operation=" + encode(operation)
                    + "&method=" + encode(method)
                    + "&isSuccess=" + (success ? 1 : 0);
            if (userId != null) url += "&userId=" + userId;
            if (StringUtils.hasText(username)) url += "&username=" + encode(username);
            if (StringUtils.hasText(json)) url += "&params=" + encode(json);
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
