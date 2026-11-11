package com.seralice.tickflow.handler.handler.impl;

import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.enums.ChannelType;
import com.seralice.tickflow.common.exception.SendRetryableException;
import com.seralice.tickflow.handler.handler.BaseHandler;
import com.seralice.tickflow.support.entity.ChannelAccount;
import com.seralice.tickflow.support.mapper.ChannelAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class FeishuHandler extends BaseHandler {

    private final ChannelAccountMapper channelAccountMapper;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()      // 已赋值 → 跳过
            .connectTimeout(Duration.ofSeconds(3))
            .build();


    @Override
    public Integer getChannelCode() {
        return ChannelType.FEISHU_ROBOT.getCode();
    }

    @Override
    public boolean handler(TaskInfo taskInfo) {
        ChannelAccount account = channelAccountMapper.findById(taskInfo.getSendAccount());
        Map<String, Object> configMap = jsonMapper.readValue(account.getAccountConfig(), new TypeReference<Map<String, Object>>() {
        });

        String title = taskInfo.getContentModel().get("title");
        String content = taskInfo.getContentModel().get("content");

        String webhook = configMap.get("webhook").toString();

        Map<String, Object> body = Map.of(
                "msg_type", "text",
                "content", Map.of("text", content)
        );

        String jsonBody = jsonMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }catch (IOException e) {
            throw new SendRetryableException("飞书发送失败，messageId:"+ taskInfo.getMessageId(),e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

// 3. 判断是否成功（code 是 0）
        Map<String, Object> respMap = jsonMapper.readValue(response.body(),new TypeReference<Map<String,Object>>(){});

        return Objects.equals(respMap.get("code"),0);
    }
}
