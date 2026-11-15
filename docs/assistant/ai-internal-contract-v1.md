# 邑循内部 AI 接口契约 v1

> 此文档记录 V0.1 词法基线，保留用于对照。V0.2 已增加 history、地区/日期过滤、混合检索、重排序与生成模式，并完成 Java 只读接入；当前行为以 [V0.2 契约补充](ai-rag-v2.md)、[运行说明](../../ai-service/README.md) 和 `/openapi.json` 为准。

状态：**本地实现与交接草案**，尚未经其他三名成员共同冻结；不覆盖现有公开 API。
维护人：王家兴。当前服务 `0.1.0`，schemaVersion 为 `1.0`，默认地址 `http://127.0.0.1:8091`。

## 1. 现有入口与新增入口的关系

现有 Java：`POST /api/assistant/policy/query`，请求 `{question, history}`，返回 `ApiResponse<AgentResponse>`，不是旧文档中的单纯 QueryResponse。
AgentResponse 实际包含 intent、standaloneQuestion、answerSummary、conditions、requiredMaterials、steps、warnings、citations、nextActions、evidenceStatus、needHumanHelp、executionTrace、mode、confidence、traceId。

新增 Python：`POST /internal/policy-query`，不套 Java 的 ApiResponse，不接收用户角色、任意工具、SQL、文件路径或 URL。
Java 后续负责会话改写、身份与权限、材料授权、异步任务和草稿；本服务只提供检索结果。
现有前端继续消费 Java，不直接携带内部密钥调用 Python。

注意：Java 的 confidence 来自启发式分数，不等于正确概率；新增响应不沿用此字段。

## 2. 鉴权与追踪

- 内部 POST 请求使用 `X-AI-Service-Key`，密钥通过环境变量注入；缺失/错误返回 401。
- 当前每次请求由 Python 新建 UUID，响应正文和 `X-Trace-ID` 一致。Java 调用方记录该子调用 ID，与自身 traceId/taskId 建立关联。
- `/health` 与本地开发文档不需要密钥，默认仅监听 loopback；容器部署时由赵书亚决定内部网络和入口约束。
- 结构化请求日志只含路径模板、方法、状态、耗时、traceId，不记录问题正文或证件内容。

## 3. 政策检索

请求：

```json
{"question":"办理户籍迁入需要什么材料？","topK":4}
```

| 字段 | 约束 |
| --- | --- |
| question | 去除首尾空白后 2—500 字符的字符串；Java 如需上下文，先改写为独立问题 |
| topK | 可选，严格整数 1—8，默认 4 |
| 其他字段 | 拒绝，避免客户端误以为 region/asOf 等过滤已经生效 |

响应结构示例（UUID、摘要和分数以实际响应为准）：

```json
{
  "schemaVersion":"1.0",
  "traceId":"<server-generated-uuid>",
  "mode":"RETRIEVAL_ONLY",
  "evidenceStatus":"CANDIDATES_ONLY",
  "answer":"[1] 指南 / 章节：检索摘要……",
  "citations":[{
    "index":1,"logicalPath":"migration-in-guide","section":"材料与信息",
    "title":"户籍迁入指南","category":"迁入","sourceType":"PROJECT_GUIDE",
    "sourceVersion":"V1","effectiveDate":null,"excerpt":"原文片段……","retrievalScore":12.3
  }],
  "knowledgeVersion":"<sha256>",
  "retrievalEngine":"java-bigram-bm25-style-v1",
  "timingMs":1.2,
  "warnings":["LEXICAL_BASELINE_ONLY","PROJECT_OR_SYSTEM_GUIDES_NOT_OFFICIAL_POLICY","NO_REGION_OR_EFFECTIVE_DATE_FILTER"]
}
```

模式为 `RETRIEVAL_ONLY / SAFE_REFUSAL`；证据状态为 `CANDIDATES_ONLY / INSUFFICIENT / REFUSED`。
`CANDIDATES_ONLY` 只说明存在候选片段，不能映射成业务审核通过或政策充分适用。
源文档缺失的 effectiveDate 返回 null；已有日期原样透传，不声称它就是有效政策。
当前无语义检索、重排序、模型生成及地区/时间过滤，均需后续完善。

## 4. 文档预审预留入口

请求：

```json
{"taskId":"T001","businessType":"MIGRATION_IN","materialIds":[1,2,3]}
```

taskId 为 1—64 字符；materialIds 为 1—10 个唯一的严格正整数；业务类型暂仅迁入。
**目前合法请求返回 503 MODEL_NOT_CONFIGURED，不读取这些材料 ID。** 未配置模型不会返回成功或空 fields 来伪装完成。

正式输入流、后端签发的受控引用/授权、文件大小/类型限制、坐标归一化方式、OCR 模型与结果模式仍需张锦源、张蕊共同评审后实现。
未来字段建议包括 `materialId/page/field/value/bbox/score/source` 和 `conflicts/warnings`，但它们不是当前已实现响应。
现有 Java `/recognize/idcard` 适配与此预留接口不同，本轮没有声称兼容，不能直接修改 URL 就上线。

## 5. 错误约定

```json
{"schemaVersion":"1.0","traceId":"<uuid>","error":{"code":"MODEL_NOT_CONFIGURED","message":"文档识别模型尚未接入，未读取或处理材料。"}}
```

| HTTP | code | 含义 |
| --- | --- | --- |
| 401 | UNAUTHORIZED | 内部密钥缺失或不匹配 |
| 422 | INVALID_REQUEST | 字段、长度或类型错误，不回显原始输入 |
| 503 | MODEL_NOT_CONFIGURED | 文档识别尚未接入，调用方转人工处理或显示不可用 |

启动时知识库与密钥不可用会直接启动失败；调用方还须处理连接失败、超时和其他 HTTP 错误，不能假定所有异常都有 JSON。
建议 Java 超时覆盖一次检索的合理预算，并与现有 OCR 15 秒配置分开；本轮不擅自更改 Java 超时设置。

机器契约在运行服务的 `/openapi.json`；独立 Java 调用探针见 `evaluation/JavaAiServiceSmoke.java`。
