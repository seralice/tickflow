package com.seralice.tickflow.api.impl.action;

import com.seralice.tickflow.api.impl.domain.SendTaskModel;
import com.seralice.tickflow.common.domain.SendTaskInfo;
import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.pipeline.BusinessProcess;
import com.seralice.tickflow.common.pipeline.ProcessContext;
import com.seralice.tickflow.common.vo.Result;
import com.seralice.tickflow.support.mq.KafkaSendClient;
import com.seralice.tickflow.support.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.util.List;

/**
 * 责任链第 4 站：写入 Kafka 消息队列并组装返回结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendMqAction implements BusinessProcess<SendTaskModel> {

    private final KafkaSendClient kafkaSendClient;
    private final JsonMapper jsonMapper;
    private final RedisUtils redisUtils;

    @Value("${tickflow.business.topic.name:tickflowBusiness}")
    private String topicName;

    @Override
    public void process(ProcessContext<SendTaskModel> context) {
        SendTaskModel model = context.getProcessModel();
        List<TaskInfo> taskInfoList = model.getTaskInfoList();
        // 1. 发送消息到 Kafka（同步等待 broker 确认，确保消息不丢；渠道下发由消费端异步完成）
        for (TaskInfo taskInfo : taskInfoList) {
            String message = jsonMapper.writeValueAsString(taskInfo);
            if (model.getSendTime() != null){
                long dueMillis = model.getSendTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                redisUtils.addDelay(taskInfo.getMessageId(),message, dueMillis);
            } else {
                String topic = topicName + "-" + taskInfo.getSendChannel();
                kafkaSendClient.send(topic, taskInfo.getMessageId(), message);
            }
        }

        // 2. 组装给调用方的精简结果 List<SendTaskInfo>
        List<SendTaskInfo> responseList = taskInfoList.stream()
                .map(task -> SendTaskInfo.builder()
                        .messageId(task.getMessageId())
                        .bizId(task.getBizId())
                        .businessId(task.getBusinessId())
                        .build())
                .toList();

        // 3. 将成功结果写入上下文
        context.setResponse(Result.ok(responseList));
    }
}
