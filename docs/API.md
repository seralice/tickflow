# TickFlow 接口文档

- 基础地址：`http://localhost:8080`
- 请求和响应均为 JSON；时间格式为 ISO-8601（例如 `2026-11-20T20:00:00`），时区 Asia/Shanghai
- 设计参考 [Austin](https://github.com/ZhongFuCheng3y/austin)，与它不同的地方以 **【差异】** 标注

## 目录

- [约定](#约定)：统一响应、错误码、枚举
- [一、渠道账号](#一渠道账号)
- [二、消息模板](#二消息模板)（含审核）
- [三、消息发送](#三消息发送)
- [四、延迟消息](#四延迟消息)

---

## 约定

### 统一响应

```json
{ "code": 0, "msg": "success", "data": { } }
```

| code | 含义 | 典型场景 |
|---|---|---|
| 0 | 成功 | |
| 400 | 参数错误 | 校验不通过，`msg` 为"字段名 + 原因" |
| 404 | 资源不存在 | 模板或账号 id 不存在，或已被删除 |
| 409 | 状态冲突 | 删除仍被模板引用的账号；发送未通过审核的营销模板 |
| 500 | 服务器内部错误 | 未预料的异常，详情只记录在服务端日志 |

- HTTP 状态码一律为 200，以响应体中的 `code` 为准（与 Austin 一致）。
- 没有数据要返回的接口，`data` 为 `null`。
- **【差异】** Austin 的错误码是字符串（`A0001` 等），这里沿用 HTTP 语义的数字码，前端只需判断 `code == 0`。

### 分页

列表接口统一使用 `page`（从 1 开始，默认 1）和 `size`（默认 20，取值 1 ~ 100），超出范围返回 400。返回：

```json
{ "total": 35, "list": [ ] }
```

### 枚举

枚举字段沿用 Austin 的整数编码。编码之间留有间隔，方便以后插入新值，也让数据库存储更紧凑。传入未定义的编码时返回 400 `xxx 取值不合法`。

| 枚举 | 取值 |
|---|---|
| ChannelType 发送渠道 | `40` 邮件 · `110` 飞书机器人 · `900` 模拟渠道（本地测试用，只打日志） |
| IdType 接收者类型 | `10` 用户 id（不做格式校验）· `50` 邮箱（发送前会做格式校验） |
| TemplateType 模板类型 | `10` 定时类（平台按 cron 触发）· `20` 实时类（业务方调用接口触发） |
| MessageType 消息类型 | `10` 通知 · `20` 营销 · `30` 验证码 |
| ShieldType 夜间屏蔽 | `10` 夜间不屏蔽 · `20` 夜间屏蔽（直接丢弃）· `30` 夜间屏蔽，次日 9 点发送 |
| MsgStatus 模板状态 | `10` 新建 · `20` 停用 · `30` 启用（只对定时类有意义） |
| AuditStatus 审核状态 | `10` 待审核 · `20` 审核通过 · `30` 审核驳回 |

各渠道对 `accountConfig`（账号配置）和 `msgContent`（消息内容）的要求：

| 渠道 | accountConfig 必填字段 | msgContent 必填字段 | 接收者类型 |
|---|---|---|---|
| 40 邮件 | `host`、`port`、`username`、`password`、`from`；可选 `sslEnable`（默认 true） | `title`、`content`（支持 HTML） | 50 邮箱 |
| 110 飞书机器人 | `webhook` | `content` | 10 用户 id（机器人发到群里，接收者只用于去重和追踪） |
| 900 模拟渠道 | 可选 `latencyMs`（默认 0）、`failRate`（0~1，默认 0） | `content` | 10 用户 id |

### 占位符

消息内容中的占位符写作 `{$变量名}`，与 Austin 一致，例如 `{$name}，你的验证码是 {$code}`。发送时用请求里的 `variables` 替换。

- 缺少变量时直接返回 400 `缺少占位符参数: name`，避免用户收到带 `{$name}` 的消息。

---

## 一、渠道账号

一个渠道下可以有多个账号，例如多个发件邮箱。模板通过 `sendAccount` 指定用哪个账号发送。

账号对象：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | |
| name | string | 账号名称 |
| sendChannel | int | 发送渠道，见 ChannelType |
| accountConfig | object | 账号配置，结构见上方渠道表；**查询时敏感字段脱敏** |
| createdAt / updatedAt | datetime | |

脱敏规则：`password` 返回 `"******"`；`webhook` 只保留前 40 个字符，后面接 `****`。

### 新增账号

`POST /api/channel-accounts`

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| name | string | 是 | 非空，≤ 100 字 |
| sendChannel | int | 是 | 必须是已支持的渠道 |
| accountConfig | object | 是 | 按渠道校验必填字段，缺失时返回 400 `accountConfig.host 不能为空` |

```json
{
  "name": "通知邮箱",
  "sendChannel": 40,
  "accountConfig": {
    "host": "smtp.qq.com", "port": 465, "username": "me@qq.com",
    "password": "授权码", "from": "TickFlow <me@qq.com>", "sslEnable": true
  }
}
```

成功：`data` 为新账号 id。

### 账号列表

`GET /api/channel-accounts?sendChannel=40`

- `sendChannel` 可选，不传则返回全部；不返回已删除的账号
- 按 `updatedAt` 倒序；账号数量很少，不分页；`data` 为账号数组，没有数据时返回 `[]`

### 修改账号

`PUT /api/channel-accounts/{id}`

- 请求体同"新增"，但 `sendChannel` 不允许修改（传了不同的值返回 400 `sendChannel 不允许修改`）
- `accountConfig` 中的敏感字段（`password`、`webhook`）**不传或传空字符串表示保持原值**，这样前端拿到脱敏数据后可以直接回填提交
- id 不存在或已删除 → 404 `账号不存在`

### 删除账号

`DELETE /api/channel-accounts/{id}`

- 软删除（`is_deleted = 1`），用条件更新 `WHERE id = ? AND is_deleted = 0`
- 仍有未删除的模板在使用该账号 → 409 `仍有 3 个模板在使用该账号`
- id 不存在或已删除 → 404 `账号不存在`

---

## 二、消息模板

模板对象：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | |
| name | string | 模板名称 |
| idType | int | 接收者类型，见 IdType |
| sendChannel | int | 发送渠道 |
| sendAccount | long | 渠道账号 id |
| templateType | int | 模板类型；目前只支持 `20` 实时类 |
| msgType | int | 消息类型 |
| shieldType | int | 夜间屏蔽类型 |
| msgContent | object | 消息内容，结构见渠道表 |
| msgStatus | int | 模板状态，新建时为 `10` |
| auditStatus | int | 审核状态，规则见下方 |
| auditReason | string | 审核意见，可能为 `null` |
| expectPushTime | string | 定时类的 cron 表达式（预留），实时类为 `null` |
| cronCrowdPath | string | 定时类的人群文件路径（预留） |
| createdAt / updatedAt | datetime | |

审核状态的初始值：
- 通知类和验证码类模板：创建时直接是 `20` 审核通过。
- 营销类模板：创建或修改内容后变为 `10` 待审核，由 AI 自动审核。**审核通过前不能发送。**

### 创建模板

`POST /api/templates`

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| name | string | 是 | 非空，≤ 100 字 |
| idType | int | 是 | 必须与渠道匹配（见渠道表），否则返回 400 `idType 与渠道不匹配` |
| sendChannel | int | 是 | 已支持的渠道 |
| sendAccount | long | 是 | 账号必须存在、未删除，且渠道与 `sendChannel` 相同，否则返回 400 `sendAccount 不存在或与渠道不匹配` |
| templateType | int | 是 | 目前只能为 `20`，否则返回 400 `templateType 暂只支持 20（实时类）` |
| msgType | int | 是 | |
| shieldType | int | 是 | |
| msgContent | object | 是 | 按渠道校验必填字段，缺失时返回 400 `msgContent.title 不能为空` |

```json
{
  "name": "面试提醒",
  "idType": 50,
  "sendChannel": 40,
  "sendAccount": 1,
  "templateType": 20,
  "msgType": 10,
  "shieldType": 10,
  "msgContent": {
    "title": "面试提醒",
    "content": "{$name}，你的{$event}将在 {$time} 开始"
  }
}
```

成功：`data` 为新模板 id。

### 查询单个模板

`GET /api/templates/{id}`

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "面试提醒",
    "idType": 50,
    "sendChannel": 40,
    "sendAccount": 1,
    "templateType": 20,
    "msgType": 10,
    "shieldType": 10,
    "msgContent": { "title": "面试提醒", "content": "{$name}，你的{$event}将在 {$time} 开始" },
    "msgStatus": 10,
    "auditStatus": 20,
    "auditReason": null,
    "expectPushTime": null,
    "cronCrowdPath": null,
    "createdAt": "2026-11-06T18:00:00",
    "updatedAt": "2026-11-06T18:00:00"
  }
}
```

id 不存在或已删除 → 404 `模板不存在`。

### 模板分页列表

`GET /api/templates?page=1&size=20&name=面试&sendChannel=40`

| 参数 | 必填 | 说明 |
|---|---|---|
| page / size | 否 | 见"分页"约定 |
| name | 否 | 模糊匹配 |
| sendChannel | 否 | 精确匹配 |

- 不返回已删除的模板，按 `updatedAt` 倒序
- `data` 为 `{ total, list }`，`list` 中的元素是模板对象

### 修改模板

`PUT /api/templates/{id}`

- 请求体同"创建"，全量覆盖
- `version` 加 1；营销类模板的 `auditStatus` 重置为 `10`，`auditReason` 清空
- id 不存在或已删除 → 404 `模板不存在`

### 删除模板

`DELETE /api/templates/{id}`

- 软删除，用条件更新 `WHERE id = ? AND is_deleted = 0`
- id 不存在或已删除 → 404 `模板不存在`

### 复制模板

`POST /api/templates/{id}/copy`

- 复制除 id 之外的内容字段，名称加上后缀 `-副本`；状态重置为新建（`msgStatus = 10`），审核状态按"初始值"规则重新计算
- 成功：`data` 为新模板 id
- id 不存在或已删除 → 404 `模板不存在`

### 查询模板审核详情

`GET /api/templates/{id}/audit`

- 成功：`data` 为 `{ templateId, templateName, msgType, auditStatus, auditReason }`
- id 不存在或已删除 → 404 `模板不存在`

### 重新提交模板审核

`POST /api/templates/{id}/audit/retry`

- 条件更新：`UPDATE message_template SET audit_status = 10, audit_reason = NULL, version = version + 1 WHERE id = ? AND is_deleted = 0 AND msg_type = 20 AND audit_status = 30`
- 成功后投递 Kafka `tickflowAudit` 异步审核队列
- 模板不存在 → 400/404 `模板不存在`
- 非营销类模板 → 409 `仅营销类模板支持重试`
- 正在审核中 → 409 `正在审核，请勿提交重复请求`
- 审核已通过 → 409 `当前审核状态不支持重试`

### 模板人工复核

`POST /api/templates/{id}/audit/manual`

- 请求体：`{ "auditStatus": 20, "auditReason": "管理员复核说明" }`
- `auditStatus` 仅支持 `20`（通过）或 `30`（驳回），否则 → 400 `仅 通过/驳回 允许复核`
- 成功更新：`audit_status`、`audit_reason`（自动加 `[人工复核结果]` 前缀）与 `version = version + 1`
- 模板不存在 → 404 `模板不存在`
- 非营销类模板 → 409 `非营销模板不允许复核`

---

## 三、消息发送

发送接口对应 Austin 的 `SendService`：接口定义放在 `tickflow-service-api` 模块，实现放在 `tickflow-service-api-impl` 模块。

**异步语义**：接口返回成功只表示"消息已被受理并写入 Kafka（或延迟队列）"，不代表已经送达。

处理流程（接入层责任链）：

```
前置校验 → 组装（查模板、渲染占位符、生成 messageId）→ 后置检查（过滤非法接收者）→ 写入 Kafka
```

### 单文案发送

`POST /api/send`

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| messageTemplateId | long | 是 | 模板必须存在、未删除，且为实时类 |
| messageParam | object | 是 | 见下表 |
| sendTime | datetime | 否 | 延迟发送时间，格式 `2026-11-11T17:30:00`；必须晚于当前时间且不超过 30 天，否则 400 `sendTime 必须晚于当前时间，且不超过 30 天`；模板 `shieldType = 20` 且 sendTime 落在夜间（22:00 ~ 08:00）时 400 `sendTime 处于夜间屏蔽时段，该模板夜间消息会被丢弃`。目前只有单文案发送支持 |

`messageParam`：

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| receiver | string | 是 | 接收者，多个用英文逗号分隔，去重后 ≤ 100 个 |
| variables | object | 否 | 占位符取值，`{"name": "小明"}`；模板中的占位符必须全部提供 |
| bizId | string | 否 | 业务方自己的消息 id，用于追踪；不传则等于 `messageId` |

```json
{
  "messageTemplateId": 1,
  "messageParam": {
    "receiver": "a@example.com,b@example.com",
    "variables": { "name": "小明", "event": "一面", "time": "20:00" }
  }
}
```

成功响应：`data` 为本次生成的发送任务。单文案发送时数组只有一个元素；同一个任务下的多个接收者共用一个 `messageId`。

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    { "messageId": "V1StGXR8_Z5jdHi6B-myT", "bizId": "V1StGXR8_Z5jdHi6B-myT", "businessId": 2000000120261106 }
  ]
}
```

- `messageId`：每个发送任务的唯一 id（nanoId）
- `businessId`：`模板类型 × 1000000 + 模板 id` 再拼上日期 `yyyyMMdd`，用于按"模板 + 日期"统计，与 Austin 一致

错误：

| 场景 | 响应 |
|---|---|
| receiver 为空 | 400 `receiver 不能为空` |
| 接收者超过 100 个 | 400 `接收者不能超过 100 个` |
| 模板不存在或已删除 | 404 `模板不存在` |
| 模板是定时类 | 400 `定时类模板不能通过接口发送` |
| 缺少占位符参数 | 400 `缺少占位符参数: name` |
| 接收者全部不合法（如邮箱格式错误） | 400 `没有合法的接收者` |
| 营销模板未通过审核 | 409 `模板未通过审核` |

- 部分接收者不合法时，静默过滤掉不合法的那部分，其余正常发送（与 Austin 一致），服务端记录 warn 日志。
- **【差异】** Austin 的请求体里还有 `code` 字段（`send` / `recall`），用来选择责任链。那是为了 SDK / RPC 调用方准备的；HTTP 接口本身已经能区分业务，所以由 Controller 内部设置。

### 多文案批量发送

`POST /api/batch-send`

适合同一个模板、但每个人的文案不同的场景，例如每个人的验证码不一样。

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| messageTemplateId | long | 是 | 同单文案发送 |
| messageParamList | array | 是 | 元素结构同 `messageParam`，1 ~ 100 个 |

- 每个 `messageParam` 生成一个发送任务，`data` 数组与 `messageParamList` 一一对应
- 某个 `messageParam` 的接收者全部不合法时，跳过这一项；全部都被跳过时返回 400 `没有合法的接收者`

### 模板测试发送

`POST /api/templates/{id}/test`

请求体就是 `messageParam`（`receiver`、`variables`）。内部调用单文案发送，方便管理后台点一下就能测试。返回值和错误码与单文案发送相同。

---

## 四、延迟消息

### 延迟发送

- 发送接口填写 `sendTime` 即为延迟发送，返回的 `messageId` 用于取消。
- `sendTime` 必须是将来的时间，且不超过 30 天。
- 延迟消息存在 Redis，写入失败时接口返回 500（不降级放行，否则会提前发送或静默丢失）。

### 取消延迟消息

`PUT /api/messages/{messageId}/cancel`

- 只有尚未触发的延迟消息可以取消
- 取消与到点触发同时发生时，只有一方能成功（两者竞争同一个 `ZREM`）
- 不存在、已经触发或已取消 → 409 `消息不存在、已触发或已取消`（Redis 里分不清"从未存在"和"已投递删除"，所以不单独返回 404）

### 夜间屏蔽次日发送

`shieldType = 30` 的消息在夜间（22:00 ~ 08:00）被拦截后，放入延迟队列，在夜间结束后 1 小时（09:00）发送：凌晨被拦截的当天发，22 点后被拦截的次日发。

---
