# S4-C Java 对接 Python 政策问答、可信引用校验、业务助手编排与本地降级

## 一、定位、基线与施工边界

- **施工基线**：个人集成分支 `integration/20261101-backend-p0-security`，基线 SHA `f14f8dfe43d724a6edd9c05ea24d3e8a53eb2842`。
- **施工分支**：`feat/20261105-s4c-policy-python-adapter`（独立 worktree 隔离施工，不修改 `main`、`Snapshot`、`integration`、S3 分支及任何前端文件）。
- **契约状态**：[doc/course/s1/cross-team-contract-draft.md](../s1/cross-team-contract-draft.md) E.1/E.2 的 `POST /internal/policy-query` 当前为 **PROPOSED v1.0** 草案，尚未经跨组冻结签字。本轮仅完成 Java 侧兼容适配、可信引用与章节正文核验、业务编排修复、确定性本地降级与 Mock/Stub 自动化测试；**不声称已完成真实 Python 服务联调验收**（`NOT_VERIFIED`）。
- **对外接口兼容**：保持既有 `/api/assistant/policy/query`、`/api/assistant/policy/suggestions`、`/api/assistant/policy/check-materials` URL、HTTP 方法、请求参数与既有响应字段完全兼容，仅以向后兼容方式追加来源模式与版本标记字段。
- **职责红线**：政策问答属于只读检索增强与办理指引生成，不授予任何人口、户籍、迁移、审批或材料的数据库写入权限。

---

## 二、整体调用链与单次调用防放大设计

```text
PolicyAssistantController (POST /api/assistant/policy/query)
    │  提取 S2TraceContext.traceId 与认证用户可信 regionCode（不信任客户端伪造区划）
    ▼
PolicyTaskAgentService.execute(question, history, traceId)
    │  1. 首轮调用 PolicyAssistantService.queryPrimary(query0, history, businessType, regionCode, traceId)
    │       ├── Python 启用：调用 PolicyQueryClient (HttpPolicyQueryClient / MockPolicyQueryClient)
    │       │     └── PolicyPythonResponseValidator（结构 + 引用双向核验 + 可信来源注册表与章节正文摘录核对）
    │       └── Python 未启用或异常/核验失败：回退 Java 本地 Markdown 检索 (JAVA_LOCAL_FALLBACK)
    │  2. 仅当首轮已回退为本地检索（非 PYTHON_HYBRID_RAG）时，
    │     才对扩展查询执行最多 2 次本地检索调用 queryLocalOnly(...)
    │  3. 引用融合校验 (fusePythonCitations)：若 Python 引用与意图不匹配或被过滤，
    │     触发整体一致性降级，丢弃 Python 回答/引用/版本标记并回退本地检索结果
    ▼
PolicyAssistantController 汇总响应并回写 X-Trace-Id
```

- **单次调用约束**：`PolicyTaskAgentService` 对最多 3 个扩展查询仅在首轮主查询（Query 0）发起最多 **1 次** Python 远程调用；若首轮超时、报错或触发降级，后续扩展查询固定调用 `policyService.queryLocalOnly(...)`，避免单次用户提问触发多次远程超时叠加。

---

## 三、Python 内部 HTTP 契约适配 (`POST /internal/policy-query`)

### 1. 请求结构 (`PolicyPythonContracts.Request`)
- `schemaVersion`：固定 `"1.0"`。
- `requestId`：单次内部调用生成的唯一 UUID。
- `traceId`：透传 `S2TraceContext` 中的全链路 `traceId`（同时写入 HTTP Header `X-Trace-Id` 与 `X-Request-Id`）。
- `question`：去首尾空白并校验最大 500 字符。
- `history`：最多保留最近 **6 轮**对话，仅保留 `role`（`user`/`assistant`）与 `content`（单条截断至 500 字符）。
- `businessType`：规范化后的业务类型代码（可空）。
- `regionCode`：**仅来自 Java 服务端已认证用户的安全上下文**（`AuthenticatedUser.regionCode()`），普通请求无法伪造服务端授权区划。
- `locale`：固定 `"zh-CN"`。
- **敏感信息零外发**：请求体与请求头严禁包含浏览器 `Authorization` JWT、用户密码、身份证号、手机号、数据库主键、`DataScope` 内部结构、服务端物理路径或外部模型 API Key。

