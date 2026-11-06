# S5-A2 验证与交付报告

## 1. 隔离工作区与变更边界

- Worktree：`C:\Users\jxgm\.codex\worktrees\s5a2-tx-cache-boundaries\小学期`。
- 施工分支：`feat/20261106-s5a2-tx-cache-boundaries`。
- 精确基线：`3e6c87ed4f2441b974b3600ef7f13af06932a89d`。
- 使用桌面 `create_worktree` 从该提交建立独立工作区，再创建 feature 分支；没有复用或清理其他工作区。
- 生产源码仅修改 `ReferenceCacheAspect.java` 和 `HouseholdService.java`。没有修改 pom、SecurityConfig、DataSource、application 配置、迁移、部署或前端。
- 完整提交 SHA 和实际 push 核验记录见最终交付回复；本报告随该 feature 提交交付，不修改 integration/main/S3/Snapshot。

## 2. 旧实现复现与最终机制

在生产源码仍为精确基线时，经过真实 Spring AOP、H2 和 Redis 传输 Stub 运行三项复现测试，结果为 **3 tests / 3 failures / 0 errors / 0 skipped**，构建耗时 **1:38**，属于预期的缺陷复现：

| 测试 | 旧实现实际结果 | 应有结果 |
|---|---|---|
| `administratorThenViewerNeverLeaksDisabled` | 管理员预热后 viewer 得到 `[ON, OFF]` | viewer 仅 `[ON]` |
| `viewerThenAdministratorKeepsCompleteResults` | viewer 预热后管理员仅得到 `[ON]` | 管理员 `[ON, OFF]` |
| `coldWriteTransactionCannotPublishUncommittedValueOnRollback` | 写事务内部查询访问 Redis（GET 计数为 1） | 写事务 Redis GET/SET 都为 0 |

修复采用当前有效权限分区：`dictionary:v2:MANAGE:<TYPE>` / `dictionary:v2:ENABLED_ONLY:<TYPE>`，不读取旧共享命名空间。认证身份与 `dictionary:manage` 每次在缓存 lookup 前检查，不接收客户端可见性参数，不按角色名或 userId 选择。未认证、未认证状态的 token 或停用身份不能返回字典缓存。类型 trim 后使用 `Locale.ROOT`；null/空白继续原查询但不缓存。

活动写事务中的字典、区划查询直接回源，不读写 Redis。`@Order(0)` 使缓存切面包裹默认事务 advice；独立调用在事务提交后失效，已存在外层事务时注册 afterCommit，外层回滚不失效。没有活动事务的普通查询仍正常缓存；RO 展示事务也允许使用缓存。

`HouseholdService.member()` 由 RO 改为默认 RW，原 `lockMember` 的 `FOR UPDATE`、DataScope 和业务行为保留。独立调用通过真实 SQL/连接观察证明 active=true、readOnly=false、autoCommit=false；add/updateMember 同类内部调用由外层事务与同一连接承担，不把自调用当作再次经过事务代理。

## 3. 实际自动化结果

环境：Windows 11 amd64；Oracle JDK **21.0.7**；Maven Wrapper **3.9.16**；编译 **--release 17**，不是 JDK 17 运行时验收；Spring Boot 3.5.3；随机独立 H2 内存库 MySQL mode。新测试在 StringRedisTemplate 边界提供可控 Stub，保留真实 OptionalRedisService、Jackson、Spring AOP 和业务 Mapper；没有连接团队 Redis/MySQL。真实 Redis TTL、网络、复制与双实例行为不由 Stub 证明。

| 执行 | Tests | Failures | Errors | Skipped | Maven 总耗时 | 结论 |
|---|---:|---:|---:|---:|---|---|
| 精确基线 `mvnw.cmd test` | 677 | 0 | 0 | 0 | 6:11 | PASS |
| 未修复源码上的复现专项 | 3 | 3 | 0 | 0 | 1:38 | 预期 RED |
| 修复后首轮字典/缓存/统计专项 | 38 | 5 | 0 | 0 | 1:44 | 测试账号缺统计授权，已修正夹具 |
| 核心事务单独专项 | 14 | 0 | 0 | 0 | 1:39 | PASS |
| 最终四类新增专项 | 53 | 0 | 0 | 0 | 1:54 | PASS |
| 最终完整 Maven 回归 | 730 | 0 | 0 | 0 | 6:52 | PASS，2026-11-06 17:19:02 +08:00 完成 |

首轮统计失败未跳过：公共 schema 的 population 账号没有 `statistics:view`，预热即被真实 RBAC 拒绝。测试改用事务内独立的 REGION 用户/角色/部门与真实权限 grant；同一 JWT 在区划改变后断言精确的“用户区划数据范围无法验证”，同时证明统计权限仍存在，避免权限不足导致假阳性。未修改生产 RBAC。

