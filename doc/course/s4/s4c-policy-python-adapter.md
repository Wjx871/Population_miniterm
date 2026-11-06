# S4-C Java 对接 Python 政策问答、可信引用校验、业务助手编排与本地降级

## 一、定位、基线与施工边界

- **施工基线**：个人集成分支 `integration/20261101-backend-p0-security`，基线 SHA `f14f8dfe43d724a6edd9c05ea24d3e8a53eb2842`。
- **施工分支**：`feat/20261105-s4c-policy-python-adapter`（独立 worktree 隔离施工，不修改 `main`、`Snapshot`、`integration`、S3 分支及任何前端文件）。
- **契约状态**：[doc/course/s1/cross-team-contract-draft.md](../s1/cross-team-contract-draft.md) E.1/E.2 的 `POST /internal/policy-query` 当前为 **PROPOSED v1.0** 草案，尚未经跨组冻结签字。本轮仅完成 Java 侧兼容适配、可信引用核验、业务编排修复、确定性本地降级与 Mock/Stub 自动化测试；**不声称已完成真实 Python 服务联调验收**（`NOT_VERIFIED`）。
- **对外接口兼容**：保持既有 `/api/assistant/policy/query`、`/api/assistant/policy/suggestions`、`/api/assistant/policy/check-materials` URL、HTTP 方法、请求参数与既有响应字段完全兼容，仅以向后兼容方式追加来源模式、降级原因与版本标记字段。
- **职责红线**：政策问答属于只读检索增强与办理指引生成，不授予任何人口、户籍、迁移、审批或材料的数据库写入权限。

---

## 二、整体调用链与单次调用防放大设计

```text
PolicyAssistantController (POST /api/assistant/policy/query)
    │  提取 S2TraceContext.traceId 与认证用户可信 regionCode（不信任客户端伪造区划）
    ▼
PolicyTaskAgentService.answer(request, trustedRegionCode)
    │  1. 首轮调用 PolicyAssistantService.answer(request, trustedRegionCode)
    │       ├── Python 启用：调用 PolicyQueryClient (HttpPolicyQueryClient / MockPolicyQueryClient)
    │       │     └── PolicyPythonResponseValidator（结构 + 引用双向核验 + 可信来源注册表核对）
    │       └── Python 未启用或异常/核验失败：回退 Java 本地 Markdown 检索 (JAVA_LOCAL_FALLBACK)
    │  2. 仅当首轮已回退为 JAVA_LOCAL_FALLBACK（非 PYTHON_HYBRID_RAG）时，
    │     才对扩展查询执行最多 2 次本地检索调用 answerLocalOnly(...)
    │  3. 引用融合校验 (fusePythonCitations)：若 Python 引用与意图不匹配被全部过滤，
    │     触发整体一致性降级，丢弃 Python 回答/引用/版本标记并回退本地检索结果
    ▼
PolicyAssistantController 汇总响应并回写 X-Trace-Id
```

- **单次调用约束**：`PolicyTaskAgentService` 原有逻辑会对最多 3 个扩展查询循环调用 `policyAssistantService.answer(...)`。S4-C 将 Python 调用严格限制在**每次用户提问最多 1 次**：
  - 若首轮 `PYTHON_HYBRID_RAG` 成功通过校验，直接使用该结果进入融合，不再为扩展查询重复请求 Python；
  - 若首轮 Python 超时、报错或返回 `INSUFFICIENT_EVIDENCE` 触发降级，后续扩展查询全部调用 `policyAssistantService.answerLocalOnly(...)`，避免单次用户提问触发 3 次 8 秒超时（24 秒级联阻塞）。

---

## 三、Python 内部 HTTP 契约适配 (`POST /internal/policy-query`)

### 1. 请求结构 (`PolicyPythonContracts.PythonPolicyQueryRequest`)
- `schemaVersion`：固定 `"1.0"`。
- `requestId`：单次内部调用生成的唯一 UUID。
- `traceId`：透传 `S2TraceContext` 中的全链路 `traceId`（同时写入 HTTP Header `X-Trace-Id` 与 `X-Request-Id`）。
- `question`：去首尾空白并截断至最大 500 字符。
- `history`：最多保留最近 **6 轮**对话，仅保留 `role`（`user`/`assistant`）与 `content`（单条截断至 500 字符）。
- `businessType`：规范化后的业务类型代码（可空）。
- `regionCode`：**仅来自 Java 服务端已认证用户的安全上下文**（`AuthenticatedUser.regionCode()`），普通请求无法伪造服务端授权区划。
- `locale`：固定 `"zh-CN"`。
- **敏感信息零外发**：请求体与请求头严禁包含浏览器 `Authorization` JWT、用户密码、身份证号、手机号、数据库主键、`DataScope` 内部结构、服务端物理路径或外部模型 API Key。

