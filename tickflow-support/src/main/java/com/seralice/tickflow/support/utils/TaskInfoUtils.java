package com.seralice.tickflow.support.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 任务辅助工具类：生成唯一 messageId 和 businessId
 */
public class TaskInfoUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private TaskInfoUtils() {
    }

    /**
     * 生成短唯一消息 ID（无横线 UUID）
     */
    public static String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 businessId：(templateType * 1000000 + templateId) 拼接 yyyyMMdd
     */
    public static Long generateBusinessId(Integer templateType, Long templateId) {
        long prefix = (long) templateType * 1_000_000L + templateId;
        String date = LocalDate.now().format(DATE_FORMATTER);
        return Long.parseLong(prefix + date);
    }
}
