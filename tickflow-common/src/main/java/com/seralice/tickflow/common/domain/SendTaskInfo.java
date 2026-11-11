package com.seralice.tickflow.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送任务简要信息（接口成功响应返回给调用方）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTaskInfo {

    private String messageId;
    private String bizId;
    private Long businessId;
}
