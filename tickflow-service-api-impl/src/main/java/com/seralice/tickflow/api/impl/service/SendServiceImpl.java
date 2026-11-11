package com.seralice.tickflow.api.impl.service;

import com.seralice.tickflow.api.domain.BatchSendRequest;
import com.seralice.tickflow.api.domain.SendRequest;
import com.seralice.tickflow.api.impl.config.PipelineConfig;
import com.seralice.tickflow.api.impl.domain.SendTaskModel;
import com.seralice.tickflow.api.service.SendService;
import com.seralice.tickflow.common.domain.SendTaskInfo;
import com.seralice.tickflow.common.exception.BizException;
import com.seralice.tickflow.common.pipeline.ProcessContext;
import com.seralice.tickflow.common.pipeline.ProcessController;
import com.seralice.tickflow.common.vo.Result;
import com.seralice.tickflow.support.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 接入层发送服务实现类：将请求组装为 SendTaskModel 推入责任链执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendServiceImpl implements SendService {

    private final ProcessController processController;
    private final RedisUtils redisUtils;

    @Override
    @SuppressWarnings("unchecked")
    public Result<List<SendTaskInfo>> send(SendRequest request) {
        // 1. 构建小推车里的模型货物
        SendTaskModel model = SendTaskModel.builder()
                .messageTemplateId(request.getMessageTemplateId())
                .messageParamList(Collections.singletonList(request.getMessageParam()))
                .sendTime(request.getSendTime())
                .build();

        // 2. 构造责任链上下文
        ProcessContext<SendTaskModel> context = ProcessContext.<SendTaskModel>builder()
                .code(PipelineConfig.BUSINESS_CODE_SEND)
                .processModel(model)
                .needBreak(false)
                .response(Result.ok())
                .build();

        // 3. 交给责任链执行流水线
        ProcessContext<SendTaskModel> resultContext = processController.process(context);

        // 4. 返回责任链中组装的最终结果
        return (Result<List<SendTaskInfo>>) resultContext.getResponse();
    }

    @Override
    public Result<List<SendTaskInfo>> batchSend(BatchSendRequest request) {

        // 1. 构建小推车里的模型货物
        SendTaskModel model = SendTaskModel.builder()
                .messageTemplateId(request.getMessageTemplateId())
                .messageParamList(request.getMessageParamList())
                .build();

        // 2. 构造责任链上下文
        ProcessContext<SendTaskModel> context = ProcessContext.<SendTaskModel>builder()
                .code(PipelineConfig.BUSINESS_CODE_SEND)
                .processModel(model)
                .needBreak(false)
                .response(Result.ok())
                .build();

        // 3. 交给责任链执行流水线
        ProcessContext<SendTaskModel> resultContext = processController.process(context);

        // 4. 返回责任链中组装的最终结果
        return (Result<List<SendTaskInfo>>) resultContext.getResponse();
    }

    @Override
    public void cancel(String messageId) {
        boolean isDelete = redisUtils.cancelDelay(messageId);
        if( !isDelete ){
            throw new BizException(409,"消息不存在、已触发或已取消");
        }
    }


}
