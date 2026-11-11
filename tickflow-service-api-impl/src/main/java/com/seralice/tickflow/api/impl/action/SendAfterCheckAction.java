package com.seralice.tickflow.api.impl.action;

import com.seralice.tickflow.api.impl.domain.SendTaskModel;
import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.enums.IdType;
import com.seralice.tickflow.common.enums.ShieldType;
import com.seralice.tickflow.common.exception.BizException;
import com.seralice.tickflow.common.pipeline.BusinessProcess;
import com.seralice.tickflow.common.pipeline.ProcessContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 责任链第 3 站：后置检查与非法接收者过滤
 * 规则：
 * 1. 若 idType 是 EMAIL(50)，校验邮箱正则，剔除不合法的接收者（并记 warn 日志）；
 * 2. 某个任务的所有接收者都不合法时，将该 TaskInfo 移除；
 * 3. 若所有任务都被移除（没有一个合法的接收者），抛出 400 "没有合法的接收者"。
 */
@Slf4j
@Component
public class SendAfterCheckAction implements BusinessProcess<SendTaskModel> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    @Override
    public void process(ProcessContext<SendTaskModel> context) {
        SendTaskModel model = context.getProcessModel();
        List<TaskInfo> taskInfoList = model.getTaskInfoList();

        Iterator<TaskInfo> iterator = taskInfoList.iterator();
        while (iterator.hasNext()) {
            TaskInfo task = iterator.next();
            // 只有邮箱渠道需要做邮箱正则校验
            if (IdType.EMAIL.getCode() == task.getIdType()) {
                Set<String> validReceivers = task.getReceiver().stream()
                        .filter(receiver -> {
                            boolean valid = EMAIL_PATTERN.matcher(receiver).matches();
                            if (!valid) {
                                log.warn("过滤非法邮箱接收者: templateId=[{}], receiver=[{}]", task.getMessageTemplateId(), receiver);
                            }
                            return valid;
                        })
                        .collect(Collectors.toSet());

                task.setReceiver(validReceivers);
            }

            // 如果该任务下的接收者被全部剔除
            if (task.getReceiver().isEmpty()) {
                iterator.remove();
            }
        }

        // 如果全部任务都被过滤掉了
        if (taskInfoList.isEmpty()) {
            throw new BizException(400, "没有合法的接收者");
        }

        if (model.getSendTime() != null) {
            LocalDateTime sendTime = model.getSendTime();
            if (Objects.equals(model.getMessageTemplate().getShieldType(), ShieldType.NIGHT_SHIELD.getCode())
                    && ShieldType.isNight(sendTime)) {
                throw new BizException(400, "sendTime 处于夜间屏蔽时段，该模板夜间消息会被丢弃");
            }
        }
    }
}
