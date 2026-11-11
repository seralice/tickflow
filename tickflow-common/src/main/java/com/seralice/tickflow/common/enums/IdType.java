package com.seralice.tickflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 接收者 id 类型：决定发送前要不要做格式校验
 */
@Getter
@AllArgsConstructor
public enum IdType implements CodeEnum {

    USER_ID(10, "用户 id"),
    EMAIL(50, "邮箱");

    private final int code;
    private final String description;
}
