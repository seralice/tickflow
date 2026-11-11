package com.seralice.tickflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 夜间屏蔽类型
 */
@Getter
@AllArgsConstructor
public enum ShieldType implements CodeEnum {

    NIGHT_NO_SHIELD(10, "夜间不屏蔽"),
    NIGHT_SHIELD(20, "夜间屏蔽"),
    NIGHT_SHIELD_NEXT_DAY(30, "夜间屏蔽，次日 9 点发送");

    /** 夜间时段 [START, 24) ∪ [0, END)；接入层校验 sendTime 和处理层拦截共用，避免两处规则不一致 */
    public static final int NIGHT_START_HOUR = 22;
    public static final int NIGHT_END_HOUR = 8;

    private final int code;
    private final String description;

    /** 判断某个时间点是否处于夜间屏蔽时段 */
    public static boolean isNight(LocalDateTime time) {
        int hour = time.getHour();
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }
}
