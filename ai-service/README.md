# 邑循轻量 RAG 服务 V0.3

当前使用 **DeepSeek 官方 API**。本机禁止下载或运行模型，运行方式见 [远程部署说明](../docs/assistant/deepseek-deployment.md)。

```text
问题与用户历史 → 敏感/地区/日期/来源检查
→ DeepSeek 受约束的同义检索词改写
→ 词法多路召回与名次融合 → 模型选择原文证据
→ JSON、引用、逐字原文、完整句边界校验 → 回答或明确拒答
```

服务已部署到独立的 RAG 服务器，Ubuntu 24.04.4 LTS，单进程、256MiB 内存上限，只监听 `127.0.0.1:8091`。用户已配置服务器端模型 Key，健康状态为 UP；本机 Java 经受限 SSH 隧道 `127.0.0.1:18091` 调用，前端不持有内部密钥。心灵树洞主机保持不动。

当前不是向量检索版本，不把 DeepSeek 当作 Embedding 或 CrossEncoder 服务。73 项离线/Mock 检查已通过；另有真实 DeepSeek 40 题诊断、Java 20 题回归和页面正向/无依据场景验收，见 [原始结果与限制](../docs/assistant/deepseek-rag-validation-report.md)。OCR 接口仍返回 503。

部署、密钥隐藏输入和验收步骤见 [DeepSeek 部署说明](../docs/assistant/deepseek-deployment.md)。不要将 Key 发到聊天或腾讯云命令正文中。

当前基础依赖不含 Torch、Transformers、NumPy 或模型权重：

```bash
uv sync --locked --python 3.12
.venv/bin/python -m pytest -q
```

AI 服务目录下的本机 `.env.local` 保持生成关闭，不启动本地推理；项目根目录 `.env.local` 已启用 Java 的远程 RAG 适配。服务器配置由 systemd 的 `/etc/yixun-ai/service.env` 和私密 `provider.env` 管理；示例见 `.env.deepseek.example`。本机测试环境约 27MB，未恢复旧推理环境。项目根目录运行 `bash scripts/macos/start.sh` 会复用或建立已授权隧道，再启动业务前后端；停止用 `bash scripts/macos/stop.sh`。

之前的混合检索消融结果和 20 题 Java 原始基线保留，见 [评测](../evaluation/README.md)、[V0.2 阶段记录](../docs/assistant/rag-v2-report.md)。22/22 为历史模型实验的文档命中，不是当前 DeepSeek 答案准确率。

旧模型版本清单仅为历史记录。模型、索引与推理环境已清理；不要在本机运行模型准备、加载或消融脚本，也不要恢复旧模型依赖锁。来源检查与证据校验仅降低风险，不能把非正式指南转成法定政策依据。

### 青岛正式政策与可选远程检索

青岛正式条款由 Python RAG 按实际办理地、时间与来源类型过滤。查询命中的条款带文号、地区和链接。原文期限记录仅用于解释时效。默认部署继续使用词法检索及现有 DeepSeek 证据选取。

显式选用支持文本 `/embeddings` 和 `/rerank` 的 HTTPS 服务后，可使用以下远程配置。模型和地址取服务商实际配置。密钥单独保存在远程服务器的私有配置中。

| 配置 | 用途 |
| --- | --- |
| `YIXUN_AI_RETRIEVAL=remote` | 服务端启用远程向量检索和词法名次融合 |
| `YIXUN_AI_RETRIEVAL_URL` | 远程 HTTPS API 基址，含 API 版本路径 |
| `YIXUN_AI_RETRIEVAL_KEY` | 对应服务商的专用密钥 |
| `YIXUN_AI_EMBEDDING_MODEL` | 文本向量模型 |
| `YIXUN_AI_RERANK=true`、`YIXUN_AI_RERANK_MODEL` | 启用远程重排并指定模型 |

远程检索只索引通过适用性筛选的公开政策条款，查询和候选排序会向服务商发送当次问句。接入验收仅运行冻结的公开模拟问句，运行环境无需本地模型。Embedding 失败时回退到词法检索，重排失败时保留融合顺序。检查结果中的实际检索引擎、警告和阶段耗时判断是否使用了远程接口。

在仓库根目录，使用上表的地址、密钥和两个模型的环境变量运行以下命令，对照同一知识库的词法、混合检索和重排。评测不会改动现有远程实例或业务数据。

```bash
ai-service/.venv/bin/python evaluation/compare_qingdao_retrieval.py --remote \
  --output tmp/qingdao-remote-comparison.json
```

[Embedding API 契约示例](https://docs.siliconflow.cn/docs/api/embeddings-post) · [Rerank API 契约示例](https://docs.siliconflow.cn/docs/api/rerank-post) · [青岛六题结果](../evaluation/qingdao-pilot-findings.md)