最终全量 Surefire XML 共 **65 个 suite / 730 个用例**，与 Maven 日志一致；原有 677 个用例加新增 53 个，全部执行，没有使用 skip 或测试失败忽略参数。构建提示 JaCoCo 缺少执行数据，本轮不提供覆盖率结论。

| 关键既有回归（最终全量中实际执行） | Tests | Failures / Errors / Skipped |
|---|---:|---|
| Phase09DataDictionaryIntegrationTest + Phase09AdminRegionIntegrationTest | 22 | 0 / 0 / 0 |
| UnifiedStatisticsScopeCacheTest + StatisticsScopeIntegrationTest | 4 | 0 / 0 / 0 |
| AuthRbacIntegrationTest | 19 | 0 / 0 / 0 |
| S5A1StrictRedisServiceTest、S5A1StrictRevocationHttpIntegrationTest、TokenRevocationServiceConsistencyTest、RevocationEventsTest、RevocationConfigurationTest | 83 | 0 / 0 / 0 |
| AiPrecheckIntegrationTest（S3/S4-A） | 36 | 0 / 0 / 0 |
| AiConfirmIntegrationTest + ApplicationMaterialSubmitConcurrencyIntegrationTest + ApplicationMaterialFeatureOffIntegrationTest | 21 | 0 / 0 / 0 |
| PolicyPythonAdapterIntegrationTest + PolicyPythonHttpStubIntegrationTest | 27 | 0 / 0 / 0 |
| S2 五个入口测试 + RmiInvocationHandlerClassificationTest + PopulationMetricsCalculatorTest | 19 | 0 / 0 / 0 |

上表为关键回归子集，不是全量分类求和；迁入迁出、审批、人员家庭户和其他原有测试也在完整 Maven 结果内。S2 五个入口测试为 S2AggregationFacadeTest、S2RemoteDisabledIntegrationTest、S2RmiClientConcurrencyIntegrationTest、S2RmiHttpEndToEndIntegrationTest、S2StatisticsApiIntegrationTest。

最终新增用例分布：

| 测试类 | 实际用例数 | 核心断言 |
|---|---:|---|
| `ReferenceCacheIsolationIntegrationTest` | 15 | 两种预热顺序、权限降级/恢复、同权限共享、无认证拒绝、并发隔离、故障回源、返回对象不污染 DB/缓存、历史 key 不读取、无事务缓存、空类型绕过；含小写 i 的 certificate_type 在土耳其 Locale 下仍命中同 key |
| `ReferenceCacheTransactionIntegrationTest` | 11 | 真实 Mapper 时事务 active/readOnly；独立写已提交后失效；外层提交/回滚；事务内字典/tree/children 不触碰 Redis；两类服务各四种写操作提交失效；版本冲突；KEYS/DELETE 故障不破坏已提交数据库 |
| `StatisticsScopeCacheIntegrationTest` | 13 | SELF、REGION、子树变更、部门、ALL/受限和同用户范围变化；真实 overview/charts JSON 命中；故障重新执行 scope；缓存启停结果一致；真实加载链同 JWT 空/不存在/停用区划拒绝且不读缓存 |
| `CoreTransactionBoundaryIntegrationTest` | 14 | 独立锁查询与自调用；迁入迁出全链同连接写事务、写后读、版本/重复执行、晚期日志失败和 hook 失败实际多表回滚；32 个写入口的静态合同另行标明；S3 实际 RN helper 挂起/恢复外层 RO 事务 |

核心事务 14 项中，12 项为真实 HTTP/H2/Mapper 业务链，1 项为事务属性静态检查，1 项为实际 S3 私有 TransactionTemplate helper 的事务传播实验。后者不是完整 S3 业务端到端；AI 确认与 S3 业务流另由已有集成回归证明。不能把这 14 项全部称为所有业务链的动态覆盖。

复现和最终专项命令：

```powershell
.\mvnw.cmd '-Dtest=ReferenceCacheIsolationIntegrationTest#administratorThenViewerNeverLeaksDisabled+viewerThenAdministratorKeepsCompleteResults,ReferenceCacheTransactionIntegrationTest#coldWriteTransactionCannotPublishUncommittedValueOnRollback' test
.\mvnw.cmd '-Dtest=ReferenceCacheIsolationIntegrationTest,ReferenceCacheTransactionIntegrationTest,StatisticsScopeCacheIntegrationTest,CoreTransactionBoundaryIntegrationTest' test
.\mvnw.cmd test
```

第一条的 RED 证据来自源码修复前，修复后重跑应为 PASS。日志保留在 worktree 的 `s5a2-baseline.log`、`target/s5a2-red.log`、`target/s5a2-specialist.log`、`target/s5a2-core.log`、`target/s5a2-specialist-final.log`、`target/s5a2-full.log`，XML 在 `target/surefire-reports/`；日志和构建产物不提交。

### S2 独立进程

