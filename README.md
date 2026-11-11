# TickFlow

消息推送平台：业务方通过统一接口按模板发送消息（实时 / 延迟），平台负责屏蔽、去重、限频、异步投递、失败重试和 AI 合规审核。

模块划分与核心流程参考了开源项目 [Austin](https://github.com/ZhongFuCheng3y/austin)（感谢作者的分享），在此基础上围绕**投递可靠性**、**自研延迟队列**和 **AI 合规审核**做了扩展，并精简了部署依赖。

**技术栈**：Java 21 · Spring Boot 4.0 · MyBatis · MySQL 8 · Redis 7 · Kafka · Spring Kafka（`@RetryableTopic`）· OpenAI 兼容接口 + BGE-M3 向量检索

---

## 架构

```
                         ┌──────────────── 接入层（tickflow-service-api-impl）────────────────┐
POST /api/send  ───────► │ 前置校验 → 组装(查模板 / 渲染占位符 / 生成 messageId) → 后置检查 → 写 MQ │
                         └───────────────────────────────┬────────────────────────────────────┘
                                   sendTime 为空          │          sendTime 在将来
                         ┌────────────────────────────────┴───────────────────┐
                         ▼                                                    ▼
             Kafka  tickflowBusiness-{渠道码}                   Redis ZSet 延迟队列
             （按渠道一个 topic，独立消费组）                   DelayQueueScheduler 每秒 Lua 认领
                         │                         ◄──────────── 到期后写入 Kafka
                         ▼
          ┌────────── 处理层（tickflow-handler）──────────┐
          │ 幂等(SETNX) → 夜间屏蔽 → 内容去重 → 频次限制 → │ ──► 邮件 / 飞书机器人 / 模拟渠道
          └──────────────────────────────────────────────┘
             失败：-retry-60000 → -retry-300000 → … → -dlt（非阻塞退避重试 + 死信）

保存营销模板 → Kafka tickflowAudit → 检索法规 → 大模型 Tool Call 判定 → 按 version 条件回写审核状态
```

| 模块 | 职责 |
|---|---|
| `tickflow-common` | 统一返回值、异常、枚举、领域对象、责任链框架；不依赖任何中间件 |
| `tickflow-support` | 实体与 Mapper、Kafka / Redis 封装、邮件工具、AI 审核客户端与法规向量库 |
| `tickflow-service-api` | 对外发送接口与入参出参，调用方只依赖这一个模块 |
| `tickflow-service-api-impl` | 发送接口实现 + 接入层责任链 |
| `tickflow-handler` | Kafka 消费、屏蔽 / 去重 / 限频、各渠道 Handler、延迟队列调度、审核消费 |
| `tickflow-web` | 启动类、Controller、模板与渠道账号管理、全局异常处理 |

## 设计取舍

Austin 是功能完整的通用推送平台，接入了多种 MQ、配置中心和 xxl-job、Flink 等组件。TickFlow 作为个人项目，选择收窄范围、把可靠性做深：

| 方面 | TickFlow 的做法 | 原因 |
|---|---|---|
| 渠道隔离 | 每个渠道一个 Kafka topic + 独立消费组，消费线程同步处理 | 一个渠道卡住或触发 rebalance 不影响其他渠道；同步处理使异常能交给重试机制，offset 在处理完成后才提交 |
| 失败重试 | `@RetryableTopic` 非阻塞退避重试（1 / 5 / 25 / 125 分钟）+ 死信 | 只重试网络类临时故障，配置错误等"毒消息"直接进死信，避免空转 |
| 生产端 | 同步等待 broker 确认，失败返回 500；`max.block.ms` 3 秒 | 让调用方知道消息是否被受理；Kafka 不可用时快速失败，不占住 Tomcat 线程 |
| 消费幂等 | messageId `SET NX EX`，异常时删除标记 | 配合至少一次投递，避免重复发送，同时不误拦重试 |
| 延迟发送 | Redis ZSet 延迟队列，Lua 原子认领 + 租约 | 只依赖已有的 Redis，不额外部署调度中心 |
| 合规审核 | RAG + 大模型 Tool Call 结构化输出 | 营销文案发送前自动初审，人工复核兜底 |
| 持久层 | MyBatis 注解式 SQL | 个人熟悉，SQL 可控 |

## 可靠性设计

**延迟队列：至少一次投递**

- 三个 key：`tickflow:delay`（等待中，score = 到期时间）、`tickflow:delay:processing`（已认领，score = 租约到期时间）、`tickflow:delay:body`（消息内容）。
- 认领在 Lua 里原子完成：把到期消息从等待队列挪到处理队列，并回收租约已过期的消息。写入 Kafka 成功后才删除；实例中途挂掉，60 秒后租约到期，消息会被其他实例重新认领。
- 由此产生的重复投递由消费端 messageId 幂等兜底：**生产端至少一次 + 消费端幂等**。
- 取消与到点触发竞争同一个 `ZREM`，Redis 单线程执行，只有一方能成功。
- 实测：两个实例同时调度 300 条同一秒到期的消息，两边各自认领，Kafka 恰好收到 300 条、无重复；模拟实例挂掉后消息在租约到期后被回收投递。

**夜间屏蔽**：`shieldType = 30` 的消息在夜间被拦截后，先删除幂等标记再放入延迟队列，次日 9 点发送（顺序反过来会导致次日被幂等误拦）；`shieldType = 20` 的延迟消息若落在夜间，接口直接拒绝，而不是到点后静默丢弃。夜间时段只在 `ShieldType` 中定义一处，接入层和处理层共用。

**其他**：频次计数 `INCR` + `EXPIRE` 用 Lua 原子执行，避免 key 永不过期导致用户被永久限频；飞书 HTTP 客户端设置连接与请求超时并解析响应体判断真实结果；邮件区分认证失败（不重试）与连接 / 投递异常（重试）；所有状态变更使用条件更新（`UPDATE … WHERE id = ? AND status = ?`），按影响行数判断结果。

## AI 合规审核

营销类模板保存后异步送审：检索相关法规 → 拼入 prompt → 大模型通过强制 Tool Call 输出 `{pass, reason, hitArticle}` → 按 version 乐观锁条件回写。审核服务不可用时消息退避重试，重试耗尽才标记驳回并提示重新提交，而不是把系统故障当成文案违规。

**法规库**：《中华人民共和国广告法》（2021 修正）全文，取与消息内容相关的条款按"项"切成 77 个法规块：

- **通用规则常驻 prompt**（绝对化用语、虚假广告、退订方式、反诈等，所有广告都适用，约 2000 字）；
- **行业规则向量检索 Top-5**（医疗、药品、金融、教育、烟酒等）。

这样拆分是评测中得出的结论：向量检索擅长找"文案像什么"，但"营销短信缺少退订方式"这类规则描述的是"缺了什么"，文案中没有任何相似的词，检索不到。

**评测**（`AuditEvalTest`，30 条人工标注样例：17 条违规，每类含隐晦写法；13 条合规，其中 7 条是含敏感词的易误杀样例）

| 方案 | 准确率 | 正确法条进入 prompt | 说明 |
|---|---|---|---|
| 5 条手写法规，检索 Top-3 | 30 / 30 | 15 / 15 | 5 条取 3 条，随机命中也有 60%，数字说明不了检索质量 |
| 同上，关闭 RAG（对照） | 29 / 30 | — | 模型依据第四十三条驳回了一条无退订方式的营销短信：**判得对，是法规库和标注漏了这一条** |
| 广告法全文 74 条，检索 Top-3 | — | 8 / 17 | 立法目的、施行日期、罚则等泛化条款占据前排，检索明显退化 |
| **按项切块 + 通用规则常驻 + 行业规则 Top-5** | **30 / 30** | **17 / 17** | 违规召回 17 / 17，误杀 0 / 13 |

局限：样例围绕法规编写，不是独立测试集，每条只运行一次；大模型输出存在随机性。

## 本地运行

依赖：JDK 21、MySQL 8、Redis 7、Kafka（KRaft 单节点即可）。

1. 创建数据库 `tickflow`，表结构在启动时由 `tickflow-web/src/main/resources/db/schema.sql` 自动创建。
2. 配置密钥：设置环境变量，或在 `tickflow-web/src/main/resources/` 下新建 `application-local.yml`（已被 `.gitignore` 排除，存在时自动加载）：

   | 环境变量 | 说明 |
   |---|---|
   | `DB_PASSWORD` | MySQL root 密码 |
   | `AI_BASE_URL` / `AI_API_KEY` / `AI_CHAT_MODEL` | OpenAI 兼容的对话模型（需支持 tool call） |
   | `AI_EMBEDDING_BASE_URL` / `AI_EMBEDDING_API_KEY` | 向量模型，默认硅基流动 `BAAI/bge-m3` |

3. 启动：

   ```bash
   ./mvnw -pl tickflow-web -am spring-boot:run
   ```

4. 运行审核评测（会真实调用大模型，默认跳过）：

   ```bash
   ./mvnw -pl tickflow-web -am test -Deval=true -Dtest=AuditEvalTest -Dsurefire.failIfNoSpecifiedTests=false
   # 对照实验：追加 -Dai.audit.rag-enabled=false；只跑部分样例：追加 -Deval.ids=21,22
   ```

## 接口

完整说明见 [`docs/API.md`](docs/API.md)，表结构见 [`schema.sql`](tickflow-web/src/main/resources/db/schema.sql)。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/send` | 单文案发送；填写 `sendTime` 为延迟发送 |
| POST | `/api/batch-send` | 多文案批量发送 |
| PUT | `/api/messages/{messageId}/cancel` | 取消尚未触发的延迟消息 |
| POST / GET / PUT / DELETE | `/api/templates`… | 模板增删改查、复制、测试发送 |
| GET / POST | `/api/templates/{id}/audit`… | 查询审核结果、重新提交审核、人工复核 |
| POST / GET / PUT / DELETE | `/api/channel-accounts`… | 渠道账号管理（敏感字段脱敏） |

## 已知局限

- 死信目前只记录日志，尚未落库和提供重放接口。
- 渠道限流尚未实现（计划用 Redis 令牌桶）。
- 延迟队列只存在 Redis，Redis 数据丢失时没有 MySQL 补偿扫描。
- 审核消息只携带模板 id、不带 version，极端情况下死信处理会短暂把新版本标为驳回（随后被新版本的审核结果覆盖）。
- 法规检索没有相似度阈值；启动时某条法规向量化失败只记录日志。

## 许可证

[Apache License 2.0](LICENSE)
