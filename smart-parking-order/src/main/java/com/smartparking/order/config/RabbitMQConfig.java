package com.smartparking.order.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置
 */
@Configuration
public class RabbitMQConfig {

    /** 交换机 */
    public static final String ORDER_EXCHANGE = "parking.order.exchange";

    /** 入场队列 */
    public static final String ENTRY_QUEUE = "parking.order.entry.queue";
    /** 出场队列 */
    public static final String EXIT_QUEUE = "parking.order.exit.queue";
    /** 支付回调队列 */
    public static final String PAYMENT_QUEUE = "parking.order.payment.queue";

    /** 路由Key */
    public static final String ENTRY_KEY = "order.entry";
    public static final String EXIT_KEY = "order.exit";
    public static final String PAYMENT_KEY = "order.payment";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue entryQueue() {
        return QueueBuilder.durable(ENTRY_QUEUE).build();
    }

    @Bean
    public Queue exitQueue() {
        return QueueBuilder.durable(EXIT_QUEUE).build();
    }

    @Bean
    public Queue paymentQueue() {
        return QueueBuilder.durable(PAYMENT_QUEUE).build();
    }

    @Bean
    public Binding entryBinding() {
        return BindingBuilder.bind(entryQueue()).to(orderExchange()).with(ENTRY_KEY);
    }

    @Bean
    public Binding exitBinding() {
        return BindingBuilder.bind(exitQueue()).to(orderExchange()).with(EXIT_KEY);
    }

    @Bean
    public Binding paymentBinding() {
        return BindingBuilder.bind(paymentQueue()).to(orderExchange()).with(PAYMENT_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setReturnsCallback(returned -> {
            // 消息发送失败回调
        });
        return rabbitTemplate;
    }
}
