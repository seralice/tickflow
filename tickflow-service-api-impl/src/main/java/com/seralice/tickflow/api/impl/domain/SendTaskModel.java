package com.seralice.tickflow.api.impl.domain;

import com.seralice.tickflow.api.domain.MessageParam;
import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.pipeline.ProcessModel;
import com.seralice.tickflow.support.entity.MessageTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接入层发送责任链的核心数据模型（装在 ProcessContext 小推车里的货物）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTaskModel implements ProcessModel {

    /** 模板 ID */
    private Long messageTemplateId;

    /** 待发送参数列表（单文案发送时只有 1 项，批量发送时有多项） */
    private List<MessageParam> messageParamList;

    /** 从数据库查出的消息模板实体 */
    private MessageTemplate messageTemplate;

    /** 经过组装、校验、占位符渲染后生成的 TaskInfo 任务列表 */
    private List<TaskInfo> taskInfoList;

    /** 延迟发送时间，为空表示立即发送 */
    private LocalDateTime sendTime;
}
