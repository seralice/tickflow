package com.seralice.tickflow.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateManualAuditRequest {
    @NotNull
    private Integer auditStatus;

    private String auditReason;
}
