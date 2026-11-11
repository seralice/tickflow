package com.seralice.tickflow.api.impl.config;

import com.seralice.tickflow.api.impl.action.SendAfterCheckAction;
import com.seralice.tickflow.api.impl.action.SendAssembleAction;
import com.seralice.tickflow.api.impl.action.SendMqAction;
import com.seralice.tickflow.api.impl.action.SendPreCheckAction;
import com.seralice.tickflow.common.pipeline.BusinessProcess;
import com.seralice.tickflow.common.pipeline.ProcessController;
import com.seralice.tickflow.common.pipeline.ProcessTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 责任链流水线装配配置类
 */
@Configuration
@RequiredArgsConstructor
public class PipelineConfig {

    public static final String BUSINESS_CODE_SEND = "send";

    private final SendPreCheckAction sendPreCheckAction;
    private final SendAssembleAction sendAssembleAction;
    private final SendAfterCheckAction sendAfterCheckAction;
    private final SendMqAction sendMqAction;

    /**
     * 发送责任链模板：前置校验 -> 组装 -> 后置检查 -> 写 Kafka
     */
    @Bean("sendProcessTemplate")
    public ProcessTemplate sendProcessTemplate() {
        ProcessTemplate template = new ProcessTemplate();
        List<BusinessProcess<?>> processList = List.of(
                sendPreCheckAction,
                sendAssembleAction,
                sendAfterCheckAction,
                sendMqAction
        );
        template.setProcessList(processList);
        return template;
    }

    /**
     * 将各责任链模板注册到中央控制器 ProcessController
     */
    @Bean
    public ProcessController processController() {
        ProcessController processController = new ProcessController();
        Map<String, ProcessTemplate> templateConfig = new HashMap<>();
        templateConfig.put(BUSINESS_CODE_SEND, sendProcessTemplate());
        processController.setTemplateConfig(templateConfig);
        return processController;
    }
}
