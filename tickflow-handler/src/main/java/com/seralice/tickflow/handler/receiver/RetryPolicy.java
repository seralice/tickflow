package com.seralice.tickflow.handler.receiver;

/**
 * 消费端非阻塞重试参数（@RetryableTopic 引用），发送和审核共用一套
 */
public final class RetryPolicy {
    public static final String ATTEMPTS = "5";
    public static final long DELAY_MS = 60_000L;
    public static final double MULTIPLIER = 5.0;

    private RetryPolicy() {}
}
