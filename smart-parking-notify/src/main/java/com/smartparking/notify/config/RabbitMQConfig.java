package com.smartparking.notify.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置
 */
@Configuration
public class RabbitMQConfig {

    /** 通知服务监听的队列 */
    public static final String NOTIFY_QUEUE = "parking.notify.queue";

    /** 订单交换机（与订单服务保持一致） */
    public static final String ORDER_EXCHANGE = "parking.order.exchange";

    /** 支付交换机（与支付服务保持一致） */
    public static final String PAYMENT_EXCHANGE = "parking.payment.exchange";

    /** 支付成功通知队列 */
    public static final String PAYMENT_NOTIFY_QUEUE = "parking.notify.payment.queue";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY_QUEUE).build();
    }

    @Bean
    public Queue paymentNotifyQueue() {
        return QueueBuilder.durable(PAYMENT_NOTIFY_QUEUE).build();
    }

    @Bean
    public Binding notifyBinding(@Qualifier("notifyQueue") Queue notifyQueue,
                                 @Qualifier("orderExchange") DirectExchange orderExchange) {
        return BindingBuilder.bind(notifyQueue).to(orderExchange).with("order.entry");
    }

    @Bean
    public Binding exitNotifyBinding(@Qualifier("notifyQueue") Queue notifyQueue,
                                     @Qualifier("orderExchange") DirectExchange orderExchange) {
        return BindingBuilder.bind(notifyQueue).to(orderExchange).with("order.exit");
    }

    @Bean
    public Binding paymentNotifyBinding(@Qualifier("paymentNotifyQueue") Queue paymentNotifyQueue,
                                        @Qualifier("paymentExchange") DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentNotifyQueue).to(paymentExchange).with("payment.success");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
