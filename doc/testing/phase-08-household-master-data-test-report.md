# Phase 08 测试报告

## 自动化范围

- 既有 147 项不删除、不跳过；`ResidentMapperTest` 的 5 个旧 CRUD 用例改为同数量的 legacy 退役守卫。
- 新增 `IdCardValidatorTest` 10 项、`Phase08HouseholdMasterDataIntegrationTest` 15 项、`Phase08PersonIdentityIntegrationTest` 5 项。
- 迁移、注销、审批、材料、流动人口、居住证和导出回归测试全部保留。
- 验收前基线：Tests run **177**，Failures **0**，Errors **0**，Skipped **0**，BUILD SUCCESS。
- 验收完成后再次执行 `mvnw.cmd clean test`：Tests run **177**，Failures **0**，Errors **0**，Skipped **0**，BUILD SUCCESS，47.824 s。

## 真实 MySQL 环境

- 服务端：MySQL Community Server **8.4.10**（Windows x86_64，隔离临时实例）。
- 客户端：`%TEMP%\codex-phase08-mysql-8.4.10\bin\mysql.exe`，版本 **8.4.10**。本机 `E:\MySQL\bin\mysql.exe` 为 9.4.0，未作为 MySQL 8 验收依据。
- 服务地址：`127.0.0.1:3410`。
- 临时数据库：`population_miniterm_phase08_verify`；未连接或修改用户现有业务库。
- 凭据、JWT、上传目录和导出目录只在当前进程或系统临时目录中使用，未写入仓库、脚本或报告。

## 场景 A：全新初始化

当前 `population_miniterm.sql`、`demo_data.sql`、`demo_data_household_migration.sql` 依次导入成功，无 unknown column、重复键、外键、CHECK 或缺表错误。演示数据自检 18 项的异常数均为 0。

- 表：30；person：13；household：7；ACTIVE household_member：11；residence：10。
- legacy `residents` 表存在，但生产代码、Mapper 和 API 对其零依赖。
- 三个 Phase 08 查询索引均存在，身份数据通过当前 GB 11643 校验及自动化覆盖。

## 场景 B：V4_007 升级

使用提交 `0379a44` 中真实的 V4_006 前置主脚本与两份演示数据建立历史状态，再连续执行当前 `V4_007_household_master_data.sql` **3 次**，三次均成功。

- 升级前后数量一致：person 13、household 7、household_member 11、residence 10、migration_in 0、migration_out 0、cancellation_record 0、floating_population 1、residence_permit 1、residents 1、sys_permission 55、sys_role_permission 159。
- 无重复列、重复索引、重复权限或数据丢失；3 个 Phase 08 索引各保留一份，临时存储过程执行后为 0。
- legacy `residents`、person、household、household_member 及既有迁移/注销/流动人口/居住证数据均保留。

## MySQL 与 H2 结构核对

已逐列核对 person、household、household_member、residence、migration_in、migration_out、cancellation_record、business_application、operation_log，并核对家庭户与成员索引。`household.version/region_code/household_type/status`、`household_member.version/status/relationship/leave_date`、`person.current_status_code` 及三个 Phase 08 查询索引的名称、长度、可空性和业务语义一致，无阻断差异。

MySQL 保留主脚本中的基础外键/唯一索引，H2 测试结构以 `ALTER TABLE` 逐阶段补齐字段；时间类型分别为 DATETIME/TIMESTAMP，文本分别为 TEXT/CLOB，属于方言差异。ACTIVE 成员唯一性和 HEAD 一致性不是依赖条件唯一索引，而由事务锁、冲突校验、乐观锁和整体回滚保证；真实 MySQL API 与一致性 SQL 已验证该策略。

## 应用与 API 烟测

Spring Boot 连接临时库成功，Mapper XML 和 Bean 均正常加载，无 residents Mapper、缺表、缺列或 SQL 方言错误。逐项结果：

- 鉴权和查询：`/api/auth/me`、人员、家庭户分页/详情/成员均为 200；ALL 用户可看全量，REGION 用户看不到外区数据。
- 写操作：创建家庭户与添加成员为 201；更新家庭户、更新成员、变更户主、原户主变更后离户均为 200。
- 校验与冲突：非法身份证、生日冲突、性别冲突为 400；重复身份证、重复户号、非成员设户主、HEAD 直接离户、过期 version 为 409。
- 权限：无 `household:edit` 的 viewer 写入为 403；REGION 户籍用户跨区写入为 403。
- 副作用：添加普通成员前后 residence、migration_in、migration_out 数量均未变化。
- 审计：本轮家庭户写操作生成 7 条日志；日志中未发现完整测试身份证或密码。

## 一致性与原业务回归

15 项一致性检查全部为 0：ACTIVE 成员重复、ACTIVE HEAD 重复、户主指针不一致、HEAD 非本户成员、MOVED_OUT 缺离户日期、已注销人员仍为 ACTIVE、已注销户仍有 ACTIVE 成员、孤立户主、身份证重复、非法演示身份证、成员维护产生 residence、成员维护产生 migration、写日志缺失、日志敏感信息、迁移申请非 DRAFT。

迁入和迁出专业入口在真实 MySQL 上均创建成功（201），申请保持 DRAFT；未审批迁出执行返回 409，未发生自动执行。注销、流动人口、居住证和导出查询均为 200；审批后显式执行、人员/家庭户注销及敏感导出状态机继续由既有自动化回归覆盖。

## 结论与清理

真实 MySQL 初始化、V4_007 幂等升级、结构核对、应用启动、API/权限/范围/一致性和原业务回归均通过，未发现需要修改 Phase 08 代码或数据库约束的缺陷。验收后已停止应用和隔离 MySQL，删除临时数据库、SQL、日志、上传/导出目录、数据目录与下载包；仓库中无凭据或运行产物。
