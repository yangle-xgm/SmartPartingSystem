package com.smartparking.notify.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.smartparking.notify.config.RabbitMQConfig;
import com.smartparking.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 支付消息消费者：支付成功 → 缴费成功通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentMessageConsumer {

    private final NotificationService notificationService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_NOTIFY_QUEUE)
    public void handlePaymentMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String body = new String(message.getBody());
            Map<String, Object> data = OBJECT_MAPPER.readValue(body, Map.class);

            Long userId = data.get("userId") == null ? null : Long.valueOf(String.valueOf(data.get("userId")));
            if (userId == null) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            String payNo = String.valueOf(data.getOrDefault("payNo", ""));
            String amount = String.valueOf(data.getOrDefault("amount", ""));
            String content = String.format("订单支付成功：流水号 %s，金额 %s 元，支付时间 %s",
                    payNo, amount, LocalDateTime.now().format(TIME_FORMAT));
            notificationService.sendNotification(userId, "缴费成功通知", content, 3);
            log.info("已发送缴费成功通知：userId={}, payNo={}", userId, payNo);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理支付消息失败：{}", e.getMessage(), e);
            channel.basicAck(deliveryTag, false);
        }
    }
}
