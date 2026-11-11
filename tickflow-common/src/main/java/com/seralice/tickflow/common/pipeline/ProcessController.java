package com.seralice.tickflow.common.pipeline;

import com.seralice.tickflow.common.exception.BizException;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 责任链执行中枢：根据业务 code 找到对应的模板并按序执行各个工位
 */
@Data
public class ProcessController {

    /** 存储业务编码与执行模板的映射，例如 "send" -> ProcessTemplate */
    private Map<String, ProcessTemplate> templateConfig;

    /**
     * 触发执行责任链
     *
     * @param context 责任链上下文
     * @return 执行后的上下文
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ProcessContext process(ProcessContext context) {
        // 1. 前置安全校验
        if (context == null) {
            throw new BizException(500, "责任链上下文不能为空");
        }
        String businessCode = context.getCode();
        if (businessCode == null || businessCode.isBlank()) {
            throw new BizException(500, "责任链业务编码不能为空");
        }

        // 2. 找到对应流水线
        ProcessTemplate template = templateConfig != null ? templateConfig.get(businessCode) : null;
        if (template == null || template.getProcessList() == null || template.getProcessList().isEmpty()) {
            throw new BizException(500, "未找到对应的责任链配置: " + businessCode);
        }

        // 3. 顺序执行工位
        List<BusinessProcess<?>> processList = template.getProcessList();
        for (BusinessProcess businessProcess : processList) {
            businessProcess.process(context);
            // 任一节点标记 needBreak，立刻中断后续执行
            if (Boolean.TRUE.equals(context.getNeedBreak())) {
                break;
            }
        }

        return context;
    }
}
