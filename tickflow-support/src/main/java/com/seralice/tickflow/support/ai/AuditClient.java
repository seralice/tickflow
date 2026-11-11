package com.seralice.tickflow.support.ai;

import com.seralice.tickflow.support.domain.AuditResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 合规审核大模型客户端（基于 RAG 上下文增强与 Tool Choice 结构化输出）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditClient {

    private final RuleVectorStore ruleVectorStore;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${ai.openai.base-url}")
    private String openAiBaseUrl;

    @Value("${ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${ai.openai.chat-model}")
    private String chatModel;

    /** 关闭后不检索法条，只靠模型自身知识判断；用于评测对照实验，衡量 RAG 的实际贡献 */
    @Value("${ai.audit.rag-enabled:true}")
    private boolean ragEnabled;

    /**
     * 对消息模板内容执行 RAG 增强合规审核
     *
     * @param content 待审核文案
     * @return 结构化决策结果 AuditResult
     */
    public AuditResult auditContent(String content) {
        try {
            // 1. 通用规则常驻 + 行业规则检索 Top-5：通用规则（绝对化用语、虚假广告、退订）和文案语义不一定相似，
            //    比如"缺少退订方式"是缺了什么，向量检索只能找"像什么"，所以不靠检索
            List<RuleVectorStore.RuleItem> rules = new ArrayList<>();
            if (ragEnabled) {
                rules.addAll(ruleVectorStore.getGlobalRules());
                rules.addAll(ruleVectorStore.searchSimilarRules(content, 5));
            }
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < rules.size(); i++) {
                RuleVectorStore.RuleItem item = rules.get(i);
                contextBuilder.append(i + 1).append(". ")
                        .append(item.getArticle()).append("：")
                        .append(item.getRule()).append("\n");
            }

            // 2. 组装 Prompt
            String systemPrompt = "你是一名资深中国商业广告与推送消息合规审查法务专家。\n" +
                    "请根据参考法规，对用户提交的营销消息文案进行严格的法律与合规风险审查。\n" +
                    (ragEnabled
                            ? "【审查标准与参考法规证据库】：\n" + contextBuilder + "【要求】：请以提供的法规为依据，"
                            : "【要求】：请依据你所掌握的《中华人民共和国广告法》《反电信网络诈骗法》等法规，") +
                    "若存在违规（如绝对化用语、虚假宣传、诱导等），务必指出违规点并引用法条；" +
                    // 模型默认只找"写了不该写的"，不会检查"缺了必须有的"，比如营销短信缺少退订方式
                    "同时检查是否缺少法规要求必须包含的内容（如商业广告须提供拒绝接收的方式）；订单、物流、账单、验证码等非广告的服务通知不适用广告规范。" +
                    "若合规请确认放行。必须调用 submit_audit_result 函数提交结果。";

            // 3. 构建 Tool Schema 规范
            Map<String, Object> toolSchema = Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "submit_audit_result",
                            "description", "提交文案合规审查的最终决策",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "pass", Map.of("type", "boolean", "description", "是否合规：true 合规，false 违规驳回"),
                                            "reason", Map.of("type", "string", "description", "审查理由，违规需指明涉案词汇与理由"),
                                            "hitArticle", Map.of("type", "string", "description", "命中的法条全称，未违规为空字符串")
                                    ),
                                    "required", List.of("pass", "reason", "hitArticle")
                            )
                    )
            );

            // 4. 组装请求体并强制使用该 Tool
            Map<String, Object> requestBody = Map.of(
                    "model", chatModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", "待审查文案如下：\n" + content)
                    ),
                    "tools", List.of(toolSchema),
                    "tool_choice", Map.of("type", "function", "function", Map.of("name", "submit_audit_result"))
            );

            String jsonPayload = jsonMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAiBaseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[AuditClient] 大模型审查调用失败: status={}, body={}", response.statusCode(), response.body());
                throw new AuditUnavailableException("大模型调用失败，状态码: " + response.statusCode());
            }

            // 5. 解析 Tool Call 结果
            Map<String, Object> respMap = jsonMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AuditUnavailableException("大模型无响应结果");
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                Map<String, Object> firstTool = toolCalls.get(0);
                Map<String, Object> function = (Map<String, Object>) firstTool.get("function");
                String argumentsJson = (String) function.get("arguments");

                // 结构化反序列化回 AuditResult
                AuditResult result = jsonMapper.readValue(argumentsJson, AuditResult.class);
                log.info("[AuditClient] AI 审核完成！pass: {}, reason: {}, hitArticle: {}",
                        result.getPass(), result.getReason(), result.getHitArticle());
                return result;
            }

        } catch (AuditUnavailableException e) {
            // try 里自己抛的已经说明了原因，原样抛出，避免被下面的 catch 再包一层
            throw e;
        } catch (Exception e) {
            log.error("[AuditClient] 审核流程异常！content: {}", content, e);
            throw new AuditUnavailableException("审核流程异常", e);
        }

        throw new AuditUnavailableException("未能提取到结构化审核结论");
    }
}
