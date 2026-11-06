# 政策办理知识助手 RAG 设计（V1 & S4-C Python 内部契约适配）

## 范围与边界

助手只提供系统内置业务指南和受控功能入口，不读取人口敏感信息、不修改业务数据、不创建业务申请、不执行审批或迁入。所有内置文档均标注 `PROJECT_GUIDE` 或 `SYSTEM_GUIDE`；页面提示正式办理要求以主管部门最新规定为准。

S1/S4-C 定义的 `POST /internal/policy-query` 契约当前处于 **PROPOSED（v1.0）** 状态，尚未完成全组冻结与真实 Python 服务联调（`NOT_VERIFIED`）。Java 侧提供完整的可替换客户端适配（`MOCK` / `HTTP`）、可信引用与来源注册表核验、回答编排与本地降级。

## 处理流程

1. **安全与敏感拦截**：`POST /api/assistant/policy/query` 要求 `population:view` 权限。`PolicyTaskAgentService` 先对当前问题、最近最多 6 轮 `history` 以及追问改写后的 `standaloneQuestion` 执行敏感输入审查（身份证号、手机号、Token/密钥、SQL、修改人口或绕过审批指令）。命中后立即返回 `mode=SAFE_REFUSAL`、`sourceMode=JAVA_SENSITIVE_GUARD`，绝不将敏感内容发送至 Python。
2. **远程调用预算与双路径检索**：
   - 保持既有意图识别、追问改写和查询扩展（1～3 条）。
   - 单次用户请求最多仅由主查询（Query 0）发起 1 次远程 Python 调用；扩展查询固定走确定性 Java 本地检索，防止串行远程超时叠加。
   - **当 `policy.python.enabled=true` 时**：主查询通过 `PolicyQueryClient`（`HttpPolicyQueryClient` 或 `MockPolicyQueryClient`）请求 `/internal/policy-query`。HTTP 客户端强制服务端可信配置 URL、禁止重定向（`Redirect.NEVER`）、要求独立服务凭据（绝不转发浏览器 JWT）、生产环境拒绝明文 HTTP，并在网络流读取期间执行 **64 KB 有界读取**。
   - **可信引用与来源真实性核验**：`PolicyPythonResponseValidator` 校验 `schemaVersion=1.0`、`requestId`/`traceId`、`outcome`、`citationIdsUsed` 与 `citations.sourceId` 唯一性及 `[[sourceId]]` 一致性、`category` 白名单、`sourceVersion`、生效期/过期时间、区域 `regionCode` 匹配及内容安全。对已收录于 Java 本地知识库的 `sourceId`，按可信来源注册表核对标题、分类、来源类型、版本号和章节；篡改元数据的引用一律拒绝。对未在本地注册表登记的外部来源，即使结构校验通过、Python 自报 `SUFFICIENT`，Java 仍强制保持保守证据等级 `PARTIAL`（`needHumanHelp=true`）。
   - **编排与整体一致降级**：`PolicyTaskAgentService` 对引用执行意图过滤与重新编号（`[[sourceId]]` → `[1]`），将通过全部校验的 Python 回答作为 `AgentResponse.answerSummary` 主体（状态类提问可追加确定性业务状态说明）。若任何引用在融合或意图过滤阶段失效，Java 丢弃 Python 回答、丢弃失效引用、清除全部 Python 版本标记，并回退至 Java 本地检索重算引用、`evidenceStatus`、`mode=RETRIEVAL_ONLY`、`sourceMode=JAVA_LOCAL_FALLBACK`（故障回退绝不调用 legacy 外部 LLM）。
   - **当 `policy.python.enabled=false`（默认）时**：使用内置 `policy-knowledge/*.md` 的中文字符二元组 BM25 检索；若显式配置 `policy.ai.enabled=true` 且调用成功，标记 `sourceMode=JAVA_LEGACY_AI_ASSISTED`。
   - **全路径故障兜底**：若 Python 与本地检索均异常，返回 `mode=SERVICE_ERROR`、`sourceMode=SERVICE_ERROR`、`evidenceStatus=INSUFFICIENT`，不抛出未处理 500。

## 前端兼容性

路由为 `/assistant/policy`，沿用 `population:view` 权限。前端无需修改即可继续消费 `answerSummary`、`conditions`、`requiredMaterials`、`steps`、`warnings`、`citations`、`nextActions`、`evidenceStatus`、`needHumanHelp`、`executionTrace`、`mode`、`confidence`、`traceId` 等既有字段，同时响应新增可选字段 `sourceMode`、`knowledgeBaseVersion`、`modelVersion`、`retrievalVersion`、`rerankerVersion`、`elapsedMs`。
