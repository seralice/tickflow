package com.seralice.tickflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 发送渠道。编码沿用 Austin；各渠道的差异（接收者类型、必填字段）集中写在这里，
 * 新增渠道只需要加一个枚举值，Service 里不用写 if-else
 */
@Getter
@AllArgsConstructor
public enum ChannelType implements CodeEnum {

    EMAIL(40, "邮件", IdType.EMAIL,
            List.of("title", "content"),
            List.of("host", "port", "username", "password", "from")),
    FEISHU_ROBOT(110, "飞书机器人", IdType.USER_ID,
            List.of("content"),
            List.of("webhook")),
    /** Austin 没有，用于本地测试和压测，可配置延迟和失败率 */
    MOCK(900, "模拟渠道", IdType.USER_ID,
            List.of("content"),
            List.of());

    private final int code;
    private final String description;
    /** 该渠道的接收者必须是哪种 id */
    private final IdType idType;
    /** 模板 msgContent 的必填字段 */
    private final List<String> contentFields;
    /** 渠道账号 accountConfig 的必填字段 */
    private final List<String> accountFields;
}
