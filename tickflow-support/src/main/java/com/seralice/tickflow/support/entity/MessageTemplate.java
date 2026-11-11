package com.seralice.tickflow.support.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息模板，与 message_template 表一一对应
 */
@Data
public class MessageTemplate {

    private Long id;
    private String name;
    private Integer idType;
    private Integer sendChannel;
    private Long sendAccount;
    private Integer templateType;
    private Integer msgType;
    private Integer shieldType;
    /** 数据库里是 JSON 列，实体按字符串存取，和 Map 的互转由 Service 负责 */
    private String msgContent;
    private Integer msgStatus;
    private Integer auditStatus;
    private String auditReason;
    private String expectPushTime;
    private String cronCrowdPath;
    private Integer version;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
