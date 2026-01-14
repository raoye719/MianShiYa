package com.yupi.mianshiya.rabbitmq;

import com.alibaba.nacos.shaded.com.google.common.util.concurrent.CycleDetectingLockFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.mapper.QuestionBankQuestionMapper;
import com.yupi.mianshiya.model.entity.QuestionBankQuestion;
import com.yupi.mianshiya.service.QuestionBankQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;


import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.yupi.mianshiya.rabbitmq.RabbitMQConfig.QUESTION_BANK_QUEUE;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionBankAddConsumer {

    private final QuestionBankQuestionService questionBankQuestionService;

    private final QuestionBankQuestionMapper questionBankQuestionMapper;


    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedisTemplate<String, Boolean> redisTemplate;

    private  int count = 0;


    @RabbitListener(
            queues = QUESTION_BANK_QUEUE,
            concurrency = "7",
            ackMode = "MANUAL"// 显示手动ack
    )
    public void handleAddQuestion(
            @Payload List<QuestionBankAddMessage> messages,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header("retry-count") Integer retryCount
    ) throws IOException {

        try {
            List<QuestionBankQuestion> entrys = new ArrayList<>();
            for(QuestionBankAddMessage message : messages) {
                String key = getQuestionBankRedis(message.getQuestionBankId(), message.getQuestionId());
                Boolean setResult = redisTemplate.opsForValue().setIfAbsent(key, Boolean.TRUE);
                if(setResult == null || !setResult) {
                    channel.basicAck(deliveryTag, false);
                    System.out.println(count++);
                    return;
                }
                QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                questionBankQuestion.setQuestionBankId(message.getQuestionBankId());
                questionBankQuestion.setQuestionId(message.getQuestionId());
                questionBankQuestion.setUserId(message.getUserId());
                entrys.add(questionBankQuestion);
            }

//            log.debug("Received message: {}, deliveryTag={}, retryCount={}", message, deliveryTag, retryCount);
//            // 1.幂等性检查（防止重复处理）
//            LambdaQueryWrapper<QuestionBankQuestion> queryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
//                    .eq(QuestionBankQuestion::getQuestionBankId, message.getQuestionBankId())
//                    .eq(QuestionBankQuestion::getQuestionId, message.getQuestionId());

//            if(questionBankQuestionMapper.exists(queryWrapper)) {
//                log.warn("题库已经存在于题库中 请不要重复添加");
//                channel.basicAck(deliveryTag, false);
//                return;
//            }

//            // 2.构建并保存实体
//            QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
//            questionBankQuestion.setQuestionBankId(message.getQuestionBankId());
//            questionBankQuestion.setQuestionId(message.getQuestionId());
//            questionBankQuestion.setUserId(message.getUserId());

            boolean save = questionBankQuestionService.saveBatch(entrys);
            if(!save) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存到题库失败");
            }

            // 3.确认消息
            channel.basicAck(deliveryTag, false);
           // log.debug("成功添加题目{}到题库{}", message.getQuestionId(),message.getQuestionBankId());
        } catch (DuplicateKeyException e) {
            // 唯一冲突不重试
            channel.basicAck(deliveryTag, false);
//        } catch (TransientDataAccessException e) {
//            // 临时性错误：进行重试
//            handleRetry(message, channel, deliveryTag, retryCount);
        }catch (Exception e) {
            // 永久性错误：进死信队列
            channel.basicNack(deliveryTag, false, false);
        }

    }


    private static String getQuestionBankRedis(Long questionBankId, Long questionId) {
        return String.format("question_bank:%s:%s", questionBankId, questionId);
    }


    private void handleRetry(
            QuestionBankAddMessage message,
            Channel channel,
            long tag,
            int retryCount
    ) throws IOException {
        if(retryCount >= 3){
            channel.basicNack(tag, false, false);
            log.error("消息重试超过3次：{}", message);
        }else {
            // 更新重试次数重新入队
            // 使用RabbitTemplate重新发送消息，而不是直接使用Channel API
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.QUESTION_BANK_EXCHANGE,
                    RabbitMQConfig.QUESTION_BANK_ROUTING_KEY,
                    message,
                    m -> {
                        m.getMessageProperties().setHeader("retry-count", retryCount + 1);
                        return m;
                    }
            );
            channel.basicAck(tag, false);
        }
    }

}
