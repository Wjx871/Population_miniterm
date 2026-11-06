# S5-A1 实测与交付记录

日期：2026-11-06（Asia/Shanghai）。本轮代码与本报告位于同一施工提交；完整提交 SHA 和推送后远端复核结果见交付消息，亦可通过 `git rev-parse HEAD` 定位。

```text
施工分支：feat/20261106-s5a1-token-revocation-consistency
worktree：C:\Users\jxgm\.codex\worktrees\s5a1-token-revocation-consistency\小学期
基线：16da441e99aa7827a7da2169f85cfa38a311a3e8
```

## 1. 实现结论

原撤销读取把 Redis 故障解释为 false，写入失败又可能因本地 Map 成功而报告退出成功。本轮为撤销新增严格 Redis 接口，正常布尔值只表示本次存在/不存在，异常和未确认结果抛出受控故障；写入使用原子 SET/PXAT 并要求确认。

默认 `SINGLE_INSTANCE_LOCAL` 保持本地开发兼容，普通缓存仍允许回源。`SHARED_REDIS_REQUIRED` 每次查询共享 Redis，不使用本地状态代替共享状态；已撤销 401，无法确认 503，稳定标识 `REVOCATION_STORE_UNAVAILABLE`。退出只在共享写确认后记录 LOGOUT SUCCESS。Filter 拒绝前清理上下文，不进入业务读写入口。

生产通过 `prod`/`production` profile 或显式生产标志识别，本地模式默认阻止启动；单实例例外必须有开关及审计编号。正确密码、启用账号和角色验证后才进行随机独立命名空间的 5 秒登录预检；失败不更新 last_login、不记录 LOGIN_SUCCESS、不返回登录成功。

本地到期记录通过现有 Spring 调度与 businessClock 清理；锁保护有序 Map，默认每 30 秒检查最多 256 条，未过期项在同一锁内轮转，迭代器不跨调用保留。日志提供四类安全事件、traceId/instanceId/模式/类别/耗时和限流计数，不输出键、JTI、JWT 或底层异常原文。

完整配置、TTL、故障恢复与部署约束见同目录 `s5a1-token-revocation-consistency.md`。

## 2. 实际测试结果

本机 Java 21.0.7，项目编译目标 Java 17，Spring Boot 3.5.3。使用 Maven Wrapper；测试数据库为隔离 H2。Redis 网络层使用 Mockito，未向团队 Redis 写入数据，未连接生产数据库。

下表是逐轮日志中的实际总数；每轮均 0 failures、0 errors、0 skipped，BUILD SUCCESS。

| 轮次 | 实际项数 | 日志（worktree 内，不提交） |
| --- | ---: | --- |
| 新增严格适配器、生产配置门禁、安全日志 | 42 | `target/s5a1/unit-final.log` |
| 新增两实例模拟、本地清理及预检 | 18 | `target/s5a1/consistency.log` |
| 新增真实 Filter/Logout/Login HTTP 集成 | 23 | `target/s5a1/http-final.log` |
| Phase11 原有撤销测试 | 6 | `target/s5a1/phase11.log` |
| 既有 Auth/Security 与普通 Redis 缓存 | 27 | `target/s5a1/auth-security.log` |
| S4-C 政策问答 | 30 | `target/s5a1/s4c.log` |
| S4-B 确认草稿与材料绑定 | 21 | `target/s5a1/s4b.log` |
| S3/S4-A 累计预审测试 | 38 | `target/s5a1/s3.log` |
| S2 远程统计/RMI/HTTP | 19 | `target/s5a1/s2.log` |
| 完整 `mvnw.cmd test` | **677** | `target/s5a1/full.log` |

完整回归于 2026-11-06 13:00:36 +08:00 完成，耗时 2 分 26 秒；较 594 基线新增 **83** 项（13 适配器 + 27 配置 + 2 日志 + 18 一致性 + 23 HTTP）。原 Phase11 六项断言保留，仅适配依赖构造。

