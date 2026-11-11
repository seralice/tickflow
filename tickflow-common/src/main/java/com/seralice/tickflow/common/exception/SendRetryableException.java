package com.seralice.tickflow.common.exception;

/**
 * 渠道发送遇到临时故障（网络超时、连接失败），可以重试。
 * 配置错误、数据格式错误等重试也不会成功的情况，不要抛这个异常。
 */
public class SendRetryableException extends RuntimeException{
    public SendRetryableException(String message) {
        super(message);
    }

    public SendRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
