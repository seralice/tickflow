package com.seralice.tickflow.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.Objects;

@Data
public class ChannelAccountRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Integer sendChannel;

    @NotNull
    private Map<String, Object> accountConfig;


}
