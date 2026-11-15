# 邑循 DeepSeek 轻量 RAG 部署

首次部署日期为 2026-11-01。专用 RAG 服务器运行 Ubuntu 24.04.4 LTS。后续版本与问答验收见 [青岛政策六题复核](../../evaluation/qingdao-pilot-findings.md)。

## 当前已完成

- 0.3.0 服务部署到 `/opt/yixun-ai/current`，systemd 单元为 `yixun-ai`，专用无登录账户运行。
- 仅监听 `127.0.0.1:8091`，未开放公网业务端口。CPU 限额为半核，MemoryHigh 192MiB、MemoryMax 256MiB，单进程。启动验收时 cgroup 内存 36265984 字节（约 34.6MiB），不是容量或峰值保证。
- 19 篇项目/系统指南、56 个分块已加载。用户已隐藏输入 DeepSeek Key，健康状态 UP、generation=true；无 Key 路径仍明确为 DEGRADED，内部接口未授权请求返回 401。
- 73 项离线/Mock 测试通过；真实 DeepSeek 40 题诊断正向文档命中 22/22、行为检查 40/40、无生成失败降级。Java 20 题回归行为检查 20/20，页面已验证迁出同义问法与未覆盖地方政策拒答。指标定义及原始结果见 [验收报告](deepseek-rag-validation-report.md)。
- 发布包只包含白名单源码、指南、运行依赖锁与部署脚本，不含密钥、模型、虚拟环境。实际发布 ID：b947b685747bc4cf。

当前默认依赖只有 FastAPI、Uvicorn、HTTPX 及其基础依赖，不包含 Torch、Transformers、NumPy 或任何模型权重。本机测试环境约 27MB，未启动本机推理进程。旧模型依赖锁仅存档于 `evaluation/baselines/uv-local-models-v0.2.lock`，禁止在本机恢复该环境。

## RAG 流程与限制

先检查敏感请求、地区、日期和来源适用性，再由 DeepSeek 生成最多两条简短同义检索问句；原问与改写分别进行词法检索并按名次融合，保留各路优先候选。最多 16 个候选片段交给模型选择完整原句。

服务检查 JSON、来源编号、逐字原文及完整句边界，重新编号后只返回实际使用的引用；不能直接回答时允许模型返回空选择，响应为 INSUFFICIENT / MODEL_ABSTENTION，不能用无关片段顶替。HTTP 或校验失败时明确返回 EXTRACTIVE_FALLBACK。401、余额不足和限流不会在下一阶段再次请求。

这是词法广召回＋大模型检索词改写/证据选择，没有启用向量检索，也不是 CrossEncoder 重排。当前 DeepSeek 公开 API 文档未列出通用 Embedding/Rerank 端点，不虚构这些能力。固定题集已测量当前效果，仍需独立题集与人工评分验证泛化、充分性和选文精度。

一次正常正向问答最多两次生成请求：改写一次、证据选择一次；没有自动收费重试。改写超时上限 8 秒，服务器生成阶段 40 秒，输出分别限制 256/1200 tokens。无 Key、敏感请求、未覆盖的正式政策等路径不发起模型请求。

当前资料是项目指南，不是权威地区政策；完整身份证号、手机号等敏感请求先拦截。模型不能操作业务数据库、审批或执行任务。

## 配置或更换 DeepSeek API Key

当前 Key 已由用户配置，下述命令仅在需要更换时执行。DeepSeek Key 不下载到本机，也不由隧道账户读取。

在专用 RAG 服务器的交互终端运行：

```bash
sudo python3 /opt/yixun-ai/set-deepseek-key.py
```

看到 `DeepSeek API Key (hidden input):` 后粘贴已有 Key 并回车，字符不回显。脚本将其原子写入仅 root 可读的 `/etc/yixun-ai/provider.env`，重启本服务，不输出 Key。不要在聊天里发送 Key，也不要把带 Key 的命令粘贴到腾讯云“执行命令”任务中，因为任务正文会留存。

