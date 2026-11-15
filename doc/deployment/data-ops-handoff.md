# 数据与部署运维交付说明

更新：2026-11-15。本文供赵书亚部署、演示及三位协作者接入使用。云端验证是临时 Ubuntu 实验环境，结束后清理容器和实验卷；它不等于已开通长期在线业务站点，也不需要本机 WSL。

云端入口为 `.github/workflows/data-ops-cloud.yml`。专用验证分支的源码 push 跑完整三轮对照；手动 workflow_dispatch 跑部署/故障短回归及 100 请求基线，跳过完整性能实验并在 summary 标明。诊断证据在容器重启/重建前保存，结束时日志只是额外快照。

## 构建与配置

构建入口为 `config/docker/compose.yaml`。基础服务包含 Java、Vue/Nginx 和 Redis；可选覆盖文件依次添加 `compose.mysql.yaml`（空库主从实验）、`compose.dual.yaml`（双业务实例）、`compose.rabbitmq.yaml`（私有 RabbitMQ）。`.env.example` 是配置模板，真实 `.env` 不提交。

Java 17；前端构建使用 Node 22、`npm ci` 与 `npm run build`，产物复制至 Nginx `/usr/share/nginx/html`。浏览器只通过 `/api` 调 Java，内部服务名仅在 Docker 网络中解析；History 路由回退到 `index.html`。静态资源使用构建哈希和 immutable，HTML 重新验证，API no-store，启用 Gzip。

现有外部数据库先备份并执行适用迁移至 V4_015；V4_014 新增共享撤销表，V4_015 为流动人口统计新增 `(status,current_flag)` 覆盖索引。后者可重复执行，遇到同名异构索引会拒绝；在线建索引仍需评估大表资源及元数据锁，安排维护窗口。若需撤销此性能索引，可在主库执行 `ALTER TABLE floating_population DROP INDEX idx_floating_status_current, ALGORITHM=INPLACE, LOCK=NONE`，不影响业务数据或查询正确性。

`compose.mysql.yaml` 的 db-bootstrap **只接受两个空的 population_ops 实验库**，不能用于更新已有业务库。增量升级不要重新导入全量快照。主从初始化与启动命令见 `data-ops-phase2.md` 第 6 节。

使用 RabbitMQ 时，在已有启动命令的最后一个 `-f` 后添加 `-f config/docker/compose.rabbitmq.yaml`，配置 `RABBITMQ_USERNAME`、随机 `RABBITMQ_PASSWORD`、可选 `RABBITMQ_VHOST`，再启动 rabbitmq、backend、backend2 和 web。管理口与 AMQP 口均不映射宿主机，数据使用独立命名卷；业务容器通过 rabbitmq:5672 访问。不要用默认课程密码部署到公网。

OCR 镜像来自 `services/ocr/`，当前调用百度 OCR，不要求本地 GPU。密钥由环境变量注入；Java 默认超时 60 秒、专用前端请求 90 秒、代理读取 95 秒。未配置时保留跳过识别的业务路径。真实云识别质量及新 RAG/材料预审模型由 AI 负责人提供独立验收。

## 读写、一致性与故障策略

- 普通读写及安全撤销查询走主库；只有明确标记的统计查询可读从库。readOnly 本身不代表可以接受延迟。写事务内的嵌套统计仍走主库。
- 从库连接获取失败时回主库，并进入短暂冷却；不重放执行失败的 SQL。主库故障不自动提升从库。
- Redis 用于可重建缓存，发生异常回源；字典按管理/只读受众区分缓存。事务成功提交后失效；SCAN 不是并发回填情况下的强一致方案。业务要求立即可见时继续读主库或绕过统计缓存。
- JWT 撤销以主库为事实来源，Redis 未命中仍查主库；主库无法确认撤销状态则拒绝请求，不以本机内存代替安全判断。
- 两实例共用数据库、JWT 密钥、Redis、上传和导出卷。backend2 关闭定时任务；这只是静态双实例部署约定，扩容时应引入任务抢占或外部调度。
- 双实例代理只对 GET/HEAD 允许最多另选一次上游；POST/PUT/PATCH/DELETE 不重试，避免一次调用多次写入。失败写请求由业务幂等和显式用户操作处理。
- 后端容器重建后重启 Web 以重新解析服务地址。这里只提供同主机双实例；主机故障仍需外部负载均衡、存储和数据库方案。

## 运维接口与协作契约

