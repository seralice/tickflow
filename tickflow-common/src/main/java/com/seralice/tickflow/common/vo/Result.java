package com.seralice.tickflow.common.vo;

import lombok.Data;

/**
 * 统一返回格式：所有接口都返回 {code, msg, data}，前端只需按 code 判断成功与否
 */
@Data
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.msg = "success";
        r.data = data;
        return r;
    }

    /** 只表示操作成功、没有数据要返回的接口用这个 */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
