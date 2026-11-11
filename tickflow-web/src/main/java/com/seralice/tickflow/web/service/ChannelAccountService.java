package com.seralice.tickflow.web.service;

import com.seralice.tickflow.web.dto.ChannelAccountRequest;
import com.seralice.tickflow.web.vo.ChannelAccountVO;
import jakarta.validation.Valid;

import java.util.List;

public interface ChannelAccountService {
    Long create(ChannelAccountRequest req);

    List<ChannelAccountVO> list(Integer sendChannel);

    void update(Long id, @Valid ChannelAccountRequest req);

    void delete(Long id);
}
