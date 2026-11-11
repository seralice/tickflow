package com.seralice.tickflow.support.mq;

import com.seralice.tickflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka 消息发送统一封装（供接入层 SendMqAction 等组件复用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaSendClient {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 发送普通消息到指定 Topic
     *
     * @param topic   目标主题
     * @param message JSON 消息内容
     */
    public void send(String topic, String message) {
        send(topic, null, message);
    }

    /**
     * 发送带 Key 的消息到指定 Topic
     *
     * @param topic   目标主题
     * @param key     消息 Key（用于分区路由）
     * @param message JSON 消息内容
     */
    public void send(String topic, String key, String message) {
        try {
            kafkaTemplate.send(topic, key, message).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[KafkaSendClient] 消息发送失败: topic={}, key={}, msg={}", topic, key, message, e);
            throw new BizException(500, "消息队列不可用，请稍后重试");
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            log.error("[KafkaSendClient] 消息发送失败: topic={}, key={}, msg={}", topic, key, message, e);
            throw new BizException(500, "消息队列不可用，请稍后重试");
        }
    }
}
