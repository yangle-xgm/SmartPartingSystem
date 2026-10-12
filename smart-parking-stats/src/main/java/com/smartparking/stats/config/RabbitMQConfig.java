package com.smartparking.stats.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置
 */
@Configuration
public class RabbitMQConfig {

    /** 统计服务监听的队列 */
    public static final String STATS_QUEUE = "parking.stats.queue";

    /** 订单交换机（与订单服务保持一致） */
    public static final String ORDER_EXCHANGE = "parking.order.exchange";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue statsQueue() {
        return QueueBuilder.durable(STATS_QUEUE).build();
    }

    @Bean
    public Binding statsEntryBinding() {
        return BindingBuilder.bind(statsQueue()).to(orderExchange()).with("order.entry");
    }

    @Bean
    public Binding statsExitBinding() {
        return BindingBuilder.bind(statsQueue()).to(orderExchange()).with("order.exit");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
