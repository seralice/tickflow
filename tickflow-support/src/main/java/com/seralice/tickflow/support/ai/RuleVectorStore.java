package com.seralice.tickflow.support.ai;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 广告合规法规规则轻量级向量知识库（基于余弦相似度向量检索）
 */
@Slf4j
@Component
public class RuleVectorStore {

    @Value("${ai.embedding.base-url}")
    private String embeddingBaseUrl;

    @Value("${ai.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${ai.embedding.model}")
    private String embeddingModel;

    private final JsonMapper jsonMapper = new JsonMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** 行业规则（医疗、金融、教育等），按文案向量检索 */
    private final List<RuleItem> ruleStore = new ArrayList<>();

    /** 通用规则（绝对化用语、虚假广告、退订等），所有广告都适用，不参与检索，常驻 prompt */
    private final List<RuleItem> globalRules = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItem {
        private Integer id;
        private String article;
        /** global 常驻；industry 参与检索 */
        private String scope;
        private String rule;
        private List<Double> embedding;
    }

    @PostConstruct
    public void init() {
        try {
            // 1. 读取规则文件
            ClassPathResource resource = new ClassPathResource("rules/advertising_law.json");
            try (InputStream is = resource.getInputStream()) {
                List<RuleItem> items = jsonMapper.readValue(is, new TypeReference<List<RuleItem>>() {});
                if (items == null || items.isEmpty()) {
                    log.warn("[RuleVectorStore] 未加载到广告法规规则！");
                    return;
                }

                // 没配密钥时每条请求都要等到失败，几十条法规会把启动拖住几分钟；只跳过检索，其余功能不受影响
                boolean embeddingEnabled = !embeddingApiKey.isBlank();
                if (!embeddingEnabled) {
                    log.warn("[RuleVectorStore] 未配置 AI_EMBEDDING_API_KEY，跳过行业规则向量化，审核只使用通用规则");
                }

                log.info("[RuleVectorStore] 正在初始化广告法规则向量索引 (条目数: {})...", items.size());
                for (RuleItem item : items) {
                    if ("global".equals(item.getScope())) {
                        globalRules.add(item);
                        continue;
                    }
                    if (!embeddingEnabled) {
                        continue;
                    }
                    // 把法条名称与具体规则拼接后计算向量
                    String text = item.getArticle() + "：" + item.getRule();
                    List<Double> vec = getEmbedding(text);
                    item.setEmbedding(vec);
                    ruleStore.add(item);
                }
                log.info("[RuleVectorStore] 广告法规则向量索引构建完成！通用规则 {} 条，行业规则 {} 条", globalRules.size(), ruleStore.size());
            }
        } catch (Exception e) {
            log.error("[RuleVectorStore] 广告法规则库初始化向量异常！", e);
        }
    }

    /**
     * 根据输入文案检索最相关的 Top-K 条法律法规
     *
     * @param queryText 待审核的文案
     * @param topK      检索前 K 个相关法条
     * @return 召回的法条列表
     */
    public List<RuleItem> getGlobalRules() {
        return globalRules;
    }

    public List<RuleItem> searchSimilarRules(String queryText, int topK) {
        if (ruleStore.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 1. 向量化待审核文案
            List<Double> queryVec = getEmbedding(queryText);
            if (queryVec == null || queryVec.isEmpty()) {
                return ruleStore.subList(0, Math.min(topK, ruleStore.size()));
            }

            // 2. 计算余弦相似度并按得分倒序排序
            record ScoredItem(RuleItem item, double score) {}
            List<ScoredItem> scoredList = new ArrayList<>();

            for (RuleItem rule : ruleStore) {
                double sim = cosineSimilarity(queryVec, rule.getEmbedding());
                scoredList.add(new ScoredItem(rule, sim));
            }

            scoredList.sort((a, b) -> Double.compare(b.score(), a.score()));

            List<RuleItem> result = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, scoredList.size()); i++) {
                result.add(scoredList.get(i).item());
            }
            return result;

        } catch (Exception e) {
            log.error("[RuleVectorStore] 向量相似度检索异常，降级返回默认法条！", e);
            return ruleStore.subList(0, Math.min(topK, ruleStore.size()));
        }
    }

    /**
     * 调用 Embedding API 计算文本向量
     */
    public List<Double> getEmbedding(String text) {
        try {
            Map<String, Object> reqBody = Map.of(
                    "model", embeddingModel,
                    "input", text
            );
            String jsonReq = jsonMapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingBaseUrl + "/embeddings"))
                    .header("Authorization", "Bearer " + embeddingApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonReq, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[RuleVectorStore] 调用 Embedding 接口失败: status={}, body={}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            // 解析 {"data": [{"embedding": [...]}]}
            Map<String, Object> respMap = jsonMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) respMap.get("data");
            if (dataList != null && !dataList.isEmpty()) {
                return (List<Double>) dataList.get(0).get("embedding");
            }
        } catch (Exception e) {
            log.error("[RuleVectorStore] 获取向量异常 text={}", text, e);
        }
        return Collections.emptyList();
    }

    /**
     * 计算两个向量之间的余弦相似度
     */
    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size() || v1.isEmpty()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            double a = v1.get(i);
            double b = v2.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