### 2. 响应结构 (`PolicyPythonContracts.Response`)
采用 Jackson 强类型反序列化，禁止通过自由文本正则表达式猜测引用：
- 顶层字段：`schemaVersion`、`requestId`、`traceId`、`outcome`（`ANSWERED` / `INSUFFICIENT_EVIDENCE`）、`answer`、`citationIdsUsed`、`citations`、`knowledgeBaseVersion`、`modelVersion`、`retrievalVersion`、`rerankerVersion`、`evidenceLevel`（`SUFFICIENT` / `PARTIAL` / `INSUFFICIENT`）、`elapsedMs`。
- `citations[]` 字段：`sourceId`、`title`、`section`、`category`、`sourceType`、`excerpt`、`sourceVersion`、`effectiveFrom`、`effectiveTo`、`regionCode`、`retrievedAt`。

---

## 四、服务间认证、传输安全、真实端到端截止时间与有界流式取消

### 1. 独立配置 (`PolicyPythonProperties` / `policy.python.*`)
与旧版外部模型配置 `policy.ai.*` 完全隔离：
- `policy.python.enabled`：默认 `false`。
- `policy.python.client`：`MOCK` 或 `HTTP`，默认 `MOCK`。
- `policy.python.base-url`：内部服务地址，仅从服务端可信配置读取，固定拼接 `/internal/policy-query`，不接受客户端传入 URL。
- `policy.python.connect-timeout`：默认 `2s`（范围 `100ms`～`10s`）。
- `policy.python.request-timeout`：默认 `8s`（范围 `100ms`～`30s`）。
- `policy.python.auth-token`：内部服务间认证令牌（支持环境变量 `POLICY_PYTHON_AUTH_TOKEN`）。
- `policy.python.allow-insecure-local-http`：**生产配置默认 `false`**；仅在隔离开发/测试配置（`src/test/resources/application.properties`）下显式开启且仅允许回环地址。

### 2. 传输与认证强制策略 (`HttpPolicyQueryClient`)
- **禁止重定向**：`HttpClient.Redirect.NEVER`，任何 `3xx` 响应立即拒绝并触发本地降级（`PYTHON_HTTP_REDIRECT_REJECTED`）。
- **生产环境安全门禁**：
  - 当激活 `prod` / `production` profile 或 `allow-insecure-local-http=false` 时，强制要求 `https://` 加密传输，拒绝任何明文 `http://`（含 `localhost`），失败原因为 `PYTHON_INSECURE_TRANSPORT_REJECTED`。
  - 即使在非生产环境开启 `allow-insecure-local-http=true`，也仅允许 `localhost`、`127.0.0.1`、`::1` 使用 `http://`；任何非回环远程地址使用 `http://` 一律拒绝。
  - 强制要求配置非空、无空白/换行注入的 `policy.python.auth-token`（发送 `Authorization: Bearer <service-token>`），缺失时拒绝调用并降级为 `PYTHON_AUTH_TOKEN_MISSING`。

### 3. 真实端到端 HTTP 截止时间与 64 KB 有界 `BoundedBodySubscriber`
- **统一端到端截止时间 (`deadlineNanos`)**：
  - 单次请求在发起前计算唯一截止时间 `deadlineNanos = System.nanoTime() + requestTimeout.toNanos()`，覆盖**建立连接、等待响应头、接收完整响应体及网络读取停顿/慢速分块流**全过程。
  - 采用 `HttpClient.sendAsync(...)` 配合自定义 `BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]>`，并在调用线程通过 `responseFuture.get(remainingNanos, TimeUnit.NANOSECONDS)` 强制等待完整响应体完成。
  - 即使服务端在超时前返回响应头，随后响应体停顿（0 字节或半截停顿）或以慢速分块（trickle chunks）持续发送字节，也无法延长统一的 `deadlineNanos`。
- **网络读取期间 64 KB 上限与主动取消回收**：
  - `BoundedBodySubscriber.onNext(List<ByteBuffer>)` 在接收每个网络 `ByteBuffer` 分块时实时检查 `deadlineNanos` 与累计字节数 `totalRead + len <= 65536`（64 KB）。
  - 一旦超过 64 KB 或到达 `deadlineNanos`（或外层 `responseFuture.get` 触发 `TimeoutException`），立即调用 `Flow.Subscription.cancel()` 与 `responseFuture.cancel(true)` 主动取消底层 HTTP 响应流订阅并释放连接资源，不创建额外后台阻塞线程，也绝不先无限量读入内存再检查长度。

