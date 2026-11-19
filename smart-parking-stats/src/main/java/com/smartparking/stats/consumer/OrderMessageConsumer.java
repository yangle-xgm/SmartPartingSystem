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

        } catch (org.springframework.dao.DataAccessException e) {
            // 只有基础设施抖动才值得重投
            log.error("处理订单消息遇到数据库异常，重新入队：{}", e.getMessage(), e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        } catch (Exception e) {
            // 报告"MQ 失败两极分化"里的死循环面：原先任何异常都 requeue=true，
            // 报文不合法 / 字段缺失这类永久性错误会让同一条消息无限重投，
            // 把统计消费者打满并挡住后续消息。统计属于可丢的场景，ack 掉并告警即可。
            log.error("处理订单消息失败（永久性错误，已丢弃）：{}，body={}",
                    e.getMessage(), new String(message.getBody()), e);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }
}
