package com.seralice.tickflow.eval;

import com.seralice.tickflow.support.ai.AuditClient;
import com.seralice.tickflow.support.ai.AuditUnavailableException;
import com.seralice.tickflow.support.ai.RuleVectorStore;
import com.seralice.tickflow.support.domain.AuditResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 审核评测：逐条跑 eval/audit-cases.json，统计准确率、违规召回率、误杀率、法条命中率和法条覆盖率
 * <p>
 * 会真实调用 Embedding 和大模型接口，默认不随 mvn test 执行，需要显式打开：
 * ./mvnw -s D:/maven/settings.xml -pl tickflow-web -am test -Deval=true -Dtest=AuditEvalTest -Dsurefire.failIfNoSpecifiedTests=false
 * <p>
 * 对照实验（关闭 RAG，只靠模型自身知识）：命令末尾加 -Dai.audit.rag-enabled=false
 * 只跑部分样例：命令末尾加 -Deval.ids=21,22
 * <p>
 * 只装配审核相关的三个 Bean，不启动 Kafka、MySQL、Redis。
 */
@SpringBootTest(classes = {AuditClient.class, RuleVectorStore.class, JsonMapper.class})
@EnabledIfSystemProperty(named = "eval", matches = "true")
class AuditEvalTest {

    /** 条号 → hitArticle 中应出现的关键字（大模型自由填写法条全称，只能按关键字匹配）；101 是反诈规则 */
    private static final Map<Integer, String> RULE_KEYWORDS = Map.of(
            9, "第九条",
            16, "第十六条",
            25, "第二十五条",
            28, "第二十八条",
            43, "第四十三条",
            101, "诈骗"
    );

    /** 法规块 id = 条号 * 100 + 块序号，评测按条号统计 */
    private static int articleOf(int chunkId) {
        return chunkId / 100;
    }

    @Autowired
    private AuditClient auditClient;

    @Autowired
    private RuleVectorStore ruleVectorStore;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${ai.audit.rag-enabled:true}")
    private boolean ragEnabled;

    @Value("${eval.ids:}")
    private List<Integer> onlyIds;

    /** expectRule 为空表示合规样例 */
    record EvalCase(Integer id, String text, Boolean expectPass, Integer expectRule, String note) {}

    @Test
    void evaluate() throws Exception {
        List<EvalCase> cases;
        try (InputStream is = new ClassPathResource("eval/audit-cases.json").getInputStream()) {
            cases = jsonMapper.readValue(is, new TypeReference<List<EvalCase>>() {});
        }
        if (!onlyIds.isEmpty()) {
            cases = cases.stream().filter(c -> onlyIds.contains(c.id())).toList();
        }

        int correct = 0, failed = 0;
        int violations = 0, violationsCaught = 0, articleHit = 0, ragHit = 0;
        int compliant = 0, falseReject = 0;
        List<String> wrong = new ArrayList<>();

        System.out.println("\n========== AI 审核评测（" + cases.size() + " 条，RAG " + (ragEnabled ? "开启" : "关闭") + "）==========");
        for (EvalCase c : cases) {
            // 检索单独再查一次：区分"没检索到对的法条"和"检索到了但模型判错"；通用规则常驻，直接算命中
            List<Integer> topIds = ruleVectorStore.searchSimilarRules(c.text(), 5).stream()
                    .map(item -> articleOf(item.getId())).toList();
            boolean global = c.expectRule() != null && ruleVectorStore.getGlobalRules().stream()
                    .anyMatch(item -> articleOf(item.getId()) == c.expectRule());

            AuditResult r;
            try {
                r = auditClient.auditContent(c.text());
            } catch (AuditUnavailableException e) {
                failed++;
                System.out.printf("#%-3d 调用失败：%s%n", c.id(), e.getMessage());
                continue;
            }

            boolean ok = c.expectPass().equals(r.getPass());
            if (ok) {
                correct++;
            }
            if (c.expectPass()) {
                compliant++;
                if (!r.getPass()) {
                    falseReject++;
                }
            } else {
                violations++;
                if (global || topIds.contains(c.expectRule())) {
                    ragHit++;
                }
                if (!r.getPass()) {
                    violationsCaught++;
                    String hit = r.getHitArticle() == null ? "" : r.getHitArticle();
                    if (hit.contains(RULE_KEYWORDS.get(c.expectRule()))) {
                        articleHit++;
                    }
                }
            }

            System.out.printf("#%-3d %s 期望=%s 实际=%s 检索=%s | %s%n", c.id(), ok ? "✓" : "✗",
                    c.expectPass() ? "通过" : "驳回", r.getPass() ? "通过" : "驳回", topIds, c.note());
            if (!ok) {
                wrong.add(String.format("#%d %s%n    文案：%s%n    理由：%s%n    法条：%s",
                        c.id(), c.note(), c.text(), r.getReason(), r.getHitArticle()));
            }
        }

        int valid = cases.size() - failed;
        System.out.println("\n---------- 指标 ----------");
        System.out.printf("有效样例：%d（调用失败 %d）%n", valid, failed);
        System.out.printf("准确率：%s%n", rate(correct, valid));
        System.out.printf("违规召回率（违规样例被驳回）：%s%n", rate(violationsCaught, violations));
        System.out.printf("误杀率（合规样例被驳回）：%s%n", rate(falseReject, compliant));
        System.out.printf("法条命中率（驳回时引用了正确法条）：%s%n", rate(articleHit, violationsCaught));
        // 与 RAG 开关无关，衡量的是"正确法条有没有进 prompt"：通用规则常驻算命中，行业规则看检索 Top-5
        System.out.printf("法条覆盖率（正确法条进入 prompt）：%s%n", rate(ragHit, violations));
        if (!wrong.isEmpty()) {
            System.out.println("\n---------- 判错样例 ----------");
            wrong.forEach(System.out::println);
        }
        System.out.println("==========================================\n");
    }

    private static String rate(int hit, int total) {
        return total == 0 ? "-" : String.format("%d/%d = %.1f%%", hit, total, hit * 100.0 / total);
    }
}
