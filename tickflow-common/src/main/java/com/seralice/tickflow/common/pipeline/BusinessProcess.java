package com.seralice.tickflow.common.pipeline;

/**
 * 责任链中的具体执行节点（工位）
 *
 * @param <T> 业务模型
 */
public interface BusinessProcess<T extends ProcessModel> {

    /**
     * 处理具体业务逻辑
     *
     * @param context 上下文
     */
    void process(ProcessContext<T> context);
}
