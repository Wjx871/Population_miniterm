# 政策办理知识助手 RAG 设计（V1 & S4-C Python 内部契约适配）

## 范围与边界

助手只提供系统内置业务指南和受控功能入口，不读取人口敏感信息、不修改业务数据、不创建业务申请、不执行审批或迁入。所有内置文档均标注 `PROJECT_GUIDE` 或 `SYSTEM_GUIDE`；页面提示正式办理要求以主管部门最新规定为准。

S1/S4-C 定义的 `POST /internal/policy-query` 契约当前处于 **PROPOSED（v1.0）** 状态，尚未完成全组冻结与真实 Python 服务联调（`NOT_VERIFIED`）。Java 侧提供完整的可替换客户端适配（`MOCK` / `HTTP`）、可信引用与来源正文注册表核验、回答编排与本地降级。

## 处理流程

1. **安全与敏感拦截**：`POST /api/assistant/policy/query` 要求 `population:view` 权限。`PolicyTaskAgentService` 先对当前问题、最近最多 6 轮 `history` 以及追问改写后的 `standaloneQuestion` 执行敏感输入审查（身份证号、手机号、Token/密钥、SQL、修改人口或绕过审批指令）。命中后立即返回 `mode=SAFE_REFUSAL`、`sourceMode=JAVA_SENSITIVE_GUARD`，绝不将敏感内容发送至 Python。
2. **远程调用预算与双路径检索**：
   - 保持既有意图识别、追问改写和查询扩展（1～3 条）。
   - 单次用户请求最多仅由主查询（Query 0）发起 1 次远程 Python 调用；扩展查询固定走确定性 Java 本地检索，防止串行远程超时叠加。
   - **当 `policy.python.enabled=true` 时**：主查询通过 `PolicyQueryClient`（`HttpPolicyQueryClient` 或 `MockPolicyQueryClient`）请求 `/internal/policy-query`。`HttpPolicyQueryClient` 采用共享统一端到端截止时间（`deadlineNanos`）的 `sendAsync` + 有界 `BoundedBodySubscriber`（`Flow.Subscription`），覆盖建立连接、等待响应头、接收完整响应体及网络停顿/慢速分块场景；超时或响应超过 **64 KB** 时在网络流式接收期间立即取消 `Flow.Subscription` 与 `CompletableFuture`，不遗留阻塞后台任务。
   - **可信引用与来源正文真实性核验**：`PolicyPythonResponseValidator` 校验 `schemaVersion=1.0`、`requestId`/`traceId`、`outcome`、`citationIdsUsed` 与 `citations.sourceId` 唯一性及 `[[sourceId]]` 一致性、`category` 白名单、`sourceVersion`、生效期/过期时间、区域 `regionCode` 匹配及内容安全。本地注册表保存每份受控指南的元数据及各章节正文（`sectionContents`）：
     - 严格按精确 `sourceId` 匹配注册来源，禁止任意 `sourceId` 伪造后缀（如 `-forged`、`.v2`、`_extra`）或冒用内部标题/`PROJECT_GUIDE`/`SYSTEM_GUIDE` 自动继承可信身份；
     - 对命中本地注册表的引用，除核对标题、分类、来源类型、版本号、章节名外，还按确定性规范化规则（去除空白符、统一中英文标点、最短有效长度 ≥ 8 字符）验证 `excerpt` 必须可追溯为对应章节真实正文的连续子串；跨章节摘录或篡改摘录一律拒绝并回退本地检索；
     - 对未在本地注册表登记的外部来源，保持保守证据等级 `PARTIAL`（`needHumanHelp=true`），且在 `PolicyTaskAgentService` 与 `PolicyWorkflowService` 中严禁将未核验外部摘录提升为强制材料要求（`requiredMaterials` 或 `checklist` 材料核对项）；
     - 明确边界：引用摘录通过本地来源核验仅证明所附引用可追溯至受控章节文本，不代表生成式回答中的每项推论均经过形式化语义证明。
   - **编排与整体一致降级**：`PolicyTaskAgentService` 对引用执行意图过滤与重新编号（`[[sourceId]]` → `[1]`），将通过全部校验的 Python 回答作为 `AgentResponse.answerSummary` 主体（状态类提问可追加确定性业务状态说明）。若任何引用在融合或意图过滤阶段失效，Java 丢弃 Python 回答、丢弃失效引用、清除全部 Python 版本标记，并回退至 Java 本地检索重算引用、`evidenceStatus`、`mode=RETRIEVAL_ONLY`、`sourceMode=JAVA_LOCAL_FALLBACK`（故障回退绝不调用 legacy 外部 LLM）。
   - **当 `policy.python.enabled=false`（默认）时**：使用内置 `policy-knowledge/*.md` 的中文字符二元组 BM25 检索；若显式配置 `policy.ai.enabled=true`，Legacy LLM 回答必须通过安全审查与 `[1..N]` 引用范围校验并在融合后重映射为最终 `answerSummary`，此时标记 `mode=AI_ASSISTED`、`sourceMode=JAVA_LEGACY_AI_ASSISTED`；若未调用或校验/映射未通过而使用 Java 模板摘要，则严格标记 `mode=RETRIEVAL_ONLY`、`sourceMode=JAVA_LOCAL_FALLBACK`。
   - **全路径故障兜底**：若 Python 与本地检索均异常，返回 `mode=SERVICE_ERROR`、`sourceMode=SERVICE_ERROR`、`evidenceStatus=INSUFFICIENT`，不抛出未处理 500。

## 前端兼容性

路由为 `/assistant/policy`，沿用 `population:view` 权限。前端无需修改即可继续消费 `answerSummary`、`conditions`、`requiredMaterials`、`steps`、`warnings`、`citations`、`nextActions`、`evidenceStatus`、`needHumanHelp`、`executionTrace`、`mode`、`confidence`、`traceId` 等既有字段，同时响应新增可选字段 `sourceMode`、`knowledgeBaseVersion`、`modelVersion`、`retrievalVersion`、`rerankerVersion`、`elapsedMs`。
