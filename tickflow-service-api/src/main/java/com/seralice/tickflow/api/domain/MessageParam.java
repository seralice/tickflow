package com.seralice.tickflow.api.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 消息发送参数：单个文案与接收人组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageParam {

    /** 接收者，多个用英文逗号分隔，去重后不超过 100 个 */
    @NotBlank(message = "receiver 不能为空")
    private String receiver;

    /** 占位符变量取值，如 {"name": "小明"} */
    private Map<String, String> variables;

    /** 业务方自己的消息 ID，用于追踪；不传则系统自动取 messageId */
    private String bizId;
}
