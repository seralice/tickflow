package com.seralice.tickflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模板审核状态
 */
@Getter
@AllArgsConstructor
public enum AuditStatus implements CodeEnum {

    WAIT(10, "待审核"),
    PASS(20, "审核通过"),
    REJECT(30, "审核驳回");

    private final int code;
    private final String description;
}
