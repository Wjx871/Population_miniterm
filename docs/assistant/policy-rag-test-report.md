# 政策办理知识助手 RAG 测试报告（V1 & S4-C）

## 验收项

| 场景 | 预期 | 结果 |
| --- | --- | --- |
| 户籍迁入材料（本地检索与 Python 适配） | 命中迁入指南、完成 `[1]` 引用重编号并填入 `answerSummary` | 自动化覆盖 |
| 居住证到期与多轮追问 | 识别事项、改写独立问题并命中居住证指南 | 自动化覆盖 |
| 审批通过状态咨询 | 在有效回答基础上追加“审批通过不等于业务已办结”说明 | 自动化覆盖 |
| 完整身份证号 / 手机号 / 历史对话含敏感信息 | 安全拒绝（`SAFE_REFUSAL` / `JAVA_SENSITIVE_GUARD`），不发送 Python | 自动化覆盖 |
| 外部未注册政策来源 | 即使 Python 自报 `SUFFICIENT`，仍保持保守等级 `PARTIAL`（`needHumanHelp=true`） | 自动化覆盖 |
| 已注册来源元数据篡改 / 过期 / 区域不匹配 / 意图过滤失效 | 丢弃 Python 回答与版本标记，整体一致回退 `JAVA_LOCAL_FALLBACK` | 自动化覆盖 |
| HTTP 超时 / 401 / 403 / 429 / 5xx / 重定向 / 超 64KB 流式响应 | 网络读取期间阻断超大响应，回退纯 Java 本地检索且不调用 legacy LLM | 自动化覆盖 |
| Python 与本地检索均故障 | 返回安全 `SERVICE_ERROR`（HTTP 200 封装），不抛 500 | 自动化覆盖 |
| 权限过滤 `nextActions` 与 `check-materials` | 按当前用户权限白名单过滤动作，不采信 Python 伪造动作或主键 | 自动化覆盖 |

## 测试类与外部依赖状态

- `PolicyAssistantServiceTest`、`PolicyWorkflowServiceTest`：覆盖内置知识库加载、逻辑路径脱敏、敏感拒绝与受控清单生成。
- `PolicyPythonAdapterIntegrationTest`：覆盖 S4-C 成功路径、可信来源注册表与保守证据等级、10 类异常引用、意图过滤后整体一致降级、双故障 `SERVICE_ERROR`、多轮历史敏感拦截、查询扩展单次远程预算及并发 `traceId`/`requestId` 隔离。
- `PolicyPythonHttpStubIntegrationTest`：使用本地隔离 `HttpServer` 覆盖真实 HTTP 传输、服务凭据校验、生产传输安全边界、64 KB 网络流式有界读取、连接异常关闭、超时、重定向拒绝与 legacy AI 隔离。
- **外部验证状态**：真实 Python 服务联调 `NOT_VERIFIED`（本轮 Mock 与本地 HTTP Stub 测试不代表真实 Python RAG 服务已上线验收）。
