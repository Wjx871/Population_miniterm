# 2026-11-01 邑循后端遗留 Bug 回归报告

## A. 项目基线

| 项目 | 结果 |
|---|---|
| 仓库 | `Wjx871/Population_miniterm` |
| 基线提交 | `caf3f9aabd89467df78bf3dbb0eb1ad6f5b0ddbc` (`origin/main`) |
| 测试分支 | `test/20261101-backend-deep-regression` |
| 工作区 | 独立 worktree；原 `main` 的已跟踪文件未修改 |
| Java | 项目编译目标 Java 17；本机运行时 Java 21.0.7 |
| Maven | Wrapper 3.9.16 |
| Spring Boot | 3.5.3 |
| 测试数据库 | H2，`MODE=MySQL`，随机内存库；测试没有连接课程库 |
| MySQL 客户端 | 8.4.8；服务端版本未能验证 |
| OCR | 本地 Mock HTTP stub；未访问外部 OCR 服务 |

本次仅修改 `src/test/java/` 和 `doc/testing/`。未修改 `src/main/`、正式 SQL、前端或运维文件。测试数据使用合成值。

原 `main` 工作区最初发现的未跟踪素材（含 `素材/`、`小学期/`、`ocr识别/`、`convert_*.py`、`temp_table.txt` 和 civicpulse 图片）均已逐项确认仍在磁盘。当前本地 `.git/info/exclude` 含有这些路径的忽略规则，因此它们不再显示在 `git status`；该本地排除文件未纳入本次提交，素材没有被删除、移动或提交。

## B. 四轮测试结果

### 第一轮：现有基线

命令：`.\mvnw.cmd clean test`

| 总数 | 通过 | 失败 | Errors | Skipped | 用时 | 状态 |
|---:|---:|---:|---:|---:|---:|---|
| 371 | 368 | 3 | 0 | 0 | 1 分 50 秒 | FAIL |

编译了 246 个生产源码文件及原有 31 个测试源码文件，Spring Context、H2、Bean 和 Mapper 均可加载。三个失败是：

1. `AuthRbacIntegrationTest.meReturnsRoleScopeDepartmentAndPermissions`：断言 `DEPARTMENT`，H2 实际返回 `ALL`。
2. `AuthRbacIntegrationTest.departmentScopedLogQueryDoesNotExposeOtherDepartmentLogs`：viewer 实际看到了 population 的操作日志。
3. `Phase06ExportAuditIntegrationTest.otherNormalUserCannotDownload`：断言 403，实际为 200。

以上三项随后各自单独重跑，均稳定复现。前两项在核对 V4_012 后判定为旧断言；第三项保留为待处理缺陷候选。

### 第二轮：核心业务深度回归及 OCR 补测

新增 4 个 H2 集成测试，使用受控 HTTP stub：OCR 成功字段持久化、OCR 服务不可达、OCR 返回非法结构，以及 viewer 读取 OCR 影印本详情时的字段脱敏。

| 执行 | 用例数 | 通过 | 失败 | Errors | Skipped | 状态 |
|---|---:|---:|---:|---:|---:|---|
| `-Dtest=AuthRbacIntegrationTest` | 16 | 16 | 0 | 0 | 0 | PASS |
| `-Dtest=Phase14PersonIdCardOcrIntegrationTest` | 4 | 3 | 1 | 0 | 0 | FAIL |

OCR 的成功、不可达、非法响应三项均通过；viewer 读取影印本详情的脱敏用例失败，证据归入缺陷登记。新增测试 4 项。原有 household、迁移、审批、注销、流动人口/居住证、重点人口、导出与统计测试均保留，没有删除或跳过。

### 第三轮：安全、事务与并发回归

基线中的三个失败方法分别单独运行，结果均为 1/1 FAIL，稳定重现。最终全量 H2 回归覆盖已有迁移、注销、流动人口、重点人口事务回滚及重点人口双线程并发门禁；这些测试均通过。仍失败的安全场景为跨用户普通导出下载和 OCR 详情敏感字段泄露。

| 范围 | 结果 | 状态 |
|---|---|---|
| viewer 全量只读范围 | 按 V4_012 更新两条过期断言；16 项认证/权限类测试通过 | PASS（H2） |
| 事务故障注入与回滚 | 现有回归用例通过；日志中的预期 `rollback` 异常由故障注入触发 | PASS（H2） |
| 并发幂等 | 现有重点人口双线程测试通过 | PASS（H2） |
| 导出文件归属 | viewer 下载其他用户的普通脱敏导出返回 200 | FAIL / 疑似缺陷 |
| OCR 详情脱敏 | viewer 读取到完整 OCR 身份证号和地址 | FAIL / P0 |

未连接真实 Redis。Redis 可用、不可达和缓存隔离的运行时回归为 NOT RUN。

### 第四轮：真实 MySQL 回归

执行状态：**BLOCKED / NOT RUN**。审计了 `scripts/verify-mysql.ps1` 和 `scripts/windows/init_database.ps1`，没有运行初始化、迁移或重置脚本。`verify-mysql.ps1` 的 `-ResetTestDatabase` 会 DROP 指定数据库；本次未启用该开关。`init_database.ps1 -Mode Fresh` 有目标库存在性检查，但当前没有可用连接凭据。

