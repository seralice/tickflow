package com.seralice.tickflow.web.controller;

import com.seralice.tickflow.api.domain.BatchSendRequest;
import com.seralice.tickflow.api.domain.SendRequest;
import com.seralice.tickflow.api.service.SendService;
import com.seralice.tickflow.common.domain.SendTaskInfo;
import com.seralice.tickflow.common.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 接入层对外统一发送入口 Controller
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SendController {

    private final SendService sendService;

    @PostMapping("/send")
    public Result<List<SendTaskInfo>> send(@RequestBody @Valid SendRequest request) {
        return sendService.send(request);
    }

    @PostMapping("/batch-send")
    public Result<List<SendTaskInfo>> batchSend(@RequestBody @Valid BatchSendRequest request){
        return sendService.batchSend(request);
    }

    @PutMapping("/messages/{messageId}/cancel")
    public Result<Void> cancel(@PathVariable String messageId){
        sendService.cancel(messageId);
        return Result.ok();
    }

}
