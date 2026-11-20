package com.smartparking.order.client;

import com.smartparking.common.core.http.InternalHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * 通知服务客户端：订单侧只需要"待支付催缴"这一个能力。
 * 催缴是尽力而为，失败只记日志，不阻断扫描任务后续订单。
 */
@Slf4j
@Component
public class NotifyClient {

    @Value("${smart-parking.notify-url:http://localhost:8086}")
    private String notifyUrl;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        return InternalHttp.attach(new RestTemplate(f));
    }

    /** 发送缴费提醒，返回是否投递成功（决定要不要置 remind_time） */
    public boolean sendPaymentReminder(Long userId, String orderNo, String amount) {
        if (userId == null || orderNo == null) {
            return false;
        }
        try {
            String url = notifyUrl + "/api/notify/internal/payment?userId=" + userId
                    + "&orderNo=" + enc(orderNo) + "&amount=" + enc(amount);
            restTemplate.postForObject(URI.create(url), HttpEntity.EMPTY, java.util.Map.class);
            return true;
        } catch (Exception e) {
            log.warn("发送待支付提醒失败 orderNo={}, err={}", orderNo, e.getMessage());
            return false;
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
