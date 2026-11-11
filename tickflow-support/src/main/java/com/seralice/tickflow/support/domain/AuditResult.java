package com.seralice.tickflow.support.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 合规审核结构化决策结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResult {

    /** 是否通过审核（true 合规通过；false 违规驳回） */
    private Boolean pass;

    /** 审核详细理由与违规词归因 */
    private String reason;

    /** 命中的具体法律法规条款名称（如未违规则为空） */
    private String hitArticle;
}