默认配置：官方地址 `https://api.deepseek.com`，模型 `deepseek-flash`，JSON Object 模式，显式关闭思考模式。当前模型标识已根据 [官方入门文档](https://api-docs.deepseek.com/) 核实；接口遵循 [Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/) 和 [JSON Output](https://api-docs.deepseek.com/guides/json_mode/)。模型名可由管理员在 `/etc/yixun-ai/service.env` 调整，之后只重启 `yixun-ai`。

## 运行检查

```bash
systemctl status yixun-ai --no-pager
journalctl -u yixun-ai -n 40 --no-pager
curl http://127.0.0.1:8091/health
systemctl show yixun-ai -p MemoryCurrent -p MemoryMax -p CPUQuotaPerSecUSec
```

`generation=true` 仅表示模型列表探测可用，不代表回答质量或生产容量。真实调用已另用少量虚构问题、固定挑战集及引用原文回核验收；没有做生产压测，尚无独立人工答案评分。

## 本机访问与受限 SSH 隧道

用户已授权建立 `yixun-tunnel` 账号和本机专用公钥。该账号只允许本地转发到服务器 `127.0.0.1:8091`；强制命令只提供 `get-service-key`，仅返回 RAG 内部服务密钥，不提供 Shell/sudo、TTY、代理转发或 DeepSeek Key。实际检查普通命令被拒、转发服务器 22 端口被拒。

本机严格校验由腾讯云控制台核实的主机公钥，私钥和 known_hosts 位于仓库忽略目录 `tmp/ssh-yixun/`。本机启动脚本从该文件读取唯一已验证的 SSH 服务地址。本地端口也只监听回环地址，不开放公网 8091。

```text
浏览器 :5180 → Java :8080 → 本机 127.0.0.1:18091
→ 受限 SSH 加密转发 → 独立服务器 127.0.0.1:8091 → DeepSeek HTTPS
```

项目根目录 `.env.local` 中已设置 `POLICY_RAG_ENABLED=true`、`POLICY_RAG_URL=http://127.0.0.1:18091`，只保存内部调用密钥；旧 `POLICY_AI_ENABLED=false` 避免重复生成。不要打印此文件。AI 服务子目录的本地生成仍关闭。

```bash
# 本机项目根目录：数据库、已授权隧道、Java、Vite；不会迁移数据库
bash scripts/macos/start.sh

# 停止前后端和本项目隧道，保留数据库进程与文件
bash scripts/macos/stop.sh

# 同时停止本项目独立 MySQL，数据文件保留
bash scripts/macos/stop.sh --all
```

重复启动已核实复用原有四个进程。Java 最大堆为 512MiB，Vite 的 Node old-space 默认上限 512MiB；这是堆参数，不是整体内存峰值保证。复制仓库到其他电脑不会自动获得隧道权限，须由维护者明确授权新公钥，不能复制私钥或关闭主机校验。

页面入口为 `http://127.0.0.1:5180/assistant/policy`；依赖当前本机前后端、数据库、SSH 网络及服务器服务。后续远程访问方案由部署负责人另行设计，不将开发端口作为公网部署。

## 复现与发布

```bash
# 本机：仅基础依赖与不加载模型的测试
cd ai-service
uv sync --locked --python 3.12
.venv/bin/python -m pytest -q
.venv/bin/python deploy/build_release.py
```

发布包由 `deploy/build_release.py` 生成到仓库 `tmp/`，按 SHA-256 校验后才在目标机解包。`deploy/bootstrap.sh` 会先核对 2 核、低于 3GiB 内存、内网地址与受管目录标记。执行部署或初始化隧道账户前，由维护者根据云控制台记录向脚本进程传入 `YIXUN_RAG_EXPECTED_PRIVATE_IP`。脚本只安装轻量运行依赖，不下载模型；首次安装后的代码更新只切换发布目录并重启本服务。
