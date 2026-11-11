package com.seralice.tickflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模板类型：定时类由平台按 cron 触发，实时类由业务方调用发送接口触发
 */
@Getter
@AllArgsConstructor
public enum TemplateType implements CodeEnum {

    CLOCKING(10, "定时类"),
    REALTIME(20, "实时类");

    private final int code;
    private final String description;
}
