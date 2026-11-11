package com.seralice.tickflow.web.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ChannelAccountVO {

    private Long id;
    private String name;
    private Integer sendChannel;
    private Map<String, Object> accountConfig; // 脱敏后的配置
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