开发过程曾遇到测试日志级别未设 WARN、AssertJ 方法参数不匹配、Mockito 对已抛异常桩重新配置时触发旧异常；均修正测试写法并完整重跑通过，没有删除测试、降低断言或跳过用例。早期日志保留在本 worktree 的 target 中。

JaCoCo 日志提示缺少 execution data，未生成本轮覆盖率证据；不据此声称覆盖率结果。默认 `test` 不运行单独的真实 MySQL `*IT` 门禁，0 skipped 不代表 MySQL 联调已执行。S2 定向命令不是新的 SOAP 跨进程部署证据。

## 3. 关键验证证据

- 两个 TokenRevocationService 与两个模拟 Redis 客户端完全独立，仅共享模拟 Redis 数据；两边本地 Map 均为空。A 撤销后 B 立即拒绝；测试也证明严格模式每次读取都访问共享存储。
- 读/写故障、超时、权限异常、null/false 未确认结果不会被解释成读取缺失或写入成功。恢复完整模拟数据后可以重新确认撤销。
- JTI 与绝对到期时间隔离；过期边界正确。并发读在撤销确认后没有错误放行。本地批量清理、有效项公平轮转、并发清理/查询/写入、重复 JTI 较晚期限均验证。
- MockMvc 请求经过真实 SecurityConfig、JwtAuthenticationFilter、AuthService 和 OptionalRedisService，模拟底层 RedisConnection 并执行真实 RedisCallback。
- 已撤销 JWT 无法读取敏感人员详情或修改人员数据；共享查询故障时读写均 503，业务表和成功审计不变；恢复后相同写入口可正常执行。
- 严格退出写失败及鉴权读取失败均无 LOGOUT SUCCESS；写成功后原 Token 持续 401。无 Token/无效 Token 仍 401，公开健康检查边界不变，异常请求结束后 SecurityContext 无认证残留。
- 登录预检位于正确密码、账号和角色检查之后；写失败、读取缺失或超时不产生成功响应/last_login/LOGIN_SUCCESS。键为随机 UUID、独立命名空间、5 秒绝对到期；可控时钟证明过期后淘汰，且不占本地 Map、不包含原始 JWT。
- 配置绑定、严格 Redis 未开启、生产 profile/标志、审计例外及非法配置均测试；production=false 不能绕过生产 profile。
- 日志验证 cause、键/JTI、合成凭据不输出，无附加异常堆栈；同类故障限流但全部计数；非法 trace 请求头被替换，MDC 作用域恢复。

复现命令（在上述 worktree 执行）：

```powershell
.\mvnw.cmd test "-Dtest=S5A1StrictRedisServiceTest,RevocationConfigurationTest,RevocationEventsTest"
.\mvnw.cmd test "-Dtest=TokenRevocationServiceConsistencyTest"
.\mvnw.cmd test "-Dtest=S5A1StrictRevocationHttpIntegrationTest"
.\mvnw.cmd test "-Dtest=Phase11TokenRevocationTest"
.\mvnw.cmd test "-Dtest=AuthRbacIntegrationTest,OptionalRedisServiceTest"
.\mvnw.cmd test "-Dtest=PolicyPythonAdapterIntegrationTest,PolicyPythonHttpStubIntegrationTest,PolicyAssistantServiceTest,PolicyWorkflowServiceTest"
.\mvnw.cmd test "-Dtest=AiConfirmIntegrationTest,ApplicationMaterialSubmitConcurrencyIntegrationTest,ApplicationMaterialFeatureOffIntegrationTest"
.\mvnw.cmd test "-Dtest=AiPrecheckIntegrationTest,DocumentAnalysisResponseValidatorTest"
.\mvnw.cmd test "-Dtest=S2StatisticsApiIntegrationTest,S2RmiHttpEndToEndIntegrationTest,S2RmiClientConcurrencyIntegrationTest,S2RemoteDisabledIntegrationTest,S2AggregationFacadeTest,RmiInvocationHandlerClassificationTest,PopulationMetricsCalculatorTest"
.\mvnw.cmd test
git diff --check
```

