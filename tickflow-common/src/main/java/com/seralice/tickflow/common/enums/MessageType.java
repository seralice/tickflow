package com.seralice.tickflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型：同一渠道下不同类型的消息使用独立的消费组和线程池，营销消息再多也不会堵住验证码
 */
@Getter
@AllArgsConstructor
public enum MessageType implements CodeEnum {

    NOTICE(10, "通知"),
    MARKETING(20, "营销"),
    AUTH_CODE(30, "验证码");

    private final int code;
    private final String description;
}
