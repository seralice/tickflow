package com.seralice.tickflow.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * 发送任务核心领域模型（在接入层组装、推入 Kafka、并在处理层消费执行）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskInfo {

    /** 业务方传入的追踪 ID */
    private String bizId;

    /** 系统生成的任务全局唯一 ID（NanoId） */
    private String messageId;

    /** 业务统计 ID：(templateType * 1000000 + templateId) 拼接 yyyyMMdd */
    private Long businessId;

    /** 模板 ID */
    private Long messageTemplateId;

    /** 接收者集合（已去重、已过滤非法格式） */
    private Set<String> receiver;

    /** 接收者类型（IdType） */
    private Integer idType;

    /** 发送渠道（ChannelType） */
    private Integer sendChannel;

    /** 模板类型（TemplateType） */
    private Integer templateType;

    /** 消息类型（MessageType） */
    private Integer msgType;

    /** 夜间屏蔽类型（ShieldType） */
    private Integer shieldType;

    /** 渲染占位符后的最终消息内容 */
    private Map<String, String> contentModel;

    /** 发送渠道账号 ID */
    private Long sendAccount;
}
