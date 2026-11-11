package com.seralice.tickflow.handler.handler.impl;

import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.enums.ChannelType;
import com.seralice.tickflow.handler.handler.BaseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 模拟渠道处理器（用于本地调试与压测）
 */
@Component
public class MockHandler extends BaseHandler {

    private static final Logger log = LoggerFactory.getLogger(MockHandler.class);

    @Override
    public Integer getChannelCode() {
        return ChannelType.MOCK.getCode();
    }

    @Override
    public boolean handler(TaskInfo taskInfo) {
        log.info("[MockHandler] 模拟渠道下发成功！messageId: {}, 接收者: {}, 内容: {}",
                taskInfo.getMessageId(),
                taskInfo.getReceiver(),
                taskInfo.getContentModel());
        return true;
    }
}
