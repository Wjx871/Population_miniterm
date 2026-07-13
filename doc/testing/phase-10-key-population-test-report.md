# Phase 10 测试报告

## 自动化

- 基线：241 项，Failures 0，Errors 0，Skipped 0。
- 新增 `Phase10KeyPopulationIntegrationTest`：56 项，覆盖查询/范围/脱敏、专业入口、建档材料与审批、审批不落地、显式执行、幂等、解除、历史、审计和一致性。
- Phase 02 通用申请测试改用明确的 `GENERAL_SERVICE`，重点人口两种类型继续被通用入口拒绝。
- 最终全量：Tests run 297，Failures 0，Errors 0，Skipped 0，BUILD SUCCESS，约 62 s。

## 最终门禁补充

- 新增 `Phase10KeyPopulationGateIntegrationTest` 5 项，组合覆盖 viewer 认证/只读权限边界、建档双线程 execute、解除双线程 execute、建档中途异常回滚并重试、解除中途异常回滚并重试。
- 并发使用两个独立线程、`CountDownLatch` 同步起跑和独立 HTTP 请求，不使用固定 sleep；建档与解除均稳定得到一个 200、一个 409，当前记录、成功历史和成功执行日志均唯一。专项测试首次通过后又连续执行 3 次，均通过。
- 回滚注入点为 `KeyPopulationTransactionHook.afterHistoryInsert()`：此前已修改当前记录并插入历史。异常后当前记录/解除字段、历史、通用申请、专业申请和成功日志全部回滚；材料及审批证据保留。恢复 Mock 后第二次 execute 正常成功且只形成一套结果。
- `OperationLogService.recordTransactional` 使用 `MANDATORY`，重点人口成功执行日志参加主业务事务。
- 门禁完成后的最终全量：Tests run 302，Failures 0，Errors 0，Skipped 0，BUILD SUCCESS，约 73 s。

## MySQL 8

- MySQL Community Server 8.4.10，隔离地址 `127.0.0.1:3412`，临时库 `population_miniterm_phase10_verify`。
- 全新初始化及两份演示数据成功：33 张表，重点人口三表和 3 个权限存在。
- 从 Phase 09 merge `8ea1f21` 构造 V4_008 状态，V4_009 连续执行三次成功；legacy residents、既有权限和字典保留。
- admin/population/approver 登录成功；完成建档创建、材料准备、提交、审批、确认未自动落地、显式执行、查询记录/历史、解除创建/提交/审批、确认未自动解除、显式解除。
- 重复执行 409、approver 执行 403、跨区域创建 404、通用入口创建重点人口类型 400；写审计 4 条且无完整身份证。
- 12 项一致性检查异常数均为 0。应用、临时库、MySQL、安装包、数据/上传/导出目录在验收后清理。

## viewer 401 复核与 MySQL 并发

早期 viewer 401 为验收脚本假象：Windows PowerShell 默认管道编码把中文角色名写成问号，`QUERY_VIEWER` 与同长度角色名触发 `role_name` 唯一冲突，导致 viewer 未被种子插入。正式 SQL、BCrypt 哈希、认证代码和权限种子没有缺陷，因此未修改生产种子。显式设置 UTF-8 后从干净库重建，viewer 存在且状态 ENABLED、角色 QUERY_VIEWER、密码哈希长度 60、拥有 `key-population:view`；登录、`/api/auth/me`、重点人口列表均为 200，apply 和 execute 均为 403。

在 MySQL 8.4.10 临时库 `population_miniterm_phase10_gate_verify` 上通过两个独立后台进程同时请求建档 execute，HTTP 状态为 200 和 409；最终当前记录 1、REGISTERED 历史 1、成功执行日志 1，通用和专业申请均为 COMPLETED。V4_009 再连续执行三次成功，12 项一致性异常均为 0。
