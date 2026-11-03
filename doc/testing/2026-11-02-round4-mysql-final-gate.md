# Population_miniterm 第四批最终 MySQL 验收补充

MySQL 验收执行日：2026-11-02；H2 最终全量回归：2026-11-03。

## 基线与隔离

- 施工起点：5eba948da077ff27b580271955690c0ad26033d0；施工分支为 test/20261102-round4-mysql-final-gate。
- 验收时核对的个人集成分支：c6caa2d87038fd728772699a7af643931852c28a；共享 main：b54579b6b7f8b1feeda60f43906da05fb2ffda99。
- MySQL Community 8.4.8，127.0.0.1:13392；测试数据库均为本轮随机命名的隔离库。未连接 3306，未读取或使用前三批测试凭据。
- 专用 mysqld 进程已停止，13392 无监听。系统现有 3306 服务未操作。
- 临时目录保留在仓库外：C:\Users\jxgm\AppData\Local\Temp\population_miniterm_r4_mysql_final_20261102_df4089129c。未尝试删除；目录包含隔离数据库文件、合成数据快照、SQL 副本和日志。没有将其内容或凭据加入 Git。

## A. V4_014 从 V4_013 的增量迁移

本次先从 c6caa2d87038fd728772699a7af643931852c28a 的旧 Schema 建库，再按顺序执行 V4_011、V4_012、V4_013。旧库有 34 张业务表，person 表中不存在 initial_scope_* 字段或相应索引；因此这不是含 V4_014 字段的 Fresh Schema 冒充旧库升级。

迁移前只写入合成数据：5 名人口（已有户籍、无户籍、不同 REGION、关联申请、已注销/迁出历史），并有关联家庭户、成员、户籍、申请及注销记录。迁移 SQL 中的 USE population_miniterm 已在临时副本中替换为本轮唯一隔离库名，并核对副本目标后才执行。

实际执行 V4_014 成功。检查确认字段 initial_scope_user_id、initial_scope_department_id、initial_scope_region_code 和索引 idx_person_initial_scope_user、idx_person_initial_scope_department、idx_person_initial_scope_region 全部存在；5 条旧人口记录的新字段均为 NULL；迁移前后人员及 residence、business_application、cancellation_record 记录保留。迁移完成后运行 scripts/windows/init_database.ps1 -Mode Verify，结果通过。MySql84V4014UpgradeAcceptanceIT 的 2 项真实 MySQL 测试通过：旧人员详情/列表授权有效，普通导出 row_count 与 API 列表总数一致；审批代建记录保留申请人用户、部门和 REGION 快照，申请人能够读取该记录。

回滚只在该可丢弃隔离库验证。执行前保存了合成字段快照，SHA-256 为 7CA1F55BEF59C2D82E6A6C11828D2D4B7504232FB7CDBB63075316E31FC87EF1。回滚后 3 个字段和 3 个索引均已移除；person、household、household_member、residence、business_application、cancellation_record、operation_log、key_population、data_export_log 行数与回滚前相同。快照明确保留了回滚会丢失的归属字段值。随后 V4_014 再次执行成功。

**验证限制：**正式 Verify 已在第一次实际 V4_014 升级后通过；回滚测试后再次执行 V4_014 成功，但未在这次重放后再次运行 Verify。故“重放后的 Verify”记为 NOT_RETESTED，不宣称已通过。回滚只用于隔离测试库，没有对真实业务库执行。

## B. C2 重点人口申请并发与 REPEATABLE READ

隔离库使用 MySQL 默认 REPEATABLE-READ。MySql84KeyPopulationC2RepeatableReadIT 共 4 项测试通过：

- B1 同一人员并发建档：10/10 轮均为一个 201、一个 409；每轮使用不同 JDBC 连接，观察到 MySQL 行锁等待。最终各只有 1 条专业申请、1 条业务申请和 1 条成功创建审计；没有 HTTP 500、SQLSTATE 或 MySQL 错误。
- B2 同一重点人口记录并发解除：10/10 轮均为一个 201、一个 409；不同连接及行锁等待均有记录。每轮只留下 1 条专业申请、1 条业务申请和 1 条成功创建审计。
- B3 对已有 RETURNED 建档或解除申请再次创建：两类请求均返回 409；业务申请、专业申请和审计数没有增加。
- B4/B5 两个显式独立 JDBC 事务构造锁等待前的一致性读。REPEATABLE-READ 对照中，等待前 COUNT=0，锁等待结束后仍读到 0，而已提交行数为 1；READ-COMMITTED 对照中等待后 COUNT=1。两组均确认不同连接并观察到锁等待。

RR/RC 对照验证了 InnoDB 旧 Read View 的行为边界，但它在竞争事务中人为先做普通一致性读，再等待锁。生产服务路径先调用 FOR UPDATE 范围锁再做在途计数；本轮真实 API 的 B1/B2 竞争均串行化，未复现双 201 或重复在途申请。因此没有生产代码缺陷复现，也未改生产隔离级别或申请逻辑。MySQL 仍保持 REPEATABLE-READ。

## 回归数字与覆盖

- H2 全量：2026-11-03 最终执行 .\mvnw.cmd clean test，484 passed / 0 failed / 0 errors / 0 skipped。
- 本次 MySQL 独立专项入口：16 passed / 0 failed / 0 errors / 0 skipped。构成为 MySql84CriticalAcceptanceIT 9 项、MySql84V4014UpgradeAcceptanceIT 2 项、MySql84KeyPopulationC2RepeatableReadIT 4 项、MySQL 模式 PersonRegionOwnershipAuditIntegrationTest 1 项。
- MySql84CriticalAcceptanceIT 的 9 项包含既有事务、故障回滚和迁入/最后成员迁出门禁；先前专项结果中关键并发门禁均维持数据库不变量。
- 新增/修改均为测试或报告文件。本次没有生产 Java 代码、正式 Schema、API DTO 或接口变更。V4_014 仅作为第四批既有迁移，在本轮隔离 MySQL 上实测。
- 验收时个人集成分支核对为 c6caa2d87038fd728772699a7af643931852c28a，main 为 b54579b6b7f8b1feeda60f43906da05fb2ffda99；本轮未修改这两个分支。

## 测试及环境限制

- MySQL 证据限于本轮 MySQL 8.4.8 隔离实例和以上合成场景，不代表所有生产数据规模、并发计划或部署拓扑均已验收。
- H2 与 MySQL 结果分开计数；MySQL 专项没有用 H2 模式替代。
- 第四批 86 项账本仍有标为 OPEN、FRONTEND_HANDOFF、NOT_REPRODUCED 或 NOT_TESTED 的事项；本补充仅更新 #2 与 #44 的证据，不扩大审计范围。
- 最终 V4_014 重放后的 Verify 尚未复跑，需在专用隔离 MySQL 库上补一次才可将该子项标为 PASS。
