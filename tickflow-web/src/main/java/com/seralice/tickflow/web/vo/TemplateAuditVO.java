package com.seralice.tickflow.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateAuditVO {

    private Long templateId;

    private String templateName;

    private Integer msgType;

    private Integer auditStatus;

    private String auditReason;
}
