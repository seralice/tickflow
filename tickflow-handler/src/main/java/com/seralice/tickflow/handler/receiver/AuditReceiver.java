package com.seralice.tickflow.handler.receiver;

import com.seralice.tickflow.common.enums.AuditStatus;
import com.seralice.tickflow.support.ai.AuditClient;
import com.seralice.tickflow.support.ai.AuditUnavailableException;
import com.seralice.tickflow.support.domain.AuditResult;
import com.seralice.tickflow.support.entity.MessageTemplate;
import com.seralice.tickflow.support.mapper.MessageTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * 营销模板 AI 合规异步审核消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditReceiver {

    private final MessageTemplateMapper messageTemplateMapper;
    private final AuditClient auditClient;

    @RetryableTopic(
            attempts = RetryPolicy.ATTEMPTS,
            backOff = @BackOff(delay = RetryPolicy.DELAY_MS, multiplier = RetryPolicy.MULTIPLIER),
            include = AuditUnavailableException.class
    )
    @KafkaListener(
            topics = "#{'${tickflow.audit.topic.name:tickflowAudit}'}",
            groupId = "tickflow-audit-group"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String templateIdStr = record.value();
        if (templateIdStr == null || templateIdStr.isBlank()) {
            return;
        }

        try {
            Long templateId = Long.parseLong(templateIdStr.trim());
            MessageTemplate template = messageTemplateMapper.findById(templateId);
            if (template == null || template.getIsDeleted() == 1) {
                log.warn("[AuditReceiver] 模板不存在或已被删除，跳过审核: id={}", templateId);
                return;
            }

            log.info("[AuditReceiver] 开始执行 AI 合规审核任务: templateId={}, name={}", templateId, template.getName());

            // 1. 调用 RAG + Tool Call 审核客户端
            String content = template.getMsgContent();
            AuditResult auditResult = auditClient.auditContent(content);

            // 2. 映射审核状态：true -> 20 (通过), false -> 30 (驳回)
            int auditStatus = Boolean.TRUE.equals(auditResult.getPass())
                    ? AuditStatus.PASS.getCode()
                    : AuditStatus.REJECT.getCode();

            String reason = auditResult.getReason();
            if (auditResult.getHitArticle() != null && !auditResult.getHitArticle().isBlank()) {
                reason = "【命中法条：" + auditResult.getHitArticle() + "】" + (reason != null ? reason : "");
            }

            // 3. 版本号条件更新（乐观锁防止并发覆盖）
            int rows = messageTemplateMapper.updateAuditStatusWithVersion(
                    template.getId(),
                    template.getVersion(),
                    auditStatus,
                    reason
            );

            if (rows > 0) {
                log.info("[AuditReceiver] 模板审核状态更新成功！templateId={}, status={}, reason={}",
                        templateId, auditStatus, reason);
            } else {
                log.warn("[AuditReceiver] 模板版本已更新，放弃当前过期的审核结果！templateId={}, version={}",
                        templateId, template.getVersion());
            }

        } catch (AuditUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AuditReceiver] 审核任务执行异常！record={}", record.value(), e);
        }


    }

    @DltHandler
    public void onDlt(ConsumerRecord<String, String> record) {

        String templateIdStr = record.value();
        Long templateId = Long.parseLong(templateIdStr.trim());

        Integer auditStatus = AuditStatus.REJECT.getCode();
        String auditReason = "审核服务暂不可用，请重新提交审核";

        int row = messageTemplateMapper.rejectIfWaiting(templateId,auditStatus,auditReason);

        if(row == 0){
            MessageTemplate template = messageTemplateMapper.findById(templateId);
            if( template == null){
                log.warn("[AuditReceiver] 模板不存在或已被删除，跳过审核: id={}", templateId);
            } else {
                log.warn("[AuditReceiver] 模板已不在待审核状态，跳过: id={}", templateId);
            }
        }
    }
}
