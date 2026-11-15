# 云端容器验证记录（2026-11-15）

后续开发与新一轮 18 组验证见 `data-ops-completion-20261115.md`，其中已修复本报告单实例故障出现的 2 次 504，并新增 RabbitMQ 与更大数据性能对照。本文保留首次云端验证的原始结果；旧验证分支已删除。

## 执行位置与代码

- 执行平台：GitHub Actions，Ubuntu 24.04 托管执行器。
- Docker Engine 28.0.4，Docker Compose v2.38.2（取自首轮运行时检查）。
- 本轮 MySQL 容器实际版本：8.4.13（取自成功运行的容器日志）。
- 仓库：`Wjx871/Population_miniterm`。
- 专用分支：`codex/cloud-validation-20261115`；本轮源代码快照基于本地 `caf3f9aabd89467df78bf3dbb0eb1ad6f5b0ddbc`，包含本机未提交的运维改造。
- 不使用本机 WSL。数据库、缓存、文件卷和随机凭据均在隔离执行器创建；真实 OCR 云接口不在本次测试范围。
- 自动化入口：`.github/workflows/data-ops-cloud.yml`、`scripts/ops/run-cloud-validation.sh`、`scripts/ops/verify-cloud-stack.py`。

## 运行与修复

1. [首轮运行](https://github.com/Wjx871/Population_miniterm/actions/runs/43693961714)：容器运行时检查、前后端/OCR 镜像构建、OCR 9 项离线测试通过；MySQL 复制连接拒绝超过 32 字符的密码。修正生成长度和脚本输入约束为 20—32 字符。
2. [第二轮运行](https://github.com/Wjx871/Population_miniterm/actions/runs/43694256933)：初始化完成后，状态输出过滤未匹配字段导致脚本退出。修正 MySQL 客户端字段名输出选项，并新增复制启动后写入事务及 GTID 追平验证，避免只证明快照已导入。
3. [第三轮运行](https://github.com/Wjx871/Population_miniterm/actions/runs/43694527150)：代码 `8d799725597e48b75ff81e00dd283b89705f04a5`，最终结果 **success**。镜像构建、OCR 离线测试、主从初始化及 13 组应用/运维断言全部完成。测试结束后容器和卷已清理。

## 本轮验收范围

镜像构建、OCR 离线协议、实际主从复制、受控延迟及强一致读取、真实连接路由、Redis 停机回源和共享撤销、跨实例文件、实例重启、Nginx 分流及单实例故障、Gzip/缓存头/SPA 路由、运维接口权限和运行指标、小规模并发基线。

脚本逐项断言并保存 `checks.json`，失败保存 `failure.txt`；每轮结束上传容器日志和资源快照到 Actions artifact，再销毁本轮容器和卷。性能样本只用于验证实验入口，不宣称容量上限或优化收益。

本地证据目录：`target/cloud-verification/`。该目录不提交 Git，验收材料如需长期保留应另行归档。

## 成功运行的具体结果

- Nginx 统一入口确实分流到 backend-1/backend-2，实例响应标识和 traceId 正常。
- 匿名访问运维接口被拒绝，viewer 返回 403，管理员可读取 JVM、请求和连接池指标。
- 大 JS 实际采用 Gzip；哈希静态资源长期缓存，HTML 重新验证，API no-store，History 路由回退正确。
- 统计查询使真实从库连接计数由 0 增为 1；主从 server-id 不同，并非配置两个相同连接。
- 暂停复制 SQL 线程后，主库的新标记在从库不可见；创建申请后立即读取仍得到新数据。恢复复制后 GTID 追平。
- 上传的虚构材料能经两个业务实例授权下载，字节一致；容器重启后仍可访问。
- 停止从库后，统计接口继续成功，fallbacks 由 0 增为 1。
- 停止 Redis 后业务回源正常，注销结果对两个实例生效；重启第二实例后仍拒绝旧令牌。
- 诊断脚本在实际容器日志中识别到 Redis 回源和从库连接回主两类注入故障，保留日志行号与 traceId；未调用大模型生成诊断。

### 单实例故障的实际边界

关闭 backend-2 后，20 次请求中 18 次返回 200，2 次返回 504。验证脚本把该场景判为“剩余实例继续提供服务”，不等于无损故障切换。当前双实例 Nginx 关闭自动重放，采用被动失败探测；若课程要求故障期间读取全部成功，仍需增加只读请求有界重试，并另测写请求不被重放。不能把这两次失败隐藏在正常压测的 0 错误率中。

### 正常运行的小规模基线

在节点恢复后的独立正常窗口，4 并发、10 次预热、100 次正式请求：P50 **28.386 ms**，P95 **50.976 ms**，吞吐 **130.2 请求/秒**，错误率 **0**；两个实例各处理 50 次请求。该窗口与上述故障窗口分开统计。

数据为全新课程种子库及一份虚构申请/材料，不能用于容量上限结论；不能与此前 Windows 单实例结果直接比较或宣称优化幅度。

## 可复核证据

- Actions 页面：上面的成功运行链接，可查看完整步骤与下载 artifact（保留 14 天）。
- 仓库长期保留的脱敏原始数据：`doc/testing/evidence/cloud-20261115/checks.json`、`baseline.json` 和 `runtime-end.json`。
- 本地完整 artifact：`target/cloud-verification/run-43694527150-evidence/data-ops-cloud-43694527150-1/`，包含容器日志、资源和清理记录；本轮后处理生成的 `diagnosis.json` 也在该目录。

尚未完成真实百度 OCR、RabbitMQ 业务或新增 RAG 服务的部署测试；这些不在本次容器验证范围。本轮只构建 OCR 适配器并运行 9 项 mock 离线契约测试。此前的 58 项单元测试和 30 项定向集成测试属于本机记录，本次工作流没有将其伪称为云端重跑结果。
