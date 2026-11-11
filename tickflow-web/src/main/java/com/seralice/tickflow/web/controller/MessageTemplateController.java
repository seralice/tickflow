package com.seralice.tickflow.web.controller;

import com.seralice.tickflow.api.domain.MessageParam;
import com.seralice.tickflow.api.domain.SendRequest;
import com.seralice.tickflow.api.service.SendService;
import com.seralice.tickflow.common.domain.SendTaskInfo;
import com.seralice.tickflow.common.vo.PageResult;
import com.seralice.tickflow.common.vo.Result;
import com.seralice.tickflow.web.dto.MessageTemplateRequest;
import com.seralice.tickflow.web.dto.MessageTemplateTest;
import com.seralice.tickflow.web.dto.TemplateManualAuditRequest;
import com.seralice.tickflow.web.service.MessageTemplateService;
import com.seralice.tickflow.web.vo.MessageTemplateVO;
import com.seralice.tickflow.web.vo.TemplateAuditVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class MessageTemplateController {

    /** 依赖的是接口，具体用哪个实现由 Spring 注入 */
    private final MessageTemplateService messageTemplateService;
    private final SendService sendService;

    @PostMapping
    public Result<Long> create(@RequestBody @Valid MessageTemplateRequest req) {
        return Result.ok(messageTemplateService.create(req));
    }

    @GetMapping("/{id}")
    public Result<MessageTemplateVO> getById(@PathVariable Long id) {
        return Result.ok(messageTemplateService.getById(id));
    }

    @GetMapping
    public Result<PageResult<MessageTemplateVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer sendChannel) {

            return Result.ok(messageTemplateService.list(page, size, name, sendChannel));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody @Valid MessageTemplateRequest req){
        messageTemplateService.update(id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id){
        messageTemplateService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/copy")
    public Result<Long> copy(@PathVariable Long id){
        Long copyId = messageTemplateService.copy(id);
        return Result.ok(copyId);
    }

    @PostMapping("/{id}/test")
    public Result<List<SendTaskInfo>> test(@PathVariable Long id,
                                           @Valid @RequestBody MessageTemplateTest dto){
        MessageParam param = MessageParam.builder()
                .receiver(dto.getReceiver())
                .variables(dto.getVariables())
                .build();

        SendRequest sendRequest = SendRequest.builder()
                .messageTemplateId(id)
                .messageParam(param)
                .build();

        return sendService.send((sendRequest));
    }

    @GetMapping("/{id}/audit")
    public Result<TemplateAuditVO> getAuditById(@PathVariable Long id){
        return Result.ok(messageTemplateService.getAuditById(id));
    }

    @PostMapping("/{id}/audit/retry")
    public Result<Void> retryAudit(@PathVariable Long id){
        messageTemplateService.retryAudit(id);
        return Result.ok();
    }

    @PostMapping("/{id}/audit/manual")
    public Result<Void> manual(@PathVariable Long id,
                                @Valid @RequestBody  TemplateManualAuditRequest req){
        messageTemplateService.manual(id,req);
        return Result.ok();
    }


}
