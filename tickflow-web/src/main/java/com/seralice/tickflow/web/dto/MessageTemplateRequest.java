package com.seralice.tickflow.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 创建 / 修改模板共用的请求体。
 * 注解只负责"有没有、长不长"；枚举取值是否合法、和渠道是否匹配这类规则由 Service 校验
 */
@Data
public class MessageTemplateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Integer idType;

    @NotNull
    private Integer sendChannel;

    @NotNull
    private Long sendAccount;

    @NotNull
    private Integer templateType;

    @NotNull
    private Integer msgType;

    @NotNull
    private Integer shieldType;

    /** 结构随渠道变化，用 Map 接收，必填字段由 Service 按渠道检查 */
    @NotEmpty
    private Map<String, String> msgContent;
}
