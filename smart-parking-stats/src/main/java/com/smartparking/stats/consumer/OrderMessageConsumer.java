package com.smartparking.stats.consumer;

import com.rabbitmq.client.Channel;
import com.smartparking.stats.config.RabbitMQConfig;
import com.smartparking.stats.service.ParkingStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 订单消息消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final ParkingStatsService parkingStatsService;

    @RabbitListener(queues = RabbitMQConfig.STATS_QUEUE)
    public void handleOrderMessage(Message message, Channel channel) throws IOException {
        try {
            String body = new String(message.getBody());
            log.info("收到订单消息：{}", body);

            // 解析消息类型
            @SuppressWarnings("unchecked")
            Map<String, Object> data = new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Map.class);
            String type = (String) data.get("type");

            // 更新统计数据
            parkingStatsService.updateStats(type, data);

            // 手动确认
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            log.error("处理订单消息失败：{}", e.getMessage(), e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
