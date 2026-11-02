# Population Miniterm 第四批后端审计修复报告

## 基线与交付范围

- 起始提交：`c6caa2d87038fd728772699a7af643931852c28a`，个人集成分支 `integration/20261101-backend-p0-security`。
- 共享 `main` 基线：`b54579b6b7f8b1feeda60f43906da05fb2ffda99`；本轮没有修改、合并或推送 `main`。
- 施工分支：`fix/20261102-core-audit-round4`，独立 worktree `D:\学习用\大二下\小学期-fix-20261102-core-audit-round4`，未跟踪 `origin/main`。
- 最后一个业务代码提交：`8d78575`。A、D、B、C2 分别提交；本报告与 86 项账本作为文档提交。最终分支 HEAD 见本轮交付消息。

## 四组结论

### A：REGION 新建人口记录无法读取 — 确认并修复

旧实现 H2 复现：REGION 创建人口返回 201 后，同区创建人读取详情返回 403、列表和普通导出计数为 0；异区被拒绝，ALL 可读。新增服务端持久化的初始用户、部门和区划归属，仅用于尚无 residence 的新人员；人员首次出现实际户籍后，初始归属不再扩大读取范围。审批后代为建档使用原申请人快照，而非执行人的权限。详情、列表和导出范围回归通过。

提交：`aff3f8e`（`fix: preserve region ownership for new person records`）。新增正式增量迁移 `V4_014_person_initial_scope.sql` 及回滚文件；**没有在课程库或共享数据库执行迁移**。API 路径及 DTO 未变。

### B：流动登记关闭与居住证签注死锁 — 确认并修复

在本轮专用 MySQL 8.4.8、`REPEATABLE-READ` 实例上复现旧锁环。Performance Schema 和 `SHOW ENGINE INNODB STATUS` 显示关闭连接 85 与签注连接 86 构成等待环：签注事务持有 `residence_permit` 主键排他锁、等待 `person` 主键；关闭事务持有 `person` 主键、等待同一 `residence_permit`。MySQL 记录 `1213 / SQLSTATE 40001`。当前已有 `GlobalExceptionHandler` 会将该明确死锁映射为 409，因此复现中没有观察到 HTTP 500；旧实现的死锁胜负顺序为关闭 409、签注 200。

修复后，创建签注和执行签注都先锁流动登记，再锁居住证；`lockPermit` 只锁居住证行，避免 JOIN 顺带取得 person/流动登记行锁。MySQL 关键竞争重复 10 轮：每轮关闭 200、签注因登记已关闭返回 409；等待方未持有居住证主键行锁，`1213` 计数增量为 0。每轮数据库状态、申请状态、成功/失败审计和签注日志均通过断言。

提交：`2bfa038`（`fix: align floating and permit transaction locks`）。未改 Schema 或 API。

### C1：重点人口解除材料 — 未复现，不修改

当前 API 文档明确解除材料规则与建档相同；前端建档和解除共用 `KEY_POPULATION_BASIS`、`SITUATION_DESCRIPTION` 必需材料定义及同一核验 helper；现有 Phase10 测试也要求两类申请都通过这两项材料。未发现解除材料契约不同的证据，因此保留生产校验，不新增绕过路径。

### C2：RETURNED 重复申请与并发创建 — 确认并修复

旧实现 H2 复现：建档与解除申请均处于 RETURNED 时，同一对象再次创建返回 201；两线程同时创建同人/同类型建档、同记录解除，各出现 `[201, 201]`。修复将 RETURNED 纳入在途计数；创建建档前锁定通过当前数据范围校验的人员行，创建解除前锁定通过数据范围校验的重点人口记录行，使计数检查与插入串行化。未加数据库约束或修改 Schema。

提交：`8d78575`（`fix: prevent duplicate key population applications`）。Phase10 定向测试 11 项通过，覆盖 DRAFT、SUBMITTED、UNDER_REVIEW、APPROVED、RETURNED，REJECTED/CANCELLED/WITHDRAWN/COMPLETED 终态可重新申请，真实退回申请流程，以及建档和解除两类并发创建。