---

## 五、可信引用校验与章节正文真实性核对 (`PolicyPythonResponseValidator`)

结构合法（`schemaVersion`、`sourceId`、`sourceVersion`、`[[sourceId]]` 标记完整）只能证明 JSON 契约格式合规，不能自动证明政策来源真实可靠。S4-C 实施结构、注册表元数据与章节正文摘录三级核验：

### 1. 结构与双向引用一致性校验
1. `schemaVersion` 必须精确为 `"1.0"`（否则 `PYTHON_SCHEMA_UNSUPPORTED`）。
2. `requestId` 与 `traceId` 必须与本次请求精确一致。
3. `outcome == "INSUFFICIENT_EVIDENCE"` 时不得携带引用或伪造肯定性办理结论。
4. `outcome == "ANSWERED"` 时：`answer`、`citations`（1～8 条）、`citationIdsUsed`（1～8 条）均非空，且正文中所有 `[[sourceId]]` 标记集合必须与 `citationIdsUsed` 双向闭合一致，校验通过后重写为有序 `[1]..[N]`。

### 2. 可信来源注册表与章节正文摘录核对
`PolicyAssistantService` 启动时扫描内置 `classpath*:policy-knowledge/*.md`（19 份受控政策文档、56 个分块），构建不可变的**可信政策来源注册表 (`registeredSources`)**，记录每个 `sourceId`（即 `logicalPath`）的权威 `title`、`category`、`sourceType`、`version`、合法章节集合 `sections` 以及**各章节原始正文映射 `sectionContents`**（`Map<String, String>`）：
- **精确标识匹配与防伪造后缀/冒用拦截**：
  - 仅当 `citation.sourceId` 精确等于 `registeredSources` 中的键（`logicalPath`）时才视为命中注册来源；
  - **禁止任意 `sourceId` 后缀自动继承可信身份**：若 `sourceId` 不在注册表中，但使用了已注册 `logicalPath` 加伪造后缀（如 `migration-in-guide-forged`、`migration-in-guide.v2`、`migration-in-guide_extra`），或冒用已注册文档的 `title`，或冒用内部专属 `sourceType`（`PROJECT_GUIDE` / `SYSTEM_GUIDE`），立即判定为伪造内部来源（`PYTHON_CITATION_SOURCE_ID_SPOOFED` / `PYTHON_CITATION_SOURCE_TYPE_SPOOFED`）并拒绝降级。
- **元数据与章节正文摘录可追溯性核验**：
  - 对命中本地注册表的 `citation`，必须同时核对 `title`、`category`、`sourceType`、`sourceVersion`、`section` 与注册表完全一致；
  - 进一步调用 `PolicyPythonResponseValidator.isExcerptTraceableToSection(excerpt, trustedSectionText)` 验证 `excerpt` 必须可追溯至该文档对应 `section` 的真实正文：
    1. 确定性规范化规则（`normalizeForExcerptVerification`）：剥离末尾截断省略号（`…` 或 `...`），移除空白字符（含全角空格），并将等价中英文标点（`:`/`：`、`;`/`；`、`,`/`，`、`(`/`（`、`)`/`）`、中英文双引号）统一规范化；
    2. 规范化后的 `excerpt` 有效长度必须 `>= 8` 字符，且必须是对应章节规范化正文的连续子串；
    3. 若摘录内容被篡改，或摘录虽来自同一文档但属于其他章节（跨章节张冠李戴），立即抛出 `PYTHON_CITATION_EXCERPT_MISMATCH` 并回退 Java 本地检索。
- **未注册外部来源的保守等级与材料清单隔离**：
  - 若 `citation.sourceId` 属于未注册的外部来源（如 `EXTERNAL_POLICY`），即使结构校验通过且 Python 自报 `evidenceLevel = "SUFFICIENT"`，`allSourcesRegistryVerified` 仍为 `false`，Java 端强制将其最终证据等级保守限制为 `evidenceStatus = "PARTIAL"`、设置 `needHumanHelp = true`；
  - **严禁将未核验外部摘录提升为强制材料要求**：在 `PolicyTaskAgentService`（构造 `requiredMaterials`、`conditions`、`steps`）和 `PolicyWorkflowService`（构造 `checklist` 的“材料核对”与“办理流程”项）中，仅采纳通过 `policyService.isTrustedRegisteredCitation(c)` 核实的本地受控来源；未验证外部来源的摘录绝不进入强制材料清单。
