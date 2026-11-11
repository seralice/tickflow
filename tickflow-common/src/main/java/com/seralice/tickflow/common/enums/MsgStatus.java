package com.seralice.tickflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模板状态：只对定时类模板有意义，实时类模板创建后一直是新建
 */
@Getter
@AllArgsConstructor
public enum MsgStatus implements CodeEnum {

    INIT(10, "新建"),
    STOP(20, "停用"),
    RUN(30, "启用");

    private final int code;
    private final String description;
}
