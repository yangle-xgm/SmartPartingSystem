package com.smartparking.notify.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.smartparking.notify.config.RabbitMQConfig;
import com.smartparking.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 订单消息消费者：入场/出场通知（真实落地）
 *
 * 订单服务在入场/出场时发送 JSON 消息到 parking.order.exchange（routing: order.entry / order.exit）。
 * 本消费者解析消息 → 通过车辆服务反查车主 → 生成站内通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final NotificationService notificationService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 车辆服务地址（服务间直连，绕过网关） */
    @Value("${smart-parking.vehicle-url:http://localhost:8083}")
    private String vehicleUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @RabbitListener(queues = RabbitMQConfig.NOTIFY_QUEUE)
    public void handleOrderMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String body = new String(message.getBody());
            Map<String, Object> data = OBJECT_MAPPER.readValue(body, Map.class);
            String type = String.valueOf(data.getOrDefault("type", ""));
            String plateNo = String.valueOf(data.getOrDefault("plateNo", ""));

            // 反查车主（访客车辆无注册车主则跳过通知）
            Map<String, Object> vehicleInfo = lookupVehicleInfo(plateNo);
            if (vehicleInfo == null || vehicleInfo.get("ownerId") == null) {
                log.info("车辆{}未注册车主，跳过通知", plateNo);
                channel.basicAck(deliveryTag, false);
                return;
            }
            Long ownerId = Long.valueOf(String.valueOf(vehicleInfo.get("ownerId")));
            Integer vehicleType = vehicleInfo.get("type") == null ? 0 : Integer.valueOf(String.valueOf(vehicleInfo.get("type")));
            String vehicleLabel = vehicleType == 1 ? "您的访客车辆" : "您的车辆";

            if ("ENTRY".equals(type)) {
                String spaceNo = String.valueOf(data.getOrDefault("spaceNo", ""));
                String content = String.format("%s %s 已于 %s 入场，停放在 %s",
                        vehicleLabel, plateNo, LocalDateTime.now().format(TIME_FORMAT), spaceNo);
                notificationService.sendNotification(ownerId, "车辆入场通知", content, 1);
                log.info("已发送入场通知：userId={}, plateNo={}", ownerId, plateNo);
            } else if ("EXIT".equals(type)) {
                String duration = String.valueOf(data.getOrDefault("duration", ""));
                String amount = String.valueOf(data.getOrDefault("amount", ""));
                String content = String.format("%s %s 已出场，停车时长 %s 分钟，费用 ¥%s，请及时缴费",
                        vehicleLabel, plateNo, duration, amount);
                notificationService.sendNotification(ownerId, "车辆出场通知", content, 2);
                log.info("已发送出场通知：userId={}, plateNo={}", ownerId, plateNo);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理订单消息失败：{}", e.getMessage(), e);
            // 解析/业务失败：不重试（避免死循环），确认丢弃并记录
            channel.basicAck(deliveryTag, false);
        }
    }

    /**
     * 通过车辆服务反查车辆信息（含ownerId和type）
     */
    private Map<String, Object> lookupVehicleInfo(String plateNo) {
        if (plateNo == null || plateNo.isBlank() || "null".equals(plateNo)) {
            return null;
        }
        try {
            String url = vehicleUrl + "/api/vehicle/internal/info/" + plateNo;
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) {
                return null;
            }
            Map<String, Object> result = OBJECT_MAPPER.readValue(body, Map.class);
            Object code = result.get("code");
            if (code == null || !"200".equals(String.valueOf(code))) {
                return null;
            }
            return (Map<String, Object>) result.get("data");
        } catch (Exception e) {
            log.warn("反查车辆信息失败：plateNo={}, err={}", plateNo, e.getMessage());
            return null;
        }
    }
}
