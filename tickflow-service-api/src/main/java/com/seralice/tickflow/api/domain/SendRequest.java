package com.seralice.tickflow.api.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单文案发送请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendRequest {

    /** 模板 ID */
    @NotNull(message = "messageTemplateId 不能为空")
    private Long messageTemplateId;

    /** 消息发送参数 */
    @NotNull(message = "messageParam 不能为空")
    @Valid
    private MessageParam messageParam;

    /** 延迟发送时间（阶段 ⑥） */
    private LocalDateTime sendTime;
}
