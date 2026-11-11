package com.seralice.tickflow.handler.delay;

import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.support.mq.KafkaSendClient;
import com.seralice.tickflow.support.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 延迟队列调度：每秒认领到期消息并写入 Kafka
 * 认领即把消息从等待队列挪到处理队列（租约 60 秒），写入成功后才删除；
 * 实例中途挂掉时，租约到期会被其他实例回收重投，保证至少一次投递，重复由消费端幂等兜底
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayQueueScheduler {

    private static final long LEASE_MS = 60_000L;
    private static final int BATCH_SIZE = 100;

    private final RedisUtils redisUtils;
    private final KafkaSendClient kafkaSendClient;
    private final JsonMapper jsonMapper;

    @Value("${tickflow.business.topic.name:tickflowBusiness}")
    private String topicName;

    /** 延迟队列调度：每秒认领到期消息，写入 Kafka 成功后再删除 */
    @Scheduled(fixedDelay = 1000)
    public void poll() {
        long now = System.currentTimeMillis();
        List<String> ids = redisUtils.claimDelay(now,now + LEASE_MS ,BATCH_SIZE);
        if (!ids.isEmpty()) {
            log.info("[DelayQueueScheduler] 认领消息数目为: {}",ids.size());
        }
        for (String id : ids) {
            try {
                String message = redisUtils.getDelayBody(id);
                if (message == null) {
                    redisUtils.removeDelay(id);
                    continue;
                }
                TaskInfo taskInfo = jsonMapper.readValue(message, TaskInfo.class);
                String topic = topicName + "-" + taskInfo.getSendChannel();
                kafkaSendClient.send(topic,id,message);
                redisUtils.removeDelay(id);
            } catch (Exception e) {
                log.error("[DelayQueueScheduler] 延迟消息投递失败，租约到期后重试: messageId: {}",id,e);
            }
        }
    }
}