以下均需登录且持有 `system:user:manage`，匿名 401，普通用户 403。前端运维页面通过这些接口接入，不连接数据库、Redis 或 broker 管理口。

- `GET /api/ops/cache`：当前实例、启用状态及进程累计 hits/misses/readErrors/writeErrors/evictionErrors。按同一实例同一窗口的增量计算命中率；实例重启归零。
- `GET /api/ops/database-routing`：物理连接路由和回退计数；统计与安全请求会产生不同用途的连接，不能把计数直接等同业务请求数。
- `GET /api/ops/runtime`：JVM 堆、线程、运行时间、进程 CPU、请求窗口 P50/P95、错误率和连接池。窗口为最近 60 秒、最多 4096 样本；容量标志用于解释截断。
- `GET /api/ops/rabbitmq`：DISABLED/UP/DOWN、ready/unacknowledged、消费者、队列数及状态；最多取前 100 队列，`complete=false` 和 `aggregationScope=first_100_queues` 明确表示部分统计。DOWN 返回脱敏原因，无管理地址、密码或消息正文。

RabbitMQ 环境和监控由赵书亚提供；业务生产者、消费者、持久化任务状态、确认、有限重试及幂等由张锦源实现。AI 消费者与模型版本由王家兴提供。消息至少协商 `schemaVersion/taskId/taskType/traceId`；消费者按消息设置日志上下文并在 finally 清理，不能把异步线程继承上下文当成已贯通。当前 taskId 只有日志字段预留，监控中的合成队列消息不证明业务异步流程完成。

## 备份、升级与恢复

1. 记录当前镜像/提交、迁移版本、配置版本、主从状态。停止写入入口和两业务实例，避免文件与 SQL 备份时间不一致；保留 Redis、MySQL、文件卷。
2. 使用数据库运维账号对主库做 `mysqldump --single-transaction --routines --triggers --events` 逻辑备份。密码通过容器内已有环境或受限客户端文件提供，禁止写在命令行或文档。通过二进制安全的文件复制归档，不依赖旧 PowerShell 文本重定向保存 SQL/压缩包。
3. 一起归档 uploads、exports；另行安全保存 JWT_SECRET 和必要配置。为每项记录时间、大小和 SHA256。Redis 缓存可重建；主库 token_revocation 和 JWT 密钥用于保证恢复后的撤销状态。
4. 恢复到**新的隔离库和新的文件目录/卷**，恢复后先验证 SQL 表计数和文件哈希，再启动一个实例检查登录、权限、被撤销 Token 拒绝、文件授权下载；通过后启动第二实例并复核。
5. 升级先执行兼容迁移，再更新两业务实例与 Web。数据库回滚不能仅换旧镜像；必须核对旧代码是否兼容新结构，必要时用上述备份恢复至新库后切换连接。
6. 普通停机使用 `down` 保留卷；不要在保留业务数据的环境使用 `down --volumes`。云端脚本销毁卷仅因其专用随机项目和纯合成数据。

已有原生隔离验证脚本 `scripts/ops/verify-native-data-ops.ps1` 实测过独立 JVM、共享撤销、材料字节、停止/恢复，以及新库新目录恢复。其证据与限定见第二批报告；正式部署仍要对实际备份目标做恢复演练。

## 演示与验收顺序

1. 展示镜像和容器状态、双实例响应头，再演示静态缓存和路由刷新。
2. 字典连续读取产生 hits，等待过期，再更新和禁用，核对缓存失效及不同权限结果。
3. 展示实际从库路由、停止 SQL 复制制造延迟；强一致业务写后读仍读主库，恢复复制追平。
4. 停 Redis 后注销，另一实例及重启实例都拒绝旧 Token；恢复 Redis。
5. 一台实例上传材料，另一台授权读取；停止一个后端，观察读请求切换。展示独立代理试验中写请求不重放。
6. 展示队列积压、broker 重启后的持久消息、停止 broker 的 DOWN 状态和普通业务继续可用。
7. 展示可复现性能对照、EXPLAIN ANALYZE、慢查询记录与日志诊断，说明没有提高的项目及实验规模限制。

最终业务联调仍需三位协作者的真实接口、模型、任务和页面。已知原始 viewer 权限契约的两项测试失败应由后端负责人核实，不通过修改断言掩盖。课程要求中的长期云部署、JavaFX/Web Service 等应按团队教学口径完成；临时 CI 验证不能代替完整产品验收。
