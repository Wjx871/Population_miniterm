# 数据与运维第二批交付及实测记录

本报告记录数据运维分支的历史验收。联调版采用 V4_020 持久化撤销，激活步骤见 [Issue #64 运维说明](../development/issue64-durable-revocation-gate.md)。

日期：2026-11-08。本文覆盖共享 JWT 撤销、显式读写路由、缓存权限隔离、双实例/主从部署配置和原生运行验证。

2026-11-15 更新：已在 GitHub Actions Ubuntu 环境完成容器镜像、真实 MySQL 主从、双实例及故障注入验证，详见 `doc/testing/cloud-container-verification-20261115.md`。下文 11 月 8 日的“待 Docker 验证”保留为当时记录，最新结论以云端报告为准。

后续开发、RabbitMQ 监控、V4_015 索引和三轮对照实验见 `doc/testing/data-ops-completion-20261115.md`；当前部署升级到 V4_015，使用 `doc/deployment/data-ops-handoff.md` 和最新执行清单。本文后续章节为第二批历史记录。

## 1. 必须先执行的数据库升级

部署当前代码前，先在目标**主库**执行 `doc/database/legacy/V4_014_shared_token_revocation.sql`。该文件只新增撤销表和到期索引，支持重复执行。不通过 Fresh 脚本升级已有业务库。

原生隔离验证表明，`population_miniterm.sql` 新建库快照已经含 V4_001—V4_010 的结构调整；其后再执行 V4_011—V4_014。将 V4_003 等历史重命名脚本重放到该快照会引用已不存在的 `residence_type`，本轮新增初始化脚本已按这个边界处理。该结论仅用于当前快照的新库初始化，旧库仍按实际历史版本安排迁移。

本轮没有升级或重建日常课程库，只操作名称以 `population_ops_verify_` 开头的新验证库。

## 2. 已完成的代码

### 共享撤销

- `JdbcTokenRevocationStore` 使用物理主库连接，保存 jti 的 SHA-256 和到期时间，不保存原始 JWT。
- 注销先提交主库，再尽力写 Redis 正向标记；主库写失败返回 503，不报告注销成功。
- Redis 未命中时始终查主库，不保存“尚未撤销”的负缓存。Redis 关闭、丢失标记、实例重启均不依赖进程内撤销表。
- 撤销查询不可用时认证返回 503；无效/过期/已撤销令牌仍返回相应 401。
- 新增过期撤销清理任务，受 `SCHEDULING_ENABLED` 控制；同一 jti 重复注销幂等。

### 读写路由

- 默认关闭从库：`REPLICA_ENABLED=false`，继续使用 `spring.datasource.*` 主库配置。
- 启用后，只有 `@ReplicaRead` 白名单可选从库。当前白名单为 `UnifiedStatisticsService` 的统计读取，以及 `DashboardService.overview/charts`。
- 登录、授权、撤销、审批、执行、普通业务详情、锁定查询和未标记的 readOnly 查询继续主库。
- 路由切面在事务前生效；使用延迟取连接；已开始的主库/写事务中嵌套调用统计白名单仍保持主库。
- 仅在**取得从库连接失败**时回主，默认连接等待 1000 ms、失败后冷却 5000 ms、从库池上限 5。SQL 发出后的错误不会自动重放。
- 从库连接池设置只读；部署主从实验进一步启用数据库 read_only/super_read_only。
- 管理员接口 `GET /api/ops/database-routing` 返回路由连接计数和回主次数，不返回连接串或密码。计数只覆盖路由入口，不含撤销存储直接访问主库的连接。
- 管理员接口 `GET /api/ops/runtime` 提供 JVM 堆/非堆内存（字节）、线程数、运行时长（毫秒）、可用的进程/系统 CPU 比率（0—1）、主从连接池活跃/空闲/等待数，以及请求 P50/P95（毫秒）和 5xx 错误率。请求窗口最近 60 秒、最多 4096 项，超出容量明确标记 capacityLimited；吞吐需使用相邻采样的累计请求数与时间差计算。未引入新的监控平台依赖。
- 尚未自动依据复制延迟摘除从库，也不执行主库故障自动提升。统计允许延迟；异常时可关闭 REPLICA_ENABLED 并重建应用实例。

### 缓存与调度

- 字典管理权限和普通读取使用不同缓存键，分别支持包含停用项和只读启用项的结果。
- 统计键增加角色和 SELF 用户 ID，避免相同地区/部门下不同个人或角色的统计互用。命名空间变更为 `statistics:v2:*`，旧键自然过期。
- 导出清理、居住证/流动登记过期、撤销清理均受 `SCHEDULING_ENABLED` 控制。
- Compose 中 backend-1 启用定时任务，backend-2 禁用。调度实例故障时需要显式选择新的调度实例，不宣称自动接管。

## 3. 已运行的测试

- `mvnw.cmd -Punit-tests test -q`：58 项单元测试通过，涵盖撤销先持久化、拒绝负缓存、主库不可用、路由回主/冷却、不重放 SQL、SELF/角色统计缓存隔离、延迟百分位与有界窗口等。
- 定向集成测试共 30 项通过：读写路由 4、字典缓存双向权限隔离 2、运维接口权限 5、迁入业务 17、撤销失败 HTTP 行为 2。
- H2 使用两个不同数据源和不同标记值证明真实路由；验证写事务内读取主库、未标记 readOnly 走主库，以及异常后的路由上下文清理。H2 数据源验证不代表已经完成 MySQL 复制验收。
- `mvnw.cmd -DskipTests package -q` 后端打包通过。
- 日志诊断脚本 2 项离线规则测试通过；此前第一批的前端构建/测试和 OCR 9 项离线测试记录仍适用，本轮未修改其业务实现。
- 三份 Compose 文件通过官方 JSON Schema 校验；复制初始化脚本通过 Bash 语法检查，PowerShell 验证脚本通过语法检查。
- 既有 viewer 数据范围两项失败仍按第一批记录处理，未修改权限契约或测试断言。

## 4. 真实 MySQL 与独立 JVM 验证

运行入口：`scripts/ops/verify-native-data-ops.ps1`。实际数据库版本为 MySQL 8.0.44，Redis 关闭，使用相同 JWT 密钥、共享上传目录和两个独立 JVM。

最终有效证据目录：

`target/ops-native-verification/population_ops_verify_20261108_164239/`

该目录中的 `results.txt`、各实例 stdout/stderr、`baseline.json`、`backup.sql` 和恢复文件可复核。

已验证：

1. 新建隔离库、加载新建快照、执行 V4_011—V4_014，并重复执行 V4_014。
2. A 签发的令牌在注销前可由 B 使用。
3. 通过 A 创建通用申请并上传虚构 PNG，B 经过授权下载的字节与原始文件一致。
4. A 注销后 B 对同一令牌返回 401；主库保留一条撤销哈希记录。
5. 终止实际 B JVM，再启动实例标识为 `instance-b-restarted` 的 JVM；旧令牌仍返回 401，文件仍可下载。
6. 备份数据库及上传目录，恢复到新的 `_restored` 数据库和独立文件目录，启动第四个恢复实例；授权文件下载字节一致，旧撤销令牌仍被拒绝。
7. 脚本结束关闭本次启动的实际 JVM，保留验证库和证据，方便复核。

Windows 的 javapath 启动器会拉起子 JVM。验证脚本已改为读取 java.home 后直接启动实际 java.exe，按响应中的实例标识检查就绪。上面只采用修正后完整重跑的证据；此前尝试不作为重启验收依据。已确认没有遗留本次验证的后端 JVM。

恢复测试期间没有其他并发写入。并发业务环境中的数据库/文件一致性快照与备份窗口仍需制定后再演练。

### 小规模基线

- 接口：`GET /api/statistics/overview`；管理员；单实例接收请求；Redis 关闭、读写分离关闭。
- 数据：新建课程种子库，没有额外填充人口样本，包含本次的一份通用申请与虚构材料。
- 配置：4 并发、10 次串行预热、100 次正式读取；客户端与服务同机；未采集 CPU/内存。
- 实测：P50 13.675 ms，P95 30.226 ms，吞吐 239.272 请求/秒，错误率 0。
- 百分位采用 nearest rank，包含全部正式请求；原始逐请求耗时在 baseline.json。

这是功能与脚本可运行的小样本基线，不是容量结论，也不是缓存、主从或双实例的提升证明。完整性能报告仍需更大虚构数据、固定硬件、持续负载、资源采样及同条件重复对照。

## 5. 如何运行双实例

使用外部、已迁移的数据库时：

```powershell
docker compose --env-file config/docker/.env -f config/docker/compose.yaml -f config/docker/compose.dual.yaml --profile dual config --quiet
docker compose --env-file config/docker/.env -f config/docker/compose.yaml -f config/docker/compose.dual.yaml --profile dual up -d --build
```

两个实例共享数据库、Redis、JWT 配置和上传/导出卷；各自写入独立日志子目录，避免共同滚动同一个日志文件；Nginx 使用两个 upstream。失败节点通过被动失败计数避开，首个失败请求可能报错，失败窗口必须实测；配置关闭自动请求重放。后端显式更新时依赖重启 Web，重新解析实例地址。

同时使用 OCR 时再加 `--profile ocr`，并按第一批说明设置 `IDCARD_OCR_URL=http://ocr:8866`。

## 6. 如何初始化隔离主从实验

`compose.mysql.yaml` 使用独立 MySQL 8.4 主从容器、唯一 server-id、GTID、ROW binlog、持久卷，不映射宿主数据库端口；不会修改宿主机现有 MySQL。

在 `config/docker/.env` 设置非 root 的应用 `DB_USERNAME`、`DB_PASSWORD`、独立 `MYSQL_ROOT_PASSWORD`，以及 20—32 位字母/数字/下划线/连字符组成的 `REPLICATION_PASSWORD`（复制连接密码不能超过 32 个字符）。保留 DB_URL 配置项以满足基础文件插值，主从覆盖文件会把两个应用实例指向容器内数据库。

**应用停止且实验库为空时**显式初始化：

```powershell
docker compose --env-file config/docker/.env -f config/docker/compose.yaml -f config/docker/compose.mysql.yaml --profile dbinit run --rm db-bootstrap
```

脚本先检查两个 `population_ops` 库均为空、从库没有复制通道，再导入快照及新迁移；创建复制账号；用包含 GTID 信息的一致性 dump 初始化从库，配置自动定位，等待追平并启用持久只读。非空库直接拒绝，没有自动清库逻辑。失败留下的非空实验库需要人工检查，不反复执行初始化覆盖数据。

初始化成功后运行完整双实例实验：

```powershell
docker compose --env-file config/docker/.env -f config/docker/compose.yaml -f config/docker/compose.mysql.yaml -f config/docker/compose.dual.yaml --profile dual up -d --build
```

检查复制线程、实际路由，再执行停止复制/停从库/停业务实例等实验；复用 `scripts/ops/verify-deployment.ps1`。两业务实例均在 mysql 覆盖文件中配置同一组主从连接。

配置依据：[MySQL GTID 初始化说明](https://dev.mysql.com/doc/refman/8.4/en/replication-gtids-howto.html)、[复制源配置语法](https://dev.mysql.com/doc/refman/8.4/en/change-replication-source-to.html)。当前没有 Docker，因此此段仅完成配置、脚本和结构/语法检查，尚未证明 MySQL 8.4 镜像构建运行或实际主从复制成功。

## 7. 压测和日志诊断工具

`scripts/perf/read-load.py` 仅发送 GET，令牌从 OPS_BEARER_TOKEN 环境变量读取，不保存令牌或响应正文，保存 HTTP/业务成功状态、逐请求耗时和实例分布。

```powershell
python scripts/perf/read-load.py --url http://127.0.0.1:8088/api/statistics/overview --requests 1000 --concurrency 10 --warmup 30 --output target/perf/overview.json
python scripts/ops/diagnose-logs.py logs/population-miniterm.log --output target/ops-diagnosis.json
```

诊断规则覆盖 Redis 回源告警、从库连接失败、HTTP 5xx 和高 P95；输出观测值、文件/行号/traceId、可能原因和检查项，不把建议当成已确定根因。日志窗口是传入文件的全部记录，应选择同一实验时间窗口的日志文件；Redis 告警限频，日志条数不等同于错误请求总数。请求日志只采集状态/耗时，未保留 URL，因此无法仅凭该日志按接口聚合。

## 8. 剩余验收与协作

- Docker 环境可用后实际构建镜像，验证 Nginx 配置、Gzip、分流与节点故障、卷权限、Redis 真实停机回源、MySQL 复制与延迟。
- JVM/CPU/内存及连接池快照接口已实现；继续接入前端看板、复制延迟和队列指标，并结合真实故障时间线检验诊断规则。
- 完成更大样本和重复压测，分别对比缓存、主从和双实例，选择有测量依据的 SQL/索引瓶颈优化。
- RabbitMQ 消息业务、统计/RMI 服务及新增 RAG/材料 AI 服务仍需各模块实际构建与接口产物；现有身份证 OCR 不能替代这些服务。
- 保留第一批的 viewer 权限契约问题，交由张锦源核实。所有原始测试数据、证据和备份位于被忽略的 target 目录，发布验收时应另行归档。
