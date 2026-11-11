package com.seralice.tickflow.api.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSendRequest {


    @NotNull(message = "messageTemplateId 不能为空")
    private Long messageTemplateId;

    @NotNull(message = "messageParam 不能为空")
    @Valid
    private List<MessageParam> messageParamList;
}
