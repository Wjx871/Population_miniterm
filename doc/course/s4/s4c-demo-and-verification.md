# S4-C 演示与验证记录

## 一、当前交付状态与基线

- **施工基线**：`integration/20261101-backend-p0-security`（SHA `f14f8dfe43d724a6edd9c05ea24d3e8a53eb2842`）。
- **当前施工分支**：`feat/20261105-s4c-policy-python-adapter`（独立 worktree 隔离施工，等待独立复核）。
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

1. **默认关闭与纯本地检索 (`JAVA_LOCAL_ONLY`)**：
   - 在默认配置（`policy.python.enabled=false`）下，以具备 `population:view` 权限的 `household` 用户请求 `POST /api/assistant/policy/query`。
   - 验证返回 HTTP 200，`sourceMode="JAVA_LOCAL_ONLY"`，`fallbackUsed=false`，`knowledgeBaseVersion="LOCAL_MD_V1"`，原有 `answerSummary`、`handlingSteps`、`requiredMaterials`、`citations` 等字段完整保留。
2. **Python 混合检索成功与可信来源注册表核验 (`PYTHON_HYBRID_RAG`)**：
   - 启用 `policy.python.enabled=true`，返回命中内置可信政策注册表（如 `policy-knowledge/01-migration-in-guide.md`）且元数据一致的响应。
   - 验证 `PolicyPythonResponseValidator` 完成双向引用核验并将 `[[policy-knowledge/01-migration-in-guide.md]]` 净化为可读章节引用，`PolicyTaskAgentService` 优先采用该回答作为 `answerSummary`，`sourceMode="PYTHON_HYBRID_RAG"`，`evidenceStatus="SUFFICIENT"`，`needHumanHelp=false`。
3. **未注册外部来源的保守证据等级约束**：
   - 当 Python 返回未在 Java 内置知识库注册的合成外部来源（如 `ext-provincial-2026-01`）且自报 `evidenceLevel="SUFFICIENT"` 时，验证 Java 强制将 `evidenceStatus` 限制为 `"PARTIAL"` 且 `needHumanHelp=true`。
4. **融合阶段意图不匹配的整体一致性降级 (`JAVA_LOCAL_FALLBACK`)**：
   - 提问迁入材料，而 Python 返回与迁入无关的死亡注销政策引用；在 `PolicyTaskAgentService.fusePythonCitations` 过滤后引用为空。
   - 验证 Java 丢弃 Python 回答、丢弃 Python 引用、清除 `modelVersion`/`retrievalVersion`/`rerankerVersion`、重新计算本地引用与 `evidenceStatus`，并将 `mode` 置为 `"RETRIEVAL_ONLY"`、`sourceMode` 置为 `"JAVA_LOCAL_FALLBACK"`、`fallbackReason="PYTHON_CITATION_INVALID"`。
5. **网络层 64 KB 有界流式读取与传输安全边界**：
   - 本地 `HttpServer` Stub 流式输出超过 64 KB（如 80 KB）响应体时，`HttpPolicyQueryClient` 在 `InputStream` 读取期间立即中断并关闭流，返回 `fallbackReason="PYTHON_RESPONSE_TOO_LARGE"`。
   - 生产 profile 或关闭 `allow-insecure-local-http` 时拒绝明文 `http://`（`PYTHON_INSECURE_TRANSPORT_REJECTED`）；缺失服务令牌时拒绝远程调用（`PYTHON_AUTH_TOKEN_MISSING`）；验证浏览器 Bearer JWT 从未转发至 Python 端。

---

## 三、自动化测试与回归证据

### 1. S4-C 专项测试结果（28 项通过，0 失败）
执行命令：
```powershell
.\mvnw.cmd test "-Dtest=PolicyPythonAdapterIntegrationTest,PolicyPythonHttpStubIntegrationTest,PolicyAssistantServiceTest,PolicyWorkflowServiceTest"
```
- `PolicyPythonAdapterIntegrationTest`：**19 项通过，0 失败**
  - 覆盖默认关闭、可信注册表核验通过、合成外部来源保守降级 `PARTIAL`、已注册来源元数据篡改拦截、10 类异常引用与协议错误降级、融合阶段意图不匹配整体一致性降级、查询扩展最多 1 次 Python 调用预算、当前问题与多轮历史敏感信息拦截（身份证/手机号）、Python 与本地检索双故障 `SERVICE_ERROR` 兜底、`nextActions` 权限过滤与 `check-materials` 兼容、未授权 401 / 无 `population:view` 403、并发 8 线程 `traceId`/`requestId` 隔离。
- `PolicyPythonHttpStubIntegrationTest`：**6 项通过，0 失败**
  - 使用本地 `HttpServer` 覆盖 HTTP 200 正常交互与请求头核验（确认不转发浏览器 JWT、仅发送服务 Token 与 `X-Trace-Id`、仅传递服务端可信 `regionCode`）、HTTP 401/403/429/5xx、302 重定向拒绝、64 KB 网络流式有界读取拦截、响应流半途断开、请求超时、连接拒绝（且验证启用 legacy `policy.ai.enabled=true` 时降级路径绝不触发公网 LLM）、生产环境 `http://` 拒绝与空凭据拒绝。
- `PolicyAssistantServiceTest`：**2 项通过，0 失败**（内置 19 份政策文档 56 个分块加载、逻辑路径脱敏、身份证敏感拦截）。
- `PolicyWorkflowServiceTest`：**1 项通过，0 失败**（工作流意图与受控材料清单生成）。

### 2. 全量 Maven 回归测试结果（592 项通过，0 失败）
执行命令：
```powershell
.\mvnw.cmd test
```
- **总计**：**592 项通过，0 失败，0 错误，0 跳过**（`BUILD SUCCESS`，耗时 2 分 21 秒）。
- **各阶段完整回归数量独立记录**：
  - S3 阶段最终报告：527 项；
  - S4-A 阶段完整回归：545 项；
  - S4-B 阶段完整回归：567 项；
  - **本轮 S4-C 阶段完整回归：592 项**（在 S4-B 567 项基础上新增 S4-C 专项测试 25 项，既有 S2 RMI/SOAP、S3 异步预审、S4-A 候选解析、S4-B 人工确认与材料绑定、人口/户籍/迁入/迁出/注销/统计/安全测试全部通过）。
