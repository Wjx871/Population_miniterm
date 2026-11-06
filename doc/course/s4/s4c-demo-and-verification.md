# S4-C 演示与验证记录

## 一、当前交付状态与基线

- **施工基线**：`integration/20261101-backend-p0-security`（SHA `f14f8dfe43d724a6edd9c05ea24d3e8a53eb2842`）。
- **第一轮提交基线**：`c34608b4ec99bc3354ab2872428d03bd2f76cee3`。
- **当前施工分支**：`feat/20261105-s4c-policy-python-adapter`（独立 worktree 隔离施工，已完成第一轮独立审查后定向加固，等待第二轮独立复核）。
- **外部与跨组联调状态**：
  - Python 真实服务联调：`NOT_VERIFIED`（当前基于 S1 E.1/E.2 PROPOSED v1.0 契约完成 Java 适配、Mock 与本地 HTTP Stub 测试，不代表真实 Python 混合检索服务已完成联调验收）。
  - RabbitMQ 真实联调：`NOT_VERIFIED`。
  - 前端联调：`NOT_VERIFIED`（未改动 `frontend/` 下任何文件）。
  - 真实 MySQL 执行验证：`NOT_VERIFIED`（全部测试运行于隔离 H2 环境，不访问团队共享 MySQL）。
- **历史审计项台账**：
  - 审计 `#3`（长事务）：保持 `OPEN`；
  - 审计 `#33`（区划层级规则）：保持 `OPEN`；
  - 审计 `#21`（人员版本与输入精确校验）：保持 `FIXED_VERIFIED`。

---

## 二、可复现演示与验证路径（H2 / Mock / 本地 HTTP Stub）

以下演示仅使用 H2 内存数据库、确定性 `MockPolicyQueryClient` 或本地隔离 `com.sun.net.httpserver.HttpServer` Stub，不访问公网 LLM，也不代表真实 Python 政策核验：

1. **默认关闭与纯本地检索 (`JAVA_LOCAL_FALLBACK` / `RETRIEVAL_ONLY`)**：
   - 在默认配置（`policy.python.enabled=false`）下，以具备 `population:view` 权限的 `household` 用户请求 `POST /api/assistant/policy/query`。
   - 验证返回 HTTP 200，`mode="RETRIEVAL_ONLY"`，`sourceMode="JAVA_LOCAL_FALLBACK"`，`knowledgeBaseVersion="LOCAL_MD_V1"`，原有 `answerSummary`、`conditions`、`requiredMaterials`、`steps`、`citations` 等字段完整保留。
2. **Python 混合检索成功、可信来源注册表与章节正文摘录核验 (`PYTHON_HYBRID_RAG`)**：
   - 启用 `policy.python.enabled=true`，返回命中内置可信政策注册表（如 `migration-in-guide`）且标题、分类、来源类型、版本、章节名及**章节正文摘录 (`excerpt`)** 完全可追溯的响应。
   - 验证 `PolicyPythonResponseValidator` 完成双向引用核验与章节正文可追溯性核对，将 `[[migration-in-guide]]` 重写为 `[1]`，`PolicyTaskAgentService` 优先采用该回答作为 `answerSummary`，`sourceMode="PYTHON_HYBRID_RAG"`，`evidenceStatus="SUFFICIENT"`，`needHumanHelp=false`。
   - 验证当注册文档真实存在但摘录被篡改、摘录来自同一文档的其他章节、或 `sourceId` 使用合法前缀加伪造后缀（如 `migration-in-guide-forged`、`.v2`、`_extra`）时，均被拒绝并整体一致回退 `JAVA_LOCAL_FALLBACK`。
3. **未注册外部来源的保守证据等级与强制材料清单隔离**：
   - 当 Python 返回未在 Java 内置知识库注册的外部来源（如 `ext-migration-checklist-2026`）且自报 `evidenceLevel="SUFFICIENT"`、甚至包含伪造强制材料要求时：
     - 验证 Java 强制将 `evidenceStatus` 限制为 `"PARTIAL"` 且 `needHumanHelp=true`；
     - 验证未核验外部摘录绝不进入 `AgentResponse.requiredMaterials()`，也绝不进入 `PolicyWorkflowService.generateChecklist()` 的“材料核对”或“办理流程”强制清单。
4. **融合阶段意图不匹配的整体一致性降级 (`JAVA_LOCAL_FALLBACK`)**：
   - 提问迁入材料，而 Python 返回与迁入无关的注销政策引用；在 `PolicyTaskAgentService.fusePythonCitations` 过滤后引用缺失。
   - 验证 Java 丢弃 Python 回答、丢弃 Python 引用、清除 `modelVersion`/`retrievalVersion`/`rerankerVersion`、重新计算本地引用与 `evidenceStatus`，并将 `mode` 置为 `"RETRIEVAL_ONLY"`、`sourceMode` 置为 `"JAVA_LOCAL_FALLBACK"`。