## 4. 提交范围与 Git 保护

本轮共 17 个文件，无数据库迁移、前端、Docker/Nginx、MySQL 路由、S3/S4 业务逻辑改动。

```text
doc/course/s5/s5a1-token-revocation-consistency.md
doc/course/s5/s5a1-verification.md
src/main/java/com/wjx871/population/auth/AuthService.java
src/main/java/com/wjx871/population/cache/OptionalRedisService.java
src/main/java/com/wjx871/population/cache/StrictRedisException.java
src/main/java/com/wjx871/population/security/JwtAuthenticationFilter.java
src/main/java/com/wjx871/population/security/RevocationEvents.java
src/main/java/com/wjx871/population/security/RevocationProperties.java
src/main/java/com/wjx871/population/security/RevocationStoreUnavailableException.java
src/main/java/com/wjx871/population/security/TokenRevocationService.java
src/main/resources/application.properties
src/test/java/com/wjx871/population/cache/S5A1StrictRedisServiceTest.java
src/test/java/com/wjx871/population/security/Phase11TokenRevocationTest.java
src/test/java/com/wjx871/population/security/RevocationConfigurationTest.java
src/test/java/com/wjx871/population/security/RevocationEventsTest.java
src/test/java/com/wjx871/population/security/S5A1StrictRevocationHttpIntegrationTest.java
src/test/java/com/wjx871/population/security/TokenRevocationServiceConsistencyTest.java
```

现有 `.gitignore` 的 `cache/` 规则会忽略新缓存包文件；仅对明确列出的 StrictRedisException 与其新测试使用精确路径 `git add -f`，不修改 `.gitignore`。此操作不涉及 force push。

开工和回归过程中通过 ls-remote 核验的远端保护值（提交/推送时再次核对，变化则停止）：

```text
integration/20261101-backend-p0-security = 16da441e99aa7827a7da2169f85cfa38a311a3e8
main = b54579b6b7f8b1feeda60f43906da05fb2ffda99
feat/20261103-s3-rabbitmq-async-precheck = 758014cbcccdfe820efb2a6fae73bc3168030484
snapshot/20261103-pre-course-upgrade-backend = d6b0b87b74af7a413214d7da9dd30b075cec882b
```

推送仅使用以下显式 refspec，不合并、不更新 integration/main/S3/Snapshot、不创建 PR、不强推。旧工作区 `.gitignore` 修改保持原状，S5-A1 worktree 和分支保留。

```powershell
git push origin HEAD:refs/heads/feat/20261106-s5a1-token-revocation-consistency
```

## 5. 验收边界及后续真实联调

```text
#64 Java：CODE_CANDIDATE，等待独立代码复核
#64 Mock：PASSED（新增 83 项；完整回归 677 项通过）
#64 真实 Redis/Nginx 双实例：NOT_VERIFIED；#64 不关闭
#3：OPEN
#33：OPEN
#21：FIXED_VERIFIED
真实 Python/RabbitMQ/MySQL/前端联调：保留原 NOT_VERIFIED
```

赵书亚联调必须使用相同 JWT_SECRET、共享 Redis 一致读写端点/database/key-prefix、双实例严格模式和可识别实例号；证明 Nginx 将请求分流到不同 JVM，测试跨实例退出传播、读写故障、断网及恢复。还须实际核验 Redis >=6.2、SET/PXAT/EXISTS 权限、时钟、服务端 TTL、预检键淘汰、noeviction、持久化和故障恢复流程。

Redis 返回 KEY_ABSENT 不能证明撤销历史完整。重启、故障切换、回滚、驱逐、FLUSH 和持久化丢失均可能丢记录。本轮没有全局数据丢失防护，也未引入数据库表或 MQ；生产必须有 noeviction、持久化/备份策略、恢复期间流量隔离和无法确认完整性时的旧会话失效方案。详细逐项测试清单见设计文档第 7、8 节，全部保留真实联调 NOT_VERIFIED。

施工完成后停止，等待 ChatGPT 独立复核，不进入 S5-A2 或 S6。
