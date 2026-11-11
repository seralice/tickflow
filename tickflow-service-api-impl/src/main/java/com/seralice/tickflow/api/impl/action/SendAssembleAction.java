package com.seralice.tickflow.api.impl.action;

import com.seralice.tickflow.api.domain.MessageParam;
import com.seralice.tickflow.api.impl.domain.SendTaskModel;
import com.seralice.tickflow.common.domain.TaskInfo;
import com.seralice.tickflow.common.enums.AuditStatus;
import com.seralice.tickflow.common.enums.MessageType;
import com.seralice.tickflow.common.enums.TemplateType;
import com.seralice.tickflow.common.exception.BizException;
import com.seralice.tickflow.common.pipeline.BusinessProcess;
import com.seralice.tickflow.common.pipeline.ProcessContext;
import com.seralice.tickflow.support.entity.MessageTemplate;
import com.seralice.tickflow.support.mapper.MessageTemplateMapper;
import com.seralice.tickflow.support.utils.ContentHolderUtil;
import com.seralice.tickflow.support.utils.TaskInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 责任链第 2 站：数据组装与占位符渲染
 * 规则：
 * 1. 查模板（不存在或已删除 -> 404）
 * 2. 检查模板类型（定时类不能走实时接口发送 -> 400）
 * 3. 检查营销模板审核状态（未审核通过 -> 409，阶段 ④ 完整生效）
 * 4. 检查占位符变量：模板中的 {$var} 必须在 variables 中全量提供，缺一则报 400
 * 5. 将每个 MessageParam 组装为一个 TaskInfo 实体放入列表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendAssembleAction implements BusinessProcess<SendTaskModel> {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private final MessageTemplateMapper messageTemplateMapper;
    private final JsonMapper jsonMapper;

    @Override
    public void process(ProcessContext<SendTaskModel> context) {
        SendTaskModel model = context.getProcessModel();
        Long templateId = model.getMessageTemplateId();

        // 1. 查模板
        MessageTemplate template = messageTemplateMapper.findById(templateId);
        if (template == null) {
            throw new BizException(404, "模板不存在");
        }
        model.setMessageTemplate(template);

        // 2. 校验模板类型
        if (TemplateType.CLOCKING.getCode() == template.getTemplateType()) {
            throw new BizException(400, "定时类模板不能通过接口发送");
        }

        // 3. 营销模板审核校验
        if (MessageType.MARKETING.getCode() == template.getMsgType()
                && AuditStatus.PASS.getCode() != template.getAuditStatus()) {
            throw new BizException(409, "模板未通过审核");
        }

        // 4. 解析模板原始内容 Map
        Map<String, String> originContentMap = jsonMapper.readValue(template.getMsgContent(), MAP_TYPE);

        // 5. 提取模板中所有必需的占位符变量名
        Set<String> requiredVariables = new HashSet<>();
        for (String contentValue : originContentMap.values()) {
            requiredVariables.addAll(ContentHolderUtil.extractPlaceHolderKeys(contentValue));
        }

        // 6. 为每一个 MessageParam 组装一个 TaskInfo
        List<TaskInfo> taskInfoList = new ArrayList<>();
        Long businessId = TaskInfoUtils.generateBusinessId(template.getTemplateType(), template.getId());

        for (MessageParam param : model.getMessageParamList()) {
            Map<String, String> variables = param.getVariables() != null ? param.getVariables() : Map.of();

            // 检查必需占位符是否缺失
            for (String requiredKey : requiredVariables) {
                if (!variables.containsKey(requiredKey) || variables.get(requiredKey) == null) {
                    throw new BizException(400, "缺少占位符参数: " + requiredKey);
                }
            }

            // 渲染占位符
            Map<String, String> renderedContent = new HashMap<>();
            for (Map.Entry<String, String> entry : originContentMap.entrySet()) {
                renderedContent.put(entry.getKey(), ContentHolderUtil.replacePlaceHolder(entry.getValue(), variables));
            }

            // 拆分去重接收者集合
            Set<String> receiverSet = Arrays.stream(param.getReceiver().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());

            String messageId = TaskInfoUtils.generateMessageId();
            String bizId = (param.getBizId() != null && !param.getBizId().isBlank()) ? param.getBizId() : messageId;

            TaskInfo taskInfo = TaskInfo.builder()
                    .bizId(bizId)
                    .messageId(messageId)
                    .businessId(businessId)
                    .messageTemplateId(template.getId())
                    .receiver(receiverSet)
                    .idType(template.getIdType())
                    .sendChannel(template.getSendChannel())
                    .templateType(template.getTemplateType())
                    .msgType(template.getMsgType())
                    .shieldType(template.getShieldType())
                    .contentModel(renderedContent)
                    .sendAccount(template.getSendAccount())
                    .build();

            taskInfoList.add(taskInfo);
        }

        model.setTaskInfoList(taskInfoList);
    }
}
