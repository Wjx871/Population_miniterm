# 邑循 RAG V0.2 契约补充

> 当前运行版本为 V0.3 DeepSeek 轻量服务，见 [部署说明](deepseek-deployment.md)。新增 `retrievalQueries` 与 `MODEL_ABSTENTION`，保留原字段兼容；本文其余部分为历史设计记录。

> 本文记录 V0.2 历史设计；本地模型方案已终止，模型和推理环境已删除。V0.3 已通过远程 DeepSeek 接入 Java 与页面，真实验收见 [当前报告](deepseek-rag-validation-report.md)。用户禁止本机模型下载/推理，下文涉及本地模型的流程不得作为当前部署步骤。

日期：2026-11-01。此文档补充 [V0.1 契约](ai-internal-contract-v1.md)，当前机器契约以 AI 服务 `/openapi.json` 为准。

## 调用链与信任边界

浏览器 → 原 Spring Boot `/api/assistant/policy/query` → 可配置 `PolicyRagService` → Python `/internal/policy-query` → 本地语义模型与 llama.cpp。

前端只使用现有用户登录态。内部服务密钥由 Java 注入，不传给浏览器。Java 原 Agent 继续计算意图和按当前用户权限过滤的下一步入口；RAG 不查询个人主档、不执行 SQL、不批准申请、不写业务数据。模型只从授权知识目录的候选资料中摘取原文，不能发起工具调用。调用失败时返回明确不可用提示，不用旧的不受地区/时间约束的答案掩盖故障。

## Python 请求

`POST /internal/policy-query`，请求头 `X-AI-Service-Key`。

```json
{
  "question": "那如果材料不全呢？",
  "topK": 4,
  "history": [{"role": "user", "content": "我要办理户籍迁入"}],
  "regionCode": "370200"
}
```

| 字段 | 约束与语义 |
| --- | --- |
| question | 去除首尾空白后 2–500 字符 |
| topK | 整数 1–8，默认 4，禁止隐式字符串/布尔转换 |
| history | 可选，最多 6 条 user/assistant；每条 1–500 字符。追问只参考历史 user，助手文字不作为指令/事实 |
| regionCode | 可选，六位地区码。支持全国、省、市到下级的适用关系，不做完整地名消歧 |
| asOf | 可选，ISO 日期。与问句明确日期冲突时要求澄清；无日期来源不能支持历史版本查询 |

Java 公开入口目前仍只接收 `question/history`，没有新增地区/日期 UI。问句明确要求地方/最新/法定政策时，通用项目指南不能用于确认；没有匹配的正式来源则返回依据不足。

## Python 响应

保留 `schemaVersion: "1.0"` 的既有字段，新增兼容字段；V0.1 客户端需要接受新增模式 `AI_ASSISTED`。

| 字段 | 取值/含义 |
| --- | --- |
| mode | `AI_ASSISTED` / `RETRIEVAL_ONLY` / `SAFE_REFUSAL` |
| generationMode | `VERIFIED_EXTRACTIVE`：真实模型选文通过校验；`EXTRACTIVE_FALLBACK`：生成失败；`DISABLED`：未启用/未执行生成 |
| evidenceStatus | `CANDIDATES_ONLY` / `INSUFFICIENT` / `REFUSED`，不把检索得分当作政策正确概率 |
| standaloneQuestion | 经有限规则补全的查询，不是任意对话推理 |
| reasonCode | 无依据、日期冲突、越界等原因；正常候选回答可为 null |
| modelVersions | 语义/重排模型的仓库与固定 revision；生成服务使用配置的模型别名，精确文件见 model-manifest.json |
| stageTimingsMs | 检索、重排、生成各阶段耗时 |
| citations | 原有引用字段加 chunkId、semanticScore、rerankScore、sourceUrl、regionCodes |

`retrievalScore` 是 RRF 排名融合分，`semanticScore` 是向量相似度，`rerankScore` 是跨编码器原始输出，均不是答案正确概率。

真实模型返回 JSON 选文，服务要求引用编号存在、摘文逐字出现在对应片段中，且保留完整句子边界。不能用句内片段删掉“不等于”等前置限制。校验失败时展示检索原文并记录具体降级原因。该设计限制自由编造，但仍需人工检查选文是否回答问题、是否覆盖全部条件。

## Java 兼容映射

Python `answer` → `AgentResponse.answerSummary`；引用映射到原 `Citation`。保留原意图与已按权限过滤的动作，敏感拒绝清空动作。旧规则提取的条件/材料/流程列表不再混入 RAG 回答，避免互相矛盾。

Python `CANDIDATES_ONLY` → Java `PARTIAL`；其他无依据/拒绝 → Java `INSUFFICIENT`，保留 `SAFE_REFUSAL` 模式。`needHumanHelp` 为 true，旧 `confidence` 固定为 0.0，不能据此显示“正确率”。Java 兼容引用暂不带完整模型分数与日期字段，完整诊断信息保存在内部响应和评测文件中。

配置 `POLICY_RAG_ENABLED=true` 才启用（代码默认 false）。Java 读取超时 65 秒；浏览器仅问答请求为 75 秒；Python 生成默认 45 秒。忙时返回 `503 RAG_BUSY`，Java 展示服务暂不可用。小内存模式限单次模型推理，不承诺多人并发。

## 健康与错误

- `/health` 无需内部密钥。模型配置存在不代表可用；生成 `/models` 无对应模型时报告 `DEGRADED`、generation=false。
- `/internal/*` 密钥错误为 401；未知/非法字段为 422；忙/尚未实现的识别为 503。
- `/internal/document-analysis` 仍明确为 503，不把字段样例当作 OCR 结果。
- 知识库限定 Markdown 目录，严格解析日期。官方来源需明确标记 `OFFICIAL_POLICY`、HTTPS 来源和生效日期；这属于维护者录入约束，不自动证明站点或政策权威性。

部署、依赖、模型校验与复跑命令见 [AI 服务说明](../../ai-service/README.md)。
