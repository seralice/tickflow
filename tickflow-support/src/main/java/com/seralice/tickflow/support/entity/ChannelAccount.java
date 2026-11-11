package com.seralice.tickflow.support.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道账号，与 channel_account 表一一对应
 */
@Data
public class ChannelAccount {

    private Long id;
    private String name;
    private Integer sendChannel;
    /** 数据库里是 JSON 列，结构随渠道变化 */
    private String accountConfig;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
