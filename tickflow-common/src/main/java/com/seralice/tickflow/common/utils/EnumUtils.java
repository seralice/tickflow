package com.seralice.tickflow.common.utils;

import com.seralice.tickflow.common.enums.CodeEnum;
import com.seralice.tickflow.common.exception.BizException;

/**
 * 所有 CodeEnum 共用的查找方法，避免每个枚举各写一遍 for 循环
 */
public final class EnumUtils {

    private EnumUtils() {
    }

    /** 按 code 找枚举，找不到返回 null */
    public static <E extends Enum<E> & CodeEnum> E of(Class<E> type, Integer code) {
        if (code == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.getCode() == code) {
                return e;
            }
        }
        return null;
    }

    /** 参数校验专用：找不到直接抛 400，field 是返回给调用方看的字段名 */
    public static <E extends Enum<E> & CodeEnum> E require(Class<E> type, Integer code, String field) {
        E e = of(type, code);
        if (e == null) {
            throw new BizException(400, field + " 取值不合法");
        }
        return e;
    }
}