本轮运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\s2-demo.ps1`，**退出码 0 / PASS**，2026-11-06 17:21:16 +08:00 完成末项断言。脚本启动前确认 1199/1200/8091 没有监听，只管理本次启动的独立 JVM，没有关闭、杀死或修改既有服务。

- RMI：回环 registry/object 端口、stub/traceId、成功调用、停止后 REMOTE_UNAVAILABLE、延迟触发 REMOTE_TIMEOUT 均符合预期。
- SOAP：回环 8091、WSDL HTTP 200、service QName 日志、成功响应与 traceId、负数参数 SOAP Fault、停止后 unavailable 均符合预期。
- 汇总日志：`target/s5a2-s2-demo.log`；各客户端输出、服务端日志和 PID 证据：`target/s2-demo-logs/20261106-171934-505/`。
- 这是本机 SYNTHETIC_DEMO 跨进程传输验证，独立于 Maven 的 730 项，不代表真实业务数据、MySQL、Python、Broker 或双实例联调。

## 4. 变更文件清单

| 文件（仓库相对路径） | 变更与归属 |
|---|---|
| `src/main/java/com/wjx871/population/cache/ReferenceCacheAspect.java` | S5-A2 权限分区、Locale、认证检查、活动 RW 绕过、切面顺序 |
| `src/main/java/com/wjx871/population/household/HouseholdService.java` | 仅 member 的事务属性，保留行锁与权限 |
| `src/test/java/com/wjx871/population/reference/ReferenceCacheIsolationIntegrationTest.java` | 新增字典真实 AOP 隔离验证 |
| `src/test/java/com/wjx871/population/reference/ReferenceCacheTransactionIntegrationTest.java` | 新增缓存提交/回滚与真实 advice 顺序验证 |
| `src/test/java/com/wjx871/population/stats/StatisticsScopeCacheIntegrationTest.java` | 新增真实 SQL/JSON 和多维 DataScope 验证 |
| `src/test/java/com/wjx871/population/transaction/CoreTransactionBoundaryIntegrationTest.java` | 新增核心业务事务、物理 SQL 观察和实际回滚验证 |
| `src/test/java/com/wjx871/population/support/ControllableRedisTestConfiguration.java` | 本地可控 Redis 传输夹具与操作计数/事务观察 |
| `doc/course/s5/s5a2-transaction-cache-boundaries.md` | 业务/Mapper 路由矩阵、缓存限制与联调清单 |
| `doc/course/s5/s5a2-verification.md` | 本报告及实际证据 |

## 5. Git 保护、剩余限制与交接

保护分支 SHA：integration `3e6c87ed4f2441b974b3600ef7f13af06932a89d`；远端 main `b54579b6b7f8b1feeda60f43906da05fb2ffda99`；S3 `758014cbcccdfe820efb2a6fae73bc3168030484`；Snapshot `d6b0b87b74af7a413214d7da9dd30b075cec882b`。施工开始时远端均符合；提交/推送前后再次核验。

用户已明确本地 main 保留既有 `d7fc1192967a13c7d80d2b60fdc12749fd5218df`，不将其调整到 origin/main。原工作区 `.gitignore` 修改保留，其施工中核对的文件 SHA256 为 `8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`。原工作区、integration、S3 及其 worktree 不施工、不清理。

业务/Mapper 全矩阵与赵书亚联调清单见 [事务与缓存边界](s5a2-transaction-cache-boundaries.md)。矩阵是静态调用关系和路由建议；部分 Java 事务/业务一致性已有 H2 动态证据。没有生产路由器，没有部署主从，没有物理 MySQL 路由验收。

Redis 的 KEYS 性能、失效失败后的 TTL 残留、旧查询跨实例回填旧值、复制延迟与断连恢复仍需赵书亚协同验证；普通统计缓存仍按现有 scope key 与 TTL 工作，没有重构主动失效系统。普通缓存降级不能替代 JWT strict 撤销失败关闭语义。本轮未扩大修复既有同步 OCR 事务疑点。

| 阶段 / 审计 | 状态 |
|---|---|
| S5-A2 Java | CODE_CANDIDATE，等待 ChatGPT 独立复核 |
| S5-A2 H2 / Redis Stub | PASS；新增专项 53，全量 730，均 0 failures/errors/skipped |
| S5-A2 真实 MySQL 主从 | NOT_VERIFIED |
| S5-B Redis/Nginx 双实例 | NOT_VERIFIED |
| 真实 Python / RabbitMQ / MySQL / 前端联调 | 各自 NOT_VERIFIED |
| #64 | 既有 Java 代码审查结论保留；真实 Redis/Nginx 双实例 NOT_VERIFIED，整体 OPEN |
| #3 / #33 | OPEN |
| #21 | FIXED_VERIFIED |

仅推送指定 feature，不创建 PR、不合并、不删除工作区，不进入 S5-B 或 S6。