### 2. 响应结构 (`PolicyPythonContracts.PythonPolicyQueryResponse`)
采用 Jackson 强类型反序列化，禁止通过自由文本正则表达式猜测引用：
- 顶层字段：`schemaVersion`、`requestId`、`traceId`、`outcome`（`ANSWERED` / `INSUFFICIENT_EVIDENCE`）、`answer`、`citationIdsUsed`、`citations`、`knowledgeBaseVersion`、`modelVersion`、`retrievalVersion`、`rerankerVersion`、`evidenceLevel`（`SUFFICIENT` / `PARTIAL` / `NONE`）、`elapsedMs`。
- `citations[]` 字段：`sourceId`、`title`、`section`、`category`、`sourceType`、`excerpt`、`sourceVersion`、`effectiveFrom`、`effectiveTo`、`regionCode`、`retrievedAt`。

---

## 四、服务间认证、传输安全与有界流式读取

### 1. 独立配置 (`PolicyPythonProperties` / `policy.python.*`)
与旧版外部模型配置 `policy.ai.*` 完全隔离：
- `policy.python.enabled`：默认 `false`。
- `policy.python.client`：`MOCK` 或 `HTTP`，默认 `MOCK`。
- `policy.python.base-url`：内部服务地址，仅从服务端可信配置读取，固定拼接 `/internal/policy-query`，不接受客户端传入 URL。
- `policy.python.connect-timeout`：默认 `2000ms`（范围 `100ms`～`10000ms`）。
- `policy.python.request-timeout`：默认 `8000ms`（范围 `200ms`～`30000ms`）。
- `policy.python.auth-token`：内部服务间认证令牌（支持环境变量 `POLICY_PYTHON_AUTH_TOKEN`）。
- `policy.python.allow-insecure-local-http`：**生产配置默认 `false`**；仅在隔离开发/测试配置（`src/test/resources/application.properties`）下显式开启且仅允许回环地址。
- `policy.python.max-response-bytes`：默认 `65536`（64 KB）。

### 2. 传输与认证强制策略 (`HttpPolicyQueryClient`)
- **禁止重定向**：`HttpClient.Redirect.NEVER`，任何 `3xx` 响应立即失败并触发本地降级。
- **生产环境安全门禁**：
  - 当激活 `prod` / `production` profile 或 `allow-insecure-local-http=false` 时，强制要求 `https://` 加密传输，拒绝任何明文 `http://`（含 `localhost`），失败原因为 `PYTHON_INSECURE_TRANSPORT_REJECTED`。
  - 即使在非生产环境开启 `allow-insecure-local-http=true`，也仅允许 `localhost`、`127.0.0.1`、`::1` 使用 `http://`；任何非回环远程地址使用 `http://` 一律拒绝。
  - 当目标为 `https://`、非回环远程地址、生产环境或 `allow-insecure-local-http=false` 时，强制要求配置非空 `policy.python.auth-token`（发送 `Authorization: Bearer <service-token>` 与 `X-Internal-Token`），否则直接拒绝并降级为 `PYTHON_AUTH_TOKEN_MISSING`。

### 3. 网络层 64 KB 有界流式读取
- 使用 `HttpResponse.BodyHandlers.ofInputStream()` 获取响应输入流，在 `readBoundedBody` 循环读取网络字节流期间实时累加字节数。
- 一旦累计读取字节数超过 `maxResponseBytes`（默认 64 KB），**立即中断网络流读取、关闭 `InputStream` 并抛出 `PYTHON_RESPONSE_TOO_LARGE`**，绝不先将完整响应读入内存再检查长度。

---

## 五、可信引用校验与来源真实性核对 (`PolicyPythonResponseValidator`)

结构合法（`schemaVersion`、`sourceId`、`sourceVersion`、`[[sourceId]]` 标记完整）只能证明 JSON 契约格式合规，不能自动证明政策来源真实可靠。S4-C 实施双层校验：

