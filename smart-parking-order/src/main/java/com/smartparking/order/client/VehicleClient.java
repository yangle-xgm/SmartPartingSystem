package com.smartparking.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 车辆服务客户端：订单列表按当前业主强制过滤
 */
@Slf4j
@Component
public class VehicleClient {

    @Value("${smart-parking.vehicle-url:http://localhost:8083}")
    private String vehicleUrl;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        return new RestTemplate(f);
    }

    /**
     * 按明文车牌查询车辆归属信息（内部接口），失败返回空Map
     */
    public java.util.Map<String, Object> getByPlateNo(String plateNo) {
        try {
            String url = vehicleUrl + "/api/vehicle/internal/info/" + java.net.URLEncoder.encode(plateNo, java.nio.charset.StandardCharsets.UTF_8);
            java.util.Map<String, Object> resp = restTemplate.getForObject(java.net.URI.create(url), java.util.Map.class);
            Object data = resp == null ? null : resp.get("data");
            if (data instanceof java.util.Map<?, ?> m) {
                java.util.Map<String, Object> info = new java.util.HashMap<>();
                m.forEach((k, v) -> info.put(String.valueOf(k), v));
                return info;
            }
        } catch (Exception e) {
            log.warn("按车牌查询车辆失败：plateNo={}, err={}", plateNo, e.getMessage());
        }
        return new java.util.HashMap<>();
    }

    /**
     * 查询业主的全部车辆ID；失败返回空列表（不阻断列表查询）
     */
    public List<Long> listVehicleIdsByOwner(Long ownerId) {
        try {
            String url = vehicleUrl + "/api/vehicle/internal/list-by-owner?ownerId=" + ownerId;
            Map<String, Object> resp = restTemplate.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            if (resp == null || resp.get("data") == null) {
                return List.of();
            }
            Object data = resp.get("data");
            if (data instanceof List<?> list) {
                return list.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("查询业主车辆列表失败 ownerId={}：{}", ownerId, e.getMessage());
            return List.of();
        }
    }
}
