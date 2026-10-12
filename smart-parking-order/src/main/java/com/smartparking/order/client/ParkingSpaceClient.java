package com.smartparking.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 车位服务客户端：订单入场/出场/取消时联动车位状态
 */
@Slf4j
@Component
public class ParkingSpaceClient {

    @Value("${smart-parking.parking-url:http://localhost:8082}")
    private String parkingUrl;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        return new RestTemplate(f);
    }

    /**
     * 更新车位状态（0空闲 1占用）
     * 失败仅记录日志，不阻断订单主流程
     */
    public void updateStatus(Long spaceId, Integer status) {
        if (spaceId == null) {
            return;
        }
        try {
            String url = parkingUrl + "/api/parking/internal/space/" + spaceId + "/status?status=" + status;
            restTemplate.put(url, null);
            log.info("车位状态联动：spaceId={}, status={}", spaceId, status);
        } catch (Exception e) {
            log.warn("车位状态联动失败：spaceId={}, status={}, err={}", spaceId, status, e.getMessage());
        }
    }

    /**
     * 查询车位共享/业主信息（内部接口），失败返回空Map
     */
    public java.util.Map<String, Object> getSpaceInfo(Long spaceId) {
        if (spaceId == null) {
            return new java.util.HashMap<>();
        }
        try {
            String url = parkingUrl + "/api/parking/internal/space-info/" + spaceId;
            org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>> type =
                    new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {};
            java.util.Map<String, Object> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, null, type).getBody();
            Object data = resp == null ? null : resp.get("data");
            if (data instanceof java.util.Map<?, ?> m) {
                java.util.Map<String, Object> info = new java.util.HashMap<>();
                m.forEach((k, v) -> info.put(String.valueOf(k), v));
                return info;
            }
        } catch (Exception e) {
            log.warn("查询车位信息失败：spaceId={}, err={}", spaceId, e.getMessage());
        }
        return new java.util.HashMap<>();
    }

    /**
     * 原子占用车位（仅空闲/预约态可占用），返回是否成功
     */
    public boolean claimSpace(Long spaceId) {
        try {
            String url = parkingUrl + "/api/parking/internal/space/" + spaceId + "/claim";
            org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>> type =
                    new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {};
            java.util.Map<String, Object> resp = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.POST, null, type).getBody();
            return Boolean.TRUE.equals(resp == null ? null : resp.get("data"));
        } catch (Exception e) {
            log.warn("车位占用失败：spaceId={}, err={}", spaceId, e.getMessage());
            return false;
        }
    }
}
