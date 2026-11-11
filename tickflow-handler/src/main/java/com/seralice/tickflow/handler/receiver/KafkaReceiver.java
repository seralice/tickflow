package com.seralice.tickflow.handler.receiver;

import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.exception.SendRetryableException;
import com.seralice.tickflow.handler.handler.BaseHandler;
import com.seralice.tickflow.handler.handler.HandlerHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 核心下发消费者：监听 Kafka 业务主题，反序列化任务并路由到对应渠道 Handler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaReceiver {

    private final HandlerHolder handlerHolder;
    private final JsonMapper jsonMapper;

    @Value("${tickflow.business.topic.name:tickflowBusiness}")
    private String topicName;
    @RetryableTopic(
            attempts = RetryPolicy.ATTEMPTS,
            backOff = @BackOff(delay = RetryPolicy.DELAY_MS, multiplier = RetryPolicy.MULTIPLIER),
            include = SendRetryableException.class
    )
    @KafkaListener(
            topics = "#{'${tickflow.business.topic.name:tickflowBusiness}-40'}",
            groupId = "#{'${spring.kafka.consumer.group-id:tickflow-group}-40'}"
    )
    public void onEmail(ConsumerRecord<String, String> record) {
         consume(record);
    }

    @RetryableTopic(
            attempts = RetryPolicy.ATTEMPTS,
            backOff = @BackOff(delay = RetryPolicy.DELAY_MS, multiplier = RetryPolicy.MULTIPLIER),
            include = SendRetryableException.class
    )
    @KafkaListener(
            topics = "#{'${tickflow.business.topic.name:tickflowBusiness}-110'}",
            groupId = "#{'${spring.kafka.consumer.group-id:tickflow-group}-110'}"
    )
    public void onFeishu(ConsumerRecord<String, String> record) {
        consume(record);
    }

    @RetryableTopic(
            attempts = RetryPolicy.ATTEMPTS,
            backOff = @BackOff(delay = RetryPolicy.DELAY_MS, multiplier = RetryPolicy.MULTIPLIER),
            include = SendRetryableException.class
    )
    @KafkaListener(
            topics = "#{'${tickflow.business.topic.name:tickflowBusiness}-900'}",
            groupId = "#{'${spring.kafka.consumer.group-id:tickflow-group}-900'}"
    )
    public void onMock(ConsumerRecord<String, String> record) {
        consume(record);
    }

    private void consume(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (value == null || value.isBlank()) {
            return;
        }

        try {
            TaskInfo taskInfo = jsonMapper.readValue(value, TaskInfo.class);
            if (taskInfo == null || taskInfo.getSendChannel() == null) {
                log.warn("[KafkaReceiver] 接收到未知或缺失渠道的任务消息: {}", value);
                return;
            }

            // 策略模式路由：按渠道编码获取对应的 BaseHandler
            BaseHandler handler = handlerHolder.route(taskInfo.getSendChannel());
            if (handler == null) {
                log.error("[KafkaReceiver] 找不到对应的渠道处理器！sendChannel: {}, messageId: {}",
                        taskInfo.getSendChannel(), taskInfo.getMessageId());
                return;
            }

            // 执行模板方法进入下发流程
            String topic = topicName + "-" + taskInfo.getSendChannel();
            boolean isRetry = !topic.equals(record.topic());
            handler.doHandler(taskInfo,isRetry);
        } catch (SendRetryableException e) {
            throw e;
        } catch (Exception e) {
            log.error("[KafkaReceiver] 消费消息异常！offset: {}, value: {}", record.offset(), value, e);
        }
    }

    @DltHandler
    public void onDlt(ConsumerRecord<String, String> record){
        log.error("[KafkaReceiver] 消息重试耗尽，已进入死信: topic={}, value={}", record.topic(), record.value());
    }
}