### 1. 结构与双向引用一致性校验
1. `schemaVersion` 必须精确为 `"1.0"`（否则 `PYTHON_UNSUPPORTED_SCHEMA`）。
2. `requestId` 必须与本次请求一致（否则 `PYTHON_REQUEST_ID_MISMATCH`）。
3. `outcome == "INSUFFICIENT_EVIDENCE"` 时直接触发 `PYTHON_INSUFFICIENT_EVIDENCE` 降级，不采纳无依据自由回答。
4. `outcome == "ANSWERED"` 时：`answer`、`citations`、`citationIdsUsed`、`knowledgeBaseVersion`、`modelVersion`、`retrievalVersion`、`rerankerVersion`、`evidenceLevel` 均非空。
5. 每条 `citation` 的 `sourceId`（正则 `^[A-Za-z0-9._/-]{3,120}$`，禁止 `..`、`:`、`\`）、`title`、`section`、`category`、`sourceType`、`excerpt`（≤ 600 字符）、`sourceVersion` 必填；若存在 `effectiveFrom` 与 `effectiveTo` 则校验日期格式及先后顺序。
6. **双向引用闭合**：
   - `citationIdsUsed` 中的每个 ID 必须存在于 `citations[].sourceId`；
   - `answer` 正文中所有 `[[...]]` 标记的 ID 集合必须与 `citationIdsUsed` 完全一致，禁止悬空引用或未声明引用；
   - 校验通过后将正文中的 `[[sourceId]]` 净化为可读的 `[title - section]`，过滤最终 `citations` 仅保留 `citationIdsUsed` 实际使用的条目。

### 2. 可信来源注册表核对与外部来源保守降级
`PolicyAssistantService` 启动时扫描内置 `classpath*:policy-knowledge/*.md`（共 19 份受控政策文档、56 个分块），构建不可变的**可信政策来源注册表 (`registeredSources`)**，记录每个 `sourceId`（即 `logicalPath`）的权威 `title`、`category`、`sourceType`、`version` 与合法章节集合 `sections`：
- **已注册本地政策来源**：若 Python 返回的 `citation.sourceId` 命中注册表，必须严格核对其 `title`、`category`、`sourceType`、`sourceVersion` 及 `section` 是否与注册表一致；任一元数据被篡改或伪造，立即判定 `PYTHON_CITATION_INVALID` 并回退 Java 本地检索。
- **未注册外部来源（含合成 Mock 来源）**：若 `citation.sourceId` 不在本地注册表中（如跨组扩展来源或测试合成来源），即使结构校验通过且 Python 自报 `evidenceLevel = "SUFFICIENT"`，Java 端也**强制将其最终证据等级保守限制为 `evidenceStatus = "PARTIAL"`，并强制设置 `needHumanHelp = true`**，绝不仅凭 Python 自报 `SUFFICIENT` 就向前端宣称政策真实性已完全核实。

---

## 六、业务助手编排修复与降级整体一致性 (`PolicyTaskAgentService`)

### 1. 优先采纳已验证的 Python 回答 (`selectPreferredAnswer`)
旧版 `PolicyTaskAgentService.buildAnswerSummary` 无论底层服务返回何种模式，始终使用硬编码模板覆盖 `answerSummary`。S4-C 新增 `selectPreferredAnswer`：
- 当主查询 `sourceMode == "PYTHON_HYBRID_RAG"` 且其引用的 `citations` 在融合阶段未被过滤为空时，优先采用经过 `PolicyPythonResponseValidator` 核验与净化后的 Python 回答作为主回答，并在其后补充不重复的政策要点；
- 当 `sourceMode` 为 `JAVA_LOCAL_FALLBACK`、`JAVA_LOCAL_ONLY` 或 `LEGACY_LLM` 时，完全保持原有模板摘要逻辑。

### 2. 融合阶段验证失败时的整体一致性降级
若 Python 回答在 `PolicyTaskAgentService.fusePythonCitations` 阶段因引用与业务意图（`businessType` / 主题关键词）严重不匹配导致有效引用被全部过滤：
- **禁止只替换 `answerSummary` 而保留失效的 Python 元数据**；
- 立即调用 `policyAssistantService.answerLocalOnly(...)` 执行整体一致性降级：
  1. 丢弃失效的 Python 回答；
  2. 丢弃失效的 Python 引用，重新计算并融合 Java 本地政策引用；
  3. 重新计算本地 `evidenceStatus`（`SUFFICIENT` / `PARTIAL` / `LOW`）；
  4. 将 `mode` 更新为 `"RETRIEVAL_ONLY"`，`sourceMode` 更新为 `"JAVA_LOCAL_FALLBACK"`，`fallbackUsed = true`，`fallbackReason = "PYTHON_CITATION_INVALID"`；
  5. 清除不再适用的 Python 版本标记（`knowledgeBaseVersion = "LOCAL_MD_V1"`，`modelVersion = null`，`retrievalVersion = null`，`rerankerVersion = null`）；
  6. 重新计算 `needHumanHelp`，保持与本地实际证据状态严格一致。

---

## 七、向后兼容响应字段与降级原因码

`POST /api/assistant/policy/query` 在保留全部原有字段（`question`、`intent`、`businessType`、`answerSummary`、`handlingSteps`、`requiredMaterials`、`cautions`、`needHumanHelp`、`mode`、`retrievedCount`、`citations`）的基础上，新增以下兼容字段：

| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `sourceMode` | `String` | `PYTHON_HYBRID_RAG` / `JAVA_LOCAL_FALLBACK` / `JAVA_LOCAL_ONLY` / `LEGACY_LLM` |
| `fallbackUsed` | `boolean` | 是否触发了从 Python 到本地检索的降级 |
| `fallbackReason` | `String` | 降级原因码（未降级或未启用 Python 时为 `null`） |
| `evidenceStatus` | `String` | `SUFFICIENT` / `PARTIAL` / `LOW` / `NONE` |
| `knowledgeBaseVersion` | `String` | 知识库版本（本地模式为 `LOCAL_MD_V1`） |
| `modelVersion` | `String` | 模型版本（本地模式为 `null`） |
| `retrievalVersion` | `String` | 检索管线版本（本地模式为 `null`） |
| `rerankerVersion` | `String` | 重排序版本（本地模式为 `null`） |

支持的结构化 `fallbackReason` 枚举包括：
- `PYTHON_URL_NOT_CONFIGURED`、`PYTHON_AUTH_TOKEN_MISSING`、`PYTHON_INSECURE_TRANSPORT_REJECTED`
- `PYTHON_CONNECT_FAILED`、`PYTHON_CONNECT_TIMEOUT`、`PYTHON_REQUEST_TIMEOUT`
- `PYTHON_REDIRECT_REJECTED`、`PYTHON_HTTP_401_403`、`PYTHON_HTTP_429`、`PYTHON_HTTP_5XX`、`PYTHON_HTTP_ERROR`
- `PYTHON_RESPONSE_TOO_LARGE`、`PYTHON_INVALID_JSON`、`PYTHON_UNSUPPORTED_SCHEMA`、`PYTHON_REQUEST_ID_MISMATCH`
- `PYTHON_INCOMPLETE_RESPONSE`、`PYTHON_CITATION_INVALID`、`PYTHON_CITATION_MISMATCH`、`PYTHON_INSUFFICIENT_EVIDENCE`

---

## 八、全链路追踪与日志脱敏

- `HttpPolicyQueryClient` 请求头携带 `X-Trace-Id`（取自 `S2TraceContext`）与 `X-Request-Id`。
- `PolicyAssistantService` 记录结构化摘要日志：`traceId`、`requestId`、`instanceId`、`sourceMode`、`fallbackReason`、`citationCount`、`registryVerified`、`elapsedMs`。
- **日志安全红线**：日志中严禁打印 `policy.python.auth-token`、浏览器 Bearer JWT、完整问题请求体或原始模型回答文本。

---

## 九、验收状态台账

- **Python 政策问答 Java 适配、可信引用核验、本地降级与 H2/Mock/HTTP Stub 测试**：已完成本地自动化验证。
- **真实 Python 服务跨组联调**：`NOT_VERIFIED`（当前按 S1 E.1/E.2 PROPOSED v1.0 契约实现，Mock 及合成来源测试不等于真实 Python 政策核验验收）。
- **RabbitMQ 真实 Broker 联调**：`NOT_VERIFIED`。
- **前端联调**：`NOT_VERIFIED`（本轮未修改 `frontend/` 下任何文件）。
- **真实 MySQL 执行验证**：`NOT_VERIFIED`（S4-C 无新增数据库表或 Flyway 迁移，全部测试在隔离 H2 环境运行）。
- **历史审计项状态**：
  - 审计 `#3`（长事务）：保持 `OPEN`；
  - 审计 `#33`（区划层级规则）：保持 `OPEN`；
  - 审计 `#21`（人员版本与输入精确校验）：保持 `FIXED_VERIFIED`。