### D：失效 Bearer Token 阻断公开登录 — 确认并修复

修复前，带失效/格式错误 Bearer 的公开登录请求会在认证过滤器中先被拒绝为 401。过滤器现只对精确公开登录路径跳过 Token 解析；受保护 API 仍拒绝过期、无效和撤销 Token。

提交：`0d84620`（`fix: allow login despite stale bearer token`）。认证定向回归覆盖无 Token、有效、过期、无效、撤销 Token 登录与受保护接口。

## 回归与 MySQL 证据

- H2 隔离库：`.\mvnw.cmd clean test`，**484 passed / 0 failed / 0 errors / 0 skipped**。上一基线为 475 项通过。本轮 H2 没有连接 MySQL。
- MySQL 专项：独立 loopback MySQL Community 8.4.8 实例，端口 13391，隔离测试库，隔离级别 `REPEATABLE-READ`；`MySql84CriticalAcceptanceIT` **9 tests，0 failures / 0 errors / 0 skipped**。涵盖原有 A–E 业务场景与本轮锁顺序测试；B 组重复 10 轮，迁入/最后成员迁出竞争也重复 10 轮。
- 故障注入事务回滚仍验证 `1644 / SQLSTATE 45000`；迁出/销户状态、归档、审批、审计与申请状态断言通过。MySQL 测试与 H2 数字分别统计。
- 本轮专用 MySQL 服务已停止，`127.0.0.1:13391` 无监听。新临时目录 `C:\Users\jxgm\AppData\Local\Temp\population_miniterm_round4_mysql_deadlock_20261102_a91f93b2` 保留在仓库外，没有尝试删除；前三轮旧 Temp 目录未访问、未修改。随机测试凭据只在隔离实例/进程环境使用，未复用到其他服务，未写入 Git，工作会话缓存已清除。

## API、Schema 与文件范围

- API 路径、请求 DTO、公开响应结构均未改变。
- 唯一 Schema 交付是 A 组新增的 V4_014 人员初始归属字段及其 rollback；迁移未在课程库/共享库执行。B、C2、D 不改正式 Schema。
- A 文件：`Person.java`、`PersonService.java`、`PersonMapper.xml`、`ExportMapper.xml`、`ApprovalService.java`、`V4_014_person_initial_scope.sql`、对应 rollback、主 Schema 快照、Verify/初始化清单与 H2 集成测试。
- B 文件：`FloatingResidenceService.java`、`FloatingResidenceTransactionHook.java`、`FloatingResidenceMapper.xml`、`Phase05FloatingResidenceIntegrationTest.java`、`MySql84CriticalAcceptanceIT.java`。
- C2 文件：`KeyPopulationService.java`、`KeyPopulationMapper.java`、`KeyPopulationTransactionHook.java`、`KeyPopulationMapper.xml`、`Phase10KeyPopulationGateIntegrationTest.java`。
- D 文件：`JwtAuthenticationFilter.java`、`AuthRbacIntegrationTest.java`。
- 本轮没有修改前端、运维业务代码或未授权迁移数据。

## 未完成项与限制

完整 #1–#86 逐项状态、证据和责任方见 [`2026-11-02-antigravity-audit-ledger.md`](2026-11-02-antigravity-audit-ledger.md)。仍需后续后端评估的高风险项包括 OCR 长事务/解析、户籍归档影响行数、注销 RETURNED 状态、区划与户籍区划一致性、同城跨区户籍判定、居住证在途防重及到期任务全量扫描、注销后重点人口 REGION 补正、重点人口通用提交权限、综合查询字段与范围、日志明细脱敏、XLSX 公式注入、行政区递归停用等。#41 与 #43 本轮只做静态复核，未动态利用；#21 已有目标家庭户行锁，但区划一致性仍未补齐。

MySQL 只验证本报告列明的隔离实例和测试路径，不代表完整生产 MySQL 验收。C2 的并发门禁在 H2 验证，未在 MySQL 上另跑该两类申请创建竞争。前端交接项仍由前端组处理。