- **语义验证边界声明**：
  - 引用内容通过本地来源与章节正文核验，仅证明所附 `citation.excerpt` 真实来源于受控政策文档的对应章节，不代表生成式模型回答中的每项自由文本推论已经过形式化语义证明；正式办理要求始终以申请页面校验及主管部门最新规定为准。

---

## 六、业务助手编排、融合降级一致性与 Legacy LLM 模式一致性

### 1. 融合阶段验证失败时的整体一致性降级 (`PolicyTaskAgentService`)
若 Python 回答在 `PolicyTaskAgentService.tryBuildVerifiedPythonAgentResponse` 阶段因引用被意图过滤丢弃或引用映射不一致而验证失败：
- **禁止只替换 `answerSummary` 而保留失效的 Python 元数据**；
- 立即调用 `executeLocalFallback(...)` 执行整体一致性降级：丢弃失效的 Python 回答与引用，重新计算 Java 本地引用与 `evidenceStatus`，将 `mode` 更新为 `"RETRIEVAL_ONLY"`、`sourceMode` 更新为 `"JAVA_LOCAL_FALLBACK"`，清除全部 Python 版本标记（`knowledgeBaseVersion = "LOCAL_MD_V1"`，`modelVersion = null`，`retrievalVersion = null`，`rerankerVersion = null`），并重算 `needHumanHelp`。

### 2. Legacy LLM 模式回答与来源标记一致性 (`PolicyAssistantService` & `PolicyTaskAgentService`)
当 `policy.python.enabled=false` 且显式启用旧版 `policy.ai.enabled=true` 时：
1. **不无意义消耗远程额度**：仅当主查询本地检索命中有效政策依据（`!hits.isEmpty() && confidence >= 0.15`）时才调用一次 `tryAi`，扩展查询绝不调用 `tryAi`；Python 启用且故障降级时（`allowLegacyAi = false`）绝不调用 Legacy LLM。
2. **Legacy 回答安全与引用校验 (`validateLegacyAiAnswer`)**：Legacy LLM 返回的文本必须通过安全模式检查（禁止物理路径、凭据、越权指令或 SQL），且必须包含合法的 `[1]..[N]` 引用标记（不得包含越界引用如 `[99]`）。
3. **最终展示回答与 `mode`/`sourceMode` 严格一致**：
   - `PolicyTaskAgentService.buildLocalAgentResponse` 通过 `remapLegacyAnswerCitations` 将 Legacy 回答中的 `[k]` 映射至融合后的本地引用序号，并**将通过校验的 Legacy 回答实际作为最终 `answerSummary` 展示**，此时标记 `mode = "AI_ASSISTED"`、`sourceMode = "JAVA_LEGACY_AI_ASSISTED"`、`modelVersion = model`；
   - 若未启用 Legacy LLM、调用失败、安全/引用校验不通过或融合映射失败，最终 `answerSummary` 采用确定性 Java 模板构造，此时严格标记 `mode = "RETRIEVAL_ONLY"`、`sourceMode = "JAVA_LOCAL_FALLBACK"`、`modelVersion = null`，绝不把未采用 Legacy 回答的模板结果标记为 `AI_ASSISTED`。

---

## 七、验收状态台账

- **Python 政策问答 Java 适配、端到端 HTTP 超时与流取消、可信引用正文核验、Legacy 一致性及 H2/Mock/HTTP Stub 测试**：已完成本地自动化验证。
- **真实 Python 服务跨组联调**：`NOT_VERIFIED`（当前按 S1 E.1/E.2 PROPOSED v1.0 契约实现，Mock 及合成来源测试不等于真实 Python 政策核验验收）。
- **RabbitMQ 真实 Broker 联调**：`NOT_VERIFIED`。
- **前端联调**：`NOT_VERIFIED`（本轮未修改 `frontend/` 下任何文件）。
- **真实 MySQL 执行验证**：`NOT_VERIFIED`（S4-C 无新增数据库表或 Flyway 迁移，全部测试在隔离 H2 环境运行）。
- **历史审计项状态**：
  - 审计 `#3`（长事务）：保持 `OPEN`；
  - 审计 `#33`（区划层级规则）：保持 `OPEN`；
  - 审计 `#21`（人员版本与输入精确校验）：保持 `FIXED_VERIFIED`。
