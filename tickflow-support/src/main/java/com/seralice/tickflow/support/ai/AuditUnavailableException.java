package com.seralice.tickflow.support.ai;

/**
 * AI 审核服务暂时不可用（调用失败、无响应、无法解析出结论），可以重试。
 * 与"AI 判定文案违规"区分开：后者正常返回 AuditResult，不抛异常。
 */
public class AuditUnavailableException extends RuntimeException {

    public AuditUnavailableException(String message) {
        super(message);
    }

    public AuditUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
