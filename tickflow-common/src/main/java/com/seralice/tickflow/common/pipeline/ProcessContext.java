package com.seralice.tickflow.common.pipeline;

import com.seralice.tickflow.common.vo.Result;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 责任链上下文（在整个流水线各个工位之间传递的小推车）
 *
 * @param <T> 具体业务模型，必须实现 ProcessModel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessContext<T extends ProcessModel> {

    /** 标识哪条责任链业务，例如 "send"、"batchSend" */
    private String code;

    /** 存储责任链处理的核心业务数据 */
    private T processModel;

    /** 是否需要中断流水线（任何一个节点设为 true，后续节点不再执行） */
    private Boolean needBreak;

    /** 最终返回给调用方的统一结果 */
    private Result<?> response;
}
