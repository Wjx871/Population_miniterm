# 王家兴的 AI 基线与 RAG 评测

## 内容

- `datasets/policy_questions.v1.jsonl`：20 条手工构造的开发诊断题，含 14 条正向题、3 条敏感/越界拒绝题、3 条依据不足题；Q03 带迁入历史用于追问。
- `datasets/material_cases.v1.json`：4 组虚构的**字段与规则契约样例**，分别是一致、姓名冲突、缺材料、待人工确认。
- `evaluate_policy.py`：通过真实 Java 公开接口登录、问答和退出，不写人口业务数据。
- `results/java-agent-baseline-v1.json`：第一轮不可覆盖的逐条结果与测试条件。
- `JavaAiServiceSmoke.java`：独立 Java 17 客户端，验证 Python 服务正常、未授权和模型缺失路径。

所有 `image_path` 当前均为 null，材料样例只是接口与规则冒烟设计，不是已经准备好的证件图片，也不用于 OCR 准确率评测。
每组要求哪些材料是该虚构测试场景的输入约定，不表示真实户籍迁入的统一法定清单。

## 复跑 Java 基线

确保既有 Java 服务已启动，在项目根目录设置本地测试密码；脚本不接受命令行密码，不保存登录 token：

```bash
read -s EVAL_PASSWORD
export EVAL_PASSWORD
python3 evaluation/evaluate_policy.py \
  --username population \
  --output evaluation/results/java-agent-recheck-01.json
unset EVAL_PASSWORD
```

`--base-url` 默认 `http://127.0.0.1:8080`，可指定其他明确授权的测试服务。
结果文件已存在时拒绝覆盖。登录和退出会产生正常审计记录；问答不修改业务主档。

## 指标解释

- `document_hit_at_4`：最终 Agent 返回的前 4 条引用里是否至少有一个预标注的相关文档，仅对正向题计算。不是原始分块 Recall，也不是答案正确率。
- `expected_behavior_shape`：正向题检查文档命中；拒绝题检查 SAFE_REFUSAL 且无引用；依据不足题检查 INSUFFICIENT 且无引用。
- `human_answer_grade`：当前为 null，保留给按参考答案进行的人工复核，不伪造模型自动裁判分。
- P50/P95：单并发、无预热、每题一次的 HTTP 总耗时，仅作本地诊断；不能直接与离线模型耗时对比。

题目为开发集，部分措辞贴近现有指南，存在容易命中的偏向。后续应另建独立测试集，包含同义问法、地区、日期和否定条件等难例，不能用本组的高命中率宣称泛化效果。

脚本保存 Git 版本、助手源码快照、知识库快照和本地 JAR 哈希；这些分别描述文件状态，不自动证明目标进程使用相同构建。
结果包含项目指南文本和虚构提问，没有密钥、token 或真实证件数据。真实业务评测需先明确数据处理范围，不直接复用这个输出策略。

## 第一轮结果

20/20 请求 HTTP 成功；正向文档命中 14/14；行为形态检查通过 18/20。
Q19（未来新规）与 Q20（未覆盖地区）未正确拒绝引用通用指南。
摘要覆盖度、拒绝原因表达还存在问题，详见 [第一阶段报告](../docs/assistant/ai-phase01-report.md)。

## 第二阶段检索记录与本机限制

新增固定的 40 题挑战诊断集 `datasets/rag_challenge.v2.jsonl`（附 SHA-256）和原始消融结果 `results/rag-retrieval-ablation-v2.json`：词法、混合检索、混合加重排分别命中 19/22、20/22、22/22；该指标不等于答案正确率。完整条件与限制见 [第二阶段记录](../docs/assistant/rag-v2-report.md)。

用户已禁止在本机下载或运行模型，权重与推理环境已删除。**本机不要运行加载语义模型的 `evaluate_rag.py`，也不要重新安装 `--extra rag`。** 保留脚本与结果用于历史对照；后续真实生成结果见下文，不能混用两个阶段的测试条件。

## DeepSeek 轻量服务阶段

当前源码通过 73 项离线/Mock 检查，涵盖官方 JSON Object 协议、缺 Key 时零外部请求、鉴权/余额/限流错误、同义查询候选、引用重编号、模型弃答与降级。Mock 不证明真实 DeepSeek 的效果。

独立服务器 V0.3.0 已配置用户的 DeepSeek Key，并经受限 SSH 隧道接入本机 Java 和页面。当前没有向量模型，不将 DeepSeek 当作 Embedding/Rerank 服务。旧模型依赖锁只在 `baselines/uv-local-models-v0.2.lock` 保留，不恢复到用户 Mac。

| 原始文件 | 实际检查与结果 |
| --- | --- |
| `results/deepseek-smoke-v1.json` | 3 题真实调用；2/2 正向文档命中、3/3 行为检查通过 |
| `results/deepseek-challenge-v1.json` | 固定 40 题真实调用；22/22 命中、MRR@4 0.9242、40/40 行为检查；22 次已校验生成、0 次失败降级；HTTP P50/P95 1.541/2.269 秒 |
| `results/deepseek-challenge-v1-audit.json` | 40 条响应来源/编号回核，49 段生成摘文逐字匹配；不等于充分性评分 |
| `results/java-agent-deepseek-v3.json` | Java 公开接口 20 题：HTTP 20/20、命中 14/14、行为检查 20/20；HTTP P50/P95 1.855/2.355 秒 |

以上均为单并发、每题一次、虚构开发诊断题，不是答案正确率、独立人工评分或生产压测。完整说明、页面场景与待完善项见 [验收报告](../docs/assistant/deepseek-rag-validation-report.md)。

`evaluate_service.py` 使用标准库通过 HTTP 测试，不加载模型，不读取提供商 Key；服务未就绪或输出文件已存在时拒绝运行。以下在专用 RAG 服务器执行，默认只测 H03/H05/H24；`--ids ''` 才运行全部 40 题，会产生真实模型请求，勿无故重复：

```bash
sudo python3 /opt/yixun-ai/evaluation/evaluate_service.py \
  --service-key-file /etc/yixun-ai/service.env \
  --dataset /opt/yixun-ai/evaluation/rag_challenge.v2.jsonl \
  --output /var/lib/yixun-ai/evaluation/deepseek-smoke-recheck-01.json
```

配置与运行方式见 [DeepSeek 部署说明](../docs/assistant/deepseek-deployment.md)。
