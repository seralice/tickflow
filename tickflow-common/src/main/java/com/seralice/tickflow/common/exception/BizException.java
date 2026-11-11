package com.seralice.tickflow.common.exception;

import lombok.Getter;

/**
 * 业务异常：表示"请求本身有问题"（如模板不存在），由全局异常处理器转成友好提示
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
