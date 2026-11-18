package com.smartparking.vehicle.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 计费规则客户端。
 *
 * <p>月卡单价维护在 order 服务的 {@code billing_rule} 表（vehicle_type=2 为月租车），
 * 车辆服务不直接跨库读表，改为调用 order 服务的只读接口取价。
 *
 * <p>取不到时回退到配置的默认价：月卡充值不能因为 order 服务抖动而整体不可用，
 * 但回退值会写入订单的 unit_price 快照，事后可追溯。
 */
@Slf4j
@Component
public class BillingClient {

    @Value("${smart-parking.order-url:http://localhost:8084}")
    private String orderUrl;

    /** 兜底月单价（与 billing_rule 种子数据一致） */
    @Value("${smart-parking.monthly-default-price:300.00}")
    private BigDecimal defaultMonthlyPrice;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate;

    /** 规则极少变动，进程内缓存 5 分钟，避免每次进页面都跨服务调用 */
    private final Map<String, CachedPrice> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    public BillingClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(f);
    }

    private record CachedPrice(BigDecimal price, long at) {
    }

    /**
     * 取月卡月单价。
     *
     * @return 计费规则中 vehicle_type=2 的 monthly_price；规则缺失或调用失败时回退默认价
     */
    public BigDecimal monthlyPrice() {
        CachedPrice cached = cache.get("monthly");
        if (cached != null && System.currentTimeMillis() - cached.at() < CACHE_TTL_MS) {
            return cached.price();
        }
        BigDecimal price = fetchMonthlyPrice();
        cache.put("monthly", new CachedPrice(price, System.currentTimeMillis()));
        return price;
    }

    private BigDecimal fetchMonthlyPrice() {
        try {
            // 传 URI 而不是 String：URL 里若含中文/特殊字符，String 重载会被二次编码
            String body = restTemplate.getForObject(
                    URI.create(orderUrl + "/api/order/billing-rules"), String.class);
            if (body == null || body.isBlank()) {
                return defaultMonthlyPrice;
            }
            JsonNode root = MAPPER.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return defaultMonthlyPrice;
            }
            for (JsonNode rule : data) {
                int vehicleType = rule.path("vehicleType").asInt(-1);
                int status = rule.path("status").asInt(1);
                JsonNode monthly = rule.path("monthlyPrice");
                if (vehicleType == 2 && status == 1 && !monthly.isNull() && !monthly.isMissingNode()) {
                    BigDecimal p = monthly.decimalValue();
                    if (p != null && p.compareTo(BigDecimal.ZERO) > 0) {
                        return p;
                    }
                }
            }
            log.warn("计费规则中未找到月租车价格（vehicle_type=2），使用默认价 {}", defaultMonthlyPrice);
            return defaultMonthlyPrice;
        } catch (Exception e) {
            log.warn("调用 order 服务取月卡价失败，使用默认价 {}：{}", defaultMonthlyPrice, e.getMessage());
            return defaultMonthlyPrice;
        }
    }
}