本机 `MySQL84` 服务使用默认 ProgramData 数据目录；进程没有 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，仓库也没有 `.env`。仅用无密码 root 做只读版本查询得到 `ERROR 1045 (28000): Access denied`。因此未连接业务数据库、未创建数据库、未执行 SQL；MySQL 服务端版本、Mapper/XML 兼容性、真实事务和约束均未验证。

静态配置发现：V4_012 将 `QUERY_VIEWER` 更新为 `ALL`，README 和 H2 fixture 也为 `ALL`；但 `doc/database/population_miniterm.sql` 后段仍把 viewer 写回 `DEPARTMENT`，而 `init_database.ps1 -Mode Fresh` 只执行主 SQL，不执行 V4_012。fresh MySQL 环境可能与当前契约不一致，因 MySQL 运行验证 BLOCKED，登记为疑似初始化缺陷。

### 最终全量回归

命令：`.\mvnw.cmd test`

| 测试类 | 测试数 | 通过 | 失败 | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| 全部 32 个测试类 | 375 | 373 | 2 | 0 | 0 |

Maven 总用时 1 分 25 秒；Surefire 各测试类累计 68.96 秒。剩余两项失败是下方 `BUG-20261101-01` 和 `BUG-20261101-02`。状态 **FAIL**，未以改动生产断言或弱化业务检查来消除失败。

## C. 测试覆盖情况

| 模块 | 本轮证据与状态 | 主要风险/缺口 |
|---|---|---|
| 认证、JWT、RBAC、审计 | `AuthRbacIntegrationTest` 16/16 PASS；viewer 范围按 V4_012 的 ALL 契约更新 | 权限矩阵和旧端到端报告仍描述旧范围 |
| 人口、身份证校验、家庭户 | Phase 08 人员/家庭户集成与身份证校验用例 PASS | MySQL 约束和 SQL 未验证 |
| 影印本/OCR | 新增 4 项；成功、失败降级、非法结构 PASS | viewer 详情接口暴露 OCR 敏感字段，FAIL |
| 审批与显式执行 | Phase 02、Phase 12 等集成用例 PASS | MySQL 状态转换和并发未验证 |
| 迁入迁出、注销 | Phase 03/04 故障注入与回滚用例 PASS | MySQL 真实事务未验证 |
| 流动人口、居住证 | Phase 05 生命周期及回滚用例 PASS | 真实调度/Redis 未运行 |
| 重点人口 | Phase 10 状态机、幂等、并发和回滚用例 PASS | MySQL 并发未验证 |
| 导出、下载、审计 | 大部分普通/敏感导出用例 PASS | 非所有者下载普通导出返回 200，FAIL/待确认策略 |
| Redis 降级 | 未连真实 Redis | 不可达、TTL、缓存隔离未做本轮运行验证 |
| 真实 MySQL | BLOCKED / NOT RUN | 需可访问的独立测试库和凭据 |

## D. 缺陷汇总

- 确认缺陷：1 项 P0（OCR 影印本详情接口返回未脱敏敏感字段）。
- 疑似缺陷：2 项 P2（普通导出文件跨用户下载；Fresh 初始化 viewer 范围和 V4_012 不一致）。
- 测试自身问题：原认证测试仍断言迁移前 viewer=DEPARTMENT，已根据 V4_012、README、H2 fixture 和 Phase 08 行为更新为 ALL；没有删测试或跳过测试。
- 环境问题：真实 MySQL BLOCKED；无数据库测试凭据且无法证明当前服务为独立测试实例。
- viewer=ALL 是现有设计契约，来自 V4_012 明确说明“只读查询与统计、全量数据、写接口仍受权限控制”。`doc/security/backend-permission-matrix.md` 与旧 `end-to-end-regression-report.md` 的范围描述已过期；fresh 主 SQL 仍需单独核对。

## E. 最终质量评估

**尚不具备直接进入全量人工测试和后续分布式改造的充分质量条件。** H2 的主要业务状态机、事务回滚和并发门禁表现稳定，但存在 P0 级的 OCR 影印本详情敏感字段暴露；普通导出文件归属规则也需确认并处理。真实 MySQL 回归仍为 BLOCKED，当前结论仅代表 H2 测试环境。

建议顺序：

1. 先修复或收紧 OCR 影印本详情读取的字段脱敏/权限，并保留本轮失败测试作为回归门禁。
2. 明确 viewer（ALL）是否可下载其他用户的普通脱敏导出；若所有权限制有效，分离“全量数据范围”和“系统/范围管理员”下载权限。
3. 修正并验证 fresh 初始化 SQL 与 V4_012 的 viewer 范围一致性。
4. 获取隔离 MySQL 8 测试库访问后，完成初始化、Mapper、约束、事务、并发和完整回归。

## 文件清单

- `src/test/java/com/wjx871/population/auth/AuthRbacIntegrationTest.java`：修正两条迁移前 viewer 范围断言。
- `src/test/java/com/wjx871/population/person/idcard/Phase14PersonIdCardOcrIntegrationTest.java`：新增 4 项 OCR/脱敏集成测试。
- `doc/testing/2026-11-01-backend-regression-report.md`：本报告。
- `doc/testing/2026-11-01-backend-defect-register.md`：逐项缺陷与复现记录。
