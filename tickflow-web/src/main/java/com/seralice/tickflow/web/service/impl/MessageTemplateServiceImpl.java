package com.seralice.tickflow.web.service.impl;

import com.seralice.tickflow.common.enums.AuditStatus;
import com.seralice.tickflow.common.enums.ChannelType;
import com.seralice.tickflow.common.enums.IdType;
import com.seralice.tickflow.common.enums.MessageType;
import com.seralice.tickflow.common.enums.MsgStatus;
import com.seralice.tickflow.common.enums.ShieldType;
import com.seralice.tickflow.common.enums.TemplateType;
import com.seralice.tickflow.common.exception.BizException;
import com.seralice.tickflow.common.utils.EnumUtils;
import com.seralice.tickflow.common.vo.PageResult;
import com.seralice.tickflow.support.entity.ChannelAccount;
import com.seralice.tickflow.support.entity.MessageTemplate;
import com.seralice.tickflow.support.mapper.ChannelAccountMapper;
import com.seralice.tickflow.support.mapper.MessageTemplateMapper;
import com.seralice.tickflow.web.dto.MessageTemplateRequest;
import com.seralice.tickflow.web.dto.TemplateManualAuditRequest;
import com.seralice.tickflow.web.service.MessageTemplateService;
import com.seralice.tickflow.web.vo.MessageTemplateVO;
import com.seralice.tickflow.web.vo.TemplateAuditVO;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MessageTemplateServiceImpl implements MessageTemplateService {

    /** 泛型在运行时会被擦除，Jackson 需要靠这个匿名子类才知道要转成 Map<String, String> */
    private static final TypeReference<Map<String, String>> CONTENT_TYPE = new TypeReference<>() {
    };

    private final MessageTemplateMapper messageTemplateMapper;
    private final ChannelAccountMapper channelAccountMapper;
    private final JsonMapper jsonMapper;
    private final com.seralice.tickflow.support.mq.KafkaSendClient kafkaSendClient;

    @Override
    public Long create(MessageTemplateRequest req) {
        checkRequest(req);

        MessageTemplate template = new MessageTemplate();
        // 同名且类型兼容的字段会被复制；msgContent 两边类型不同（Map / String），会被跳过，下面单独处理
        BeanUtils.copyProperties(req, template);
        template.setMsgContent(jsonMapper.writeValueAsString(req.getMsgContent()));
        template.setMsgStatus(MsgStatus.INIT.getCode());
        template.setAuditStatus(initAuditStatus(req.getMsgType()));
        messageTemplateMapper.insert(template);

        // 如果是营销类模板，自动触发后台 AI 合规审核
        if (Objects.equals(MessageType.MARKETING.getCode(), req.getMsgType())) {
            kafkaSendClient.send("tickflowAudit", String.valueOf(template.getId()));
        }

        return template.getId();
    }

    @Override
    public MessageTemplateVO getById(Long id) {
        MessageTemplate template = messageTemplateMapper.findById(id);
        if (template == null) {
            throw new BizException(404, "模板不存在");
        }
        return toVO(template);
    }

    private @NonNull MessageTemplateVO toVO(MessageTemplate template) {
        MessageTemplateVO vo = new MessageTemplateVO();
        BeanUtils.copyProperties(template, vo);
        vo.setMsgContent(jsonMapper.readValue(template.getMsgContent(), CONTENT_TYPE));
        return vo;
    }


    @Override
    public PageResult<MessageTemplateVO> list(int page, int size, String name, Integer sendChannel) {
        if( page <= 0 || size > 100 || size <= 0){
            throw new BizException(400, "page必须>=1 且 size必须位于1~100");
        }

        int offSet = (page - 1) * size;
        List<MessageTemplate> templates = messageTemplateMapper.findPage(name, offSet, sendChannel, size);
        long total = messageTemplateMapper.countPage(name, sendChannel);
        List<MessageTemplateVO> voList = new ArrayList<>();
        for(MessageTemplate template : templates){
            voList.add(toVO(template));
        }
        return new PageResult<>(total,voList);
    }

    @Override
    public void update(Long id, MessageTemplateRequest req) {
        checkRequest(req);

        MessageTemplate template = new MessageTemplate();
        // 同名且类型兼容的字段会被复制；msgContent 两边类型不同（Map / String），会被跳过，下面单独处理
        BeanUtils.copyProperties(req, template);
        template.setMsgContent(jsonMapper.writeValueAsString(req.getMsgContent()));
        template.setAuditStatus(initAuditStatus(req.getMsgType()));
        template.setId(id);

        int row = messageTemplateMapper.update(template);
        if( row == 0){
            throw new BizException(404,"模板不存在");
        }

        // 营销类模板修改后需重新触发 AI 合规审核
        if (Objects.equals(MessageType.MARKETING.getCode(), req.getMsgType())) {
            kafkaSendClient.send("tickflowAudit", String.valueOf(id));
        }
    }

    @Override
    public void delete(Long id) {
        int row = messageTemplateMapper.delete(id);
        if (row == 0) {
            throw new BizException(404, "模板不存在");
        }
    }

    @Override
    public Long copy(Long id) {
        MessageTemplate copyTemplate = messageTemplateMapper.findById(id);
        if( copyTemplate == null){
            throw new BizException(404,"模板不存在");
        }

        copyTemplate.setId(null);
        copyTemplate.setName(copyTemplate.getName() + "-副本");
        copyTemplate.setMsgStatus(MsgStatus.INIT.getCode());
        copyTemplate.setAuditStatus(initAuditStatus(copyTemplate.getMsgType()));
        copyTemplate.setVersion(0);
        copyTemplate.setAuditReason(null);
        messageTemplateMapper.insert((copyTemplate));
        return copyTemplate.getId();
    }

    @Override
    public TemplateAuditVO getAuditById(Long id) {
        MessageTemplate template = messageTemplateMapper.findById(id);
        if (template == null) {
            throw new BizException(404, "模板不存在");
        }

        return TemplateAuditVO.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .msgType(template.getMsgType())
                .auditStatus(template.getAuditStatus())
                .auditReason(template.getAuditReason())
                .build();
    }

    @Override
    public void retryAudit(Long id) {

        int row = messageTemplateMapper.retryAudit(id);
        if (row == 0) {
            MessageTemplate template = messageTemplateMapper.findById(id);
            if (template == null) {
                throw new BizException(400, "模板不存在");
            }
            if (!Objects.equals(template.getMsgType(), MessageType.MARKETING.getCode())) {
                throw new BizException(409, "仅营销类模板支持重试");
            }
            if (Objects.equals(template.getAuditStatus(), AuditStatus.WAIT.getCode())) {
                throw new BizException(409, "正在审核，请勿提交重复请求");
            }
            throw new BizException(409, "当前审核状态不支持重试");
        }

        kafkaSendClient.send("tickflowAudit", String.valueOf(id));
    }

    @Override
    public void manual(Long id, TemplateManualAuditRequest req) {
        if ( !(Objects.equals(req.getAuditStatus(),AuditStatus.PASS.getCode())||
                Objects.equals(req.getAuditStatus(),AuditStatus.REJECT.getCode()))){
            throw new BizException(400,"仅 通过/驳回 允许复核");
        }

        String reason = req.getAuditReason() != null && !req.getAuditReason().isBlank()
                ? "[人工复核结果] " + req.getAuditReason()
                : "[人工复核结果] 管理员特批";

        int row = messageTemplateMapper.manual(id,reason,req.getAuditStatus());

        if( row == 0){
            MessageTemplate template = messageTemplateMapper.findById(id);
            if( template == null) {
                throw new BizException(404, "模板不存在");
            }

            if( !Objects.equals(template.getMsgType(),MessageType.MARKETING.getCode())){
                throw new BizException(409,"非营销模板不允许复核");
            }

        }

    }

    /**
     * 创建和修改共用。注解做不到的校验都放在这里：枚举取值、字段之间的关系、需要查库的规则
     */
    private void checkRequest(MessageTemplateRequest req) {
        ChannelType channel = EnumUtils.require(ChannelType.class, req.getSendChannel(), "sendChannel");
        IdType idType = EnumUtils.require(IdType.class, req.getIdType(), "idType");
        TemplateType templateType = EnumUtils.require(TemplateType.class, req.getTemplateType(), "templateType");
        EnumUtils.require(MessageType.class, req.getMsgType(), "msgType");
        EnumUtils.require(ShieldType.class, req.getShieldType(), "shieldType");

        // 定时类要配合 cron 和人群文件，阶段 ⑥ 才实现
        if (templateType == TemplateType.CLOCKING) {
            throw new BizException(400, "templateType 暂只支持 20（实时类）");
        }
        if (idType != channel.getIdType()) {
            throw new BizException(400, "idType 与渠道不匹配");
        }

        ChannelAccount account = channelAccountMapper.findById(req.getSendAccount());
        if (account == null || account.getSendChannel() != channel.getCode()) {
            throw new BizException(400, "sendAccount 不存在或与渠道不匹配");
        }

        for (String field : channel.getContentFields()) {
            String value = req.getMsgContent().get(field);
            if (value == null || value.isBlank()) {
                throw new BizException(400, "msgContent." + field + " 不能为空");
            }
        }
    }

    /** 只有营销类需要审核（阶段 ④ 由 AI 完成），通知和验证码不涉及广告合规，直接通过 */
    private int initAuditStatus(int msgType) {
        return msgType == MessageType.MARKETING.getCode()
                ? AuditStatus.WAIT.getCode()
                : AuditStatus.PASS.getCode();
    }
}
