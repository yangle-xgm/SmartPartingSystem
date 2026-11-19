package com.smartparking.order.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.order.config.PaymentMessageConfig;
import com.smartparking.order.service.ParkingOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付成功消息消费者：把「已支付」状态落到订单侧。
 *
 * <p>报告 P1-9：此前订单侧没有消费者，payment 发出的 payment.success 无人接收，
 * 只靠 payment 服务一次 REST 兜底；REST 失败（网络抖动/订单服务重启）就掉单，
 * 出现 payment_record 已支付而 parking_order 未支付，随后还会被超时任务反复处理。
 *
 * <p>幂等由 {@code ParkingOrderService#confirmPayment} 的条件更新保证：
 * MQ 与 REST 兜底同时到达也不会重复生效。
 *
 * <p><b>本模块 {@code acknowledge-mode: manual}（见 application.yml），必须显式 ack</b>，
 * 否则消息会永远停在 unacked 状态（实测队列 messages_ready=0 / unacked=1，看起来"没人消费"，
 * 服务重启后又被重投）。因此这里按异常性质分流：
 * <ul>
 *   <li><b>业务异常</b>（金额不一致、订单不存在、订单已取消/已退款）——重投一万次也不会变好，
 *       ack 掉并打 ERROR，转对账/人工处理，避免毒消息把队列堵死；</li>
 *   <li><b>可恢复异常</b>（DB/网络抖动）——basicNack(requeue=true) 回到队列，等恢复后自愈。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessConsumer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ParkingOrderService parkingOrderService;

    @RabbitListener(queues = PaymentMessageConfig.PAYMENT_SUCCESS_QUEUE)
    public void onPaymentSuccess(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Map<String, Object> payload;
        try {
            payload = OBJECT_MAPPER.readValue(message.getBody(), Map.class);
        } catch (Exception e) {
            // 报文本身不合法：重投也没用
            log.error("支付成功消息报文无法解析，已丢弃：{}", new String(message.getBody()), e);
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (payload == null || payload.get("orderId") == null) {
            log.warn("收到无 orderId 的支付成功消息，忽略：{}", payload);
            channel.basicAck(deliveryTag, false);
            return;
        }

        Long orderId = Long.valueOf(String.valueOf(payload.get("orderId")));
        BigDecimal amount = payload.get("amount") == null ? null
                : new BigDecimal(String.valueOf(payload.get("amount")));
        Object payNo = payload.get("payNo");

        try {
            parkingOrderService.confirmPayment(orderId, amount);
            log.info("MQ 确认支付成功：orderId={}, payNo={}", orderId, payNo);
            channel.basicAck(deliveryTag, false);
        } catch (BizException e) {
            // 流水已支付成功但订单侧没落上 = 账实不符，必须留 ERROR 让对账/人工介入
            log.error("[掉单待人工] MQ 确认支付业务失败，已丢弃该消息 orderId={}, payNo={}, amount={}：{}",
                    orderId, payNo, amount, e.getMessage());
            channel.basicAck(deliveryTag, false);
        } catch (DataAccessException e) {
            log.error("MQ 确认支付遇到数据库异常，重新入队 orderId={}：{}", orderId, e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        } catch (Exception e) {
            log.error("MQ 确认支付遇到未知异常，重新入队 orderId={}：{}", orderId, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
