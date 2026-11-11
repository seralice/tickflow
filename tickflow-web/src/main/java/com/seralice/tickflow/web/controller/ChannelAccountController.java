package com.seralice.tickflow.web.controller;

import com.seralice.tickflow.common.vo.Result;
import com.seralice.tickflow.web.dto.ChannelAccountRequest;
import com.seralice.tickflow.web.service.ChannelAccountService;
import com.seralice.tickflow.web.vo.ChannelAccountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channel-accounts")
@RequiredArgsConstructor
public class ChannelAccountController {

    private final ChannelAccountService channelAccountService;

    @PostMapping
    public Result<Long> create(@RequestBody @Valid ChannelAccountRequest req){

        Long id = channelAccountService.create(req);
        return Result.ok(id);
    }

    @GetMapping
    public Result<List<ChannelAccountVO>> list(@RequestParam(value = "sendChannel", required = false) Integer sendChannel) {
        return Result.ok(channelAccountService.list(sendChannel));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody @Valid ChannelAccountRequest req){
        channelAccountService.update(id,req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id){
        channelAccountService.delete(id);
        return Result.ok();
    }

}
