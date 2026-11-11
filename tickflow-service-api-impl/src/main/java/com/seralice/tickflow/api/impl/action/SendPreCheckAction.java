package com.seralice.tickflow.api.impl.action;

import com.seralice.tickflow.api.domain.MessageParam;
import com.seralice.tickflow.api.impl.domain.SendTaskModel;
import com.seralice.tickflow.common.exception.BizException;
import com.seralice.tickflow.common.pipeline.BusinessProcess;
import com.seralice.tickflow.common.pipeline.ProcessContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 责任链第 1 站：前置参数校验
 * 规则：
 * 1. messageTemplateId 不能为空
 * 2. messageParamList 不能为空
 * 3. 每一个 param 的 receiver 不能为空，且逗号去重后不能超过 100 个
 */
@Slf4j
@Component
public class SendPreCheckAction implements BusinessProcess<SendTaskModel> {

    @Override
    public void process(ProcessContext<SendTaskModel> context) {
        SendTaskModel model = context.getProcessModel();

        if (model.getMessageTemplateId() == null) {
            throw new BizException(400, "messageTemplateId 不能为空");
        }

        List<MessageParam> paramList = model.getMessageParamList();
        if (paramList == null || paramList.isEmpty()) {
            throw new BizException(400, "messageParam 不能为空");
        }

        if( model.getSendTime() != null){
            LocalDateTime sendTime = model.getSendTime();
            LocalDateTime now = LocalDateTime.now();
            if( !sendTime.isAfter(now) || sendTime.isAfter(now.plusDays(30))){
                throw new BizException(400,"sendTime 必须晚于当前时间，且不超过 30 天");
            }
        }

        for (MessageParam param : paramList) {
            if (param.getReceiver() == null || param.getReceiver().isBlank()) {
                throw new BizException(400, "receiver 不能为空");
            }

            long distinctCount = Arrays.stream(param.getReceiver().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .count();

            if (distinctCount == 0) {
                throw new BizException(400, "receiver 不能为空");
            }
            if (distinctCount > 100) {
                throw new BizException(400, "接收者不能超过 100 个");
            }
        }
    }
}
