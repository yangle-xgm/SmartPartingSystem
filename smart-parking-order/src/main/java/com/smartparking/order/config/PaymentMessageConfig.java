package com.smartparking.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付成功消息的订单侧消费拓扑。
 *
 * <p>报告 P1-9：payment 服务发布的是 exchange {@code parking.payment.exchange} /
 * routingKey {@code payment.success}，而订单模块此前只声明了
 * {@code parking.order.exchange} + {@code order.payment}，两边对不上 ——
 * 所谓"MQ 通知订单确认支付"是条死路径，实际只靠一次 REST 兜底，REST 失败即掉单：
 * payment_record 已支付、parking_order 仍欠费，随后还会被超时任务处理。
 *
 * <p>这里补齐订单侧的队列与绑定，让 MQ 成为主链路、REST 仅作兜底（两侧均幂等）。
 */
@Slf4j
@Configuration
@EnableRabbit
public class PaymentMessageConfig {

    /** 与 payment 服务 RabbitMQConfig.PAYMENT_EXCHANGE 保持一致 */
    public static final String PAYMENT_EXCHANGE = "parking.payment.exchange";
    public static final String PAYMENT_SUCCESS_KEY = "payment.success";
    public static final String PAYMENT_SUCCESS_QUEUE = "parking.order.payment.success.queue";

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(PAYMENT_SUCCESS_QUEUE).build();
    }

    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder.bind(paymentSuccessQueue()).to(paymentExchange()).with(PAYMENT_SUCCESS_KEY);
    }
}