5. **真实端到端 HTTP 截止时间、流式取消与 64 KB 有界读取**：
   - 本地 `HttpServer` Stub 在超时前返回响应头但响应体只发送少量字节后停顿，或以慢速分块（trickle chunks）持续发送字节时，`HttpPolicyQueryClient` 通过共享统一 `deadlineNanos` 的 `sendAsync` + `BoundedBodySubscriber` 在整体预算内主动取消 `Flow.Subscription` 与请求并降级，且后续正常快速请求立即恢复成功。
   - 本地 `HttpServer` Stub 流式输出超过 64 KB 响应体时，在网络接收 `ByteBuffer` 期间立即取消订阅并降级为 `PYTHON_RESPONSE_TOO_LARGE`。
   - 生产 profile 或关闭 `allow-insecure-local-http` 时拒绝明文 `http://`（`PYTHON_INSECURE_TRANSPORT_REJECTED`）；缺失服务令牌时拒绝远程调用（`PYTHON_AUTH_TOKEN_MISSING`）；浏览器 Bearer JWT 从未转发至 Python 端。
6. **Legacy LLM 回答与来源标记一致性**：
   - 当 `policy.python.enabled=false` 且显式启用 `policy.ai.enabled=true` 时，仅当 Legacy LLM 返回通过安全校验且包含合法 `[1..N]` 引用的回答时，才将其重映射后作为 `answerSummary` 展示并标记 `mode="AI_ASSISTED"`、`sourceMode="JAVA_LEGACY_AI_ASSISTED"`；
   - 若 Legacy LLM 返回越界引用（如 `[99]`）或越权指令，回答被拒绝并采用 Java 模板构造 `answerSummary`，同时严格标记 `mode="RETRIEVAL_ONLY"`、`sourceMode="JAVA_LOCAL_FALLBACK"`、`modelVersion=null`。

---

## 三、自动化测试与回归证据

### 1. S4-C 专项测试结果（30 项通过，0 失败）
执行命令：
```powershell
.\mvnw.cmd test "-Dtest=PolicyPythonAdapterIntegrationTest,PolicyPythonHttpStubIntegrationTest,PolicyAssistantServiceTest,PolicyWorkflowServiceTest"
```
- `PolicyPythonAdapterIntegrationTest`：**20 项通过，0 失败**
  - 覆盖默认关闭、可信注册表与章节正文摘录核验通过（含确定性空白/标点规范化）、注册文档摘录篡改拦截、跨章节摘录拦截、`sourceId` 合法前缀加伪造后缀拦截、标题/版本篡改拦截、外部未注册来源保持 `PARTIAL` 且伪造材料要求不进入 `requiredMaterials` 与 `PolicyWorkflowService` 强制清单、异常引用与协议错误降级、融合阶段意图不匹配整体一致性降级、查询扩展最多 1 次 Python 调用预算、当前问题与多轮历史敏感信息拦截、Python 与本地检索双故障 `SERVICE_ERROR` 兜底、`nextActions` 权限过滤与 `check-materials` 兼容、未授权 401 / 无 `population:view` 403、并发 12 任务 `traceId`/`requestId` 隔离。
- `PolicyPythonHttpStubIntegrationTest`：**7 项通过，0 失败**
  - 使用本地 `HttpServer` 覆盖 HTTP 200 正常交互与请求头核验、端到端统一 deadline（响应头超时、响应头返回后响应体停顿、慢速分块流持续发送不延长 deadline、超时取消后客户端立即恢复正常请求）、64 KB 网络流式有界读取拦截、响应流半途断开、HTTP 401/403/429/5xx、302 重定向拒绝、连接拒绝、生产环境 `http://` 拒绝与空凭据拒绝、Legacy AI 回答文本与 `mode`/`sourceMode` 严格一致性。
- `PolicyAssistantServiceTest`：**2 项通过，0 失败**（内置 19 份政策文档 56 个分块加载、逻辑路径脱敏、身份证敏感拦截）。
- `PolicyWorkflowServiceTest`：**1 项通过，0 失败**（工作流意图与受控材料清单生成）。

### 2. 全量 Maven 回归测试结果（594 项通过，0 失败）
执行命令：
```powershell
.\mvnw.cmd test
```
- **总计**：**594 项通过，0 失败，0 错误，0 跳过**（`BUILD SUCCESS`）。
- **各阶段完整回归数量独立记录**：
  - S3 阶段最终报告：527 项；
  - S4-A 阶段完整回归：545 项；
  - S4-B 阶段完整回归：567 项；
  - S4-C 首轮完整回归：592 项；
  - **本轮 S4-C 第一轮独立审查加固后完整回归：594 项**（既有 S2 RMI/SOAP、S3 异步预审、S4-A 候选解析、S4-B 人工确认与材料绑定、人口/户籍/迁入/迁出/注销/统计/安全测试全部通过）。
