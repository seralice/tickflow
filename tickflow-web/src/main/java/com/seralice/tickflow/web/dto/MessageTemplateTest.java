package com.seralice.tickflow.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class MessageTemplateTest {

    @NotBlank(message = "接收者不能为空")
    private String receiver;

    private Map<String,String> variables;
}
