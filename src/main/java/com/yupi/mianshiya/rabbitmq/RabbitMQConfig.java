package com.yupi.mianshiya.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class RabbitMQConfig {

    // 题库操作交换机核队列
    public static final String QUESTION_BANK_EXCHANGE = "question.bank.exchange";
    public static final String QUESTION_BANK_QUEUE = "question.bank.queue";
    public static final String QUESTION_BANK_ROUTING_KEY = "question.bank.add.key";
    public static final String DLX_EXCHANGE = "question.bank.dlx.exchange";
    public static final String DLX_QUEUE = "question.dlx.queue";

    /**
     * 主交换机
     * @return
     */
    @Bean
    public TopicExchange questionBankExchange() {
        return new TopicExchange(QUESTION_BANK_EXCHANGE, true, false); // 持久化
    }

    // 主队列（绑定死信路由）
    @Bean
    public Queue questionBankAddQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", DLX_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", DLX_QUEUE);
        arguments.put("x-max-priority", 10);
        return new Queue(QUESTION_BANK_QUEUE, true, false, false, arguments);
    }

    // 死信交换机
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // 死信队列
    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE,true);
    }

    // 绑定主队列到交换机
    @Bean
    public Binding questionBankAddBinding() {
        return BindingBuilder.bind(questionBankAddQueue())
                .to(questionBankExchange())
                .with(QUESTION_BANK_ROUTING_KEY);
    }

    // 绑定死信队列
    @Bean
    public Binding questionBankDlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(DLX_QUEUE);
    }

    // JSON消息转换器
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }






}
