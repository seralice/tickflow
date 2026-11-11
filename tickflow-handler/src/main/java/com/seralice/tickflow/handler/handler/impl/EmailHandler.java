package com.seralice.tickflow.handler.handler.impl;

import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.enums.ChannelType;
import com.seralice.tickflow.handler.handler.BaseHandler;
import com.seralice.tickflow.support.entity.ChannelAccount;
import com.seralice.tickflow.support.mapper.ChannelAccountMapper;
import com.seralice.tickflow.support.utils.MailUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailHandler extends BaseHandler {

    private final ChannelAccountMapper channelAccountMapper;
    private final JsonMapper jsonMapper;

    @Override
    public Integer getChannelCode() {
        return ChannelType.EMAIL.getCode();
    }

    @Override
    public boolean handler(TaskInfo taskInfo) {
        ChannelAccount account = channelAccountMapper.findById(taskInfo.getSendAccount());
        Map<String, Object> configMap = jsonMapper.readValue(account.getAccountConfig(), new TypeReference<Map<String, Object>>() {
        });

        String title = taskInfo.getContentModel().get("title");
        String content = taskInfo.getContentModel().get("content");

        return MailUtils.send(configMap, taskInfo.getReceiver(), title, content);
    }
}
