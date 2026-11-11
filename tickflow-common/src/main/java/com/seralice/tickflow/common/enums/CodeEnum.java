package com.seralice.tickflow.common.enums;

/**
 * 整数编码枚举的统一接口：数据库和接口里只存 code，靠它在 code 和枚举之间互相转换
 */
public interface CodeEnum {

    int getCode();

    String getDescription();
}
