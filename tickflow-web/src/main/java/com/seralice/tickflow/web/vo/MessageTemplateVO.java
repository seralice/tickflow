package com.seralice.tickflow.web.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 返回给前端的模板。和实体分开：msgContent 要以对象而不是字符串返回，version、isDeleted 不对外暴露
 */
@Data
public class MessageTemplateVO {

    private Long id;
    private String name;
    private Integer idType;
    private Integer sendChannel;
    private Long sendAccount;
    private Integer templateType;
    private Integer msgType;
    private Integer shieldType;
    private Map<String, String> msgContent;
    private Integer msgStatus;
    private Integer auditStatus;
    private String auditReason;
    private String expectPushTime;
    private String cronCrowdPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
