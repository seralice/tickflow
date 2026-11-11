package com.seralice.tickflow.api.service;

import com.seralice.tickflow.api.domain.BatchSendRequest;
import com.seralice.tickflow.api.domain.SendRequest;
import com.seralice.tickflow.common.domain.SendTaskInfo;
import com.seralice.tickflow.common.vo.Result;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 接入层统一发送服务接口
 */
public interface SendService {

    /**
     * 单文案发送接口
     *
     * @param request 发送请求
     * @return 包含发送任务信息的统一结果
     */
    Result<List<SendTaskInfo>> send(SendRequest request);

    Result<List<SendTaskInfo>> batchSend(@Valid BatchSendRequest request);

    void cancel(String messageId);
}
