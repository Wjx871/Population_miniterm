# 第二批业务闭环：无户主家庭户最后成员迁出

## 基线与缺陷复现

- 复核基线：`e2338c1708efb270ccbc52ff6c13dcafb41915d6`
- 施工分支：`fix/20261102-round2-headless-household-closure`
- 在 `Phase03MigrationIntegrationTest` 中，通过真实 API 流程创建空户主 ACTIVE 家庭户、迁入合成人员并完成材料核验/审批/执行，再完成该唯一普通成员的迁出。
- 修复前，迁出执行返回成功，但家庭户仍为 `ACTIVE`；提交合法的家庭户销户申请返回 `409`。失败用例：`finalOrdinaryMemberOutOfHeadlessHouseholdAllowsHouseholdCancellation`。

## 根因与修改

`MigrationService.executeOut()` 原先只在迁出者等于 `head_person_id` 时处理最后成员。无户主家庭户最后一名普通成员离开时，成员和有效户籍被正常移除，但没有将家庭户转为 `PENDING_CANCELLATION`。原 `markHouseholdPending` 也只支持匹配非空旧户主指针。

迁出事务现在以锁定家庭户快照识别唯一有效成员，在写入前校验户主指针与有效 HEAD 关系。迁出后，使用期望版本、ACTIVE 状态和期望户主指针（无户主时要求 `IS NULL`）条件更新家庭户。更新还要求不存在有效成员、有效户籍或状态为 `DRAFT`、`UNDER_REVIEW`、`APPROVED` 的迁入申请；条件更新未命中则抛出冲突，事务完整回滚。

迁入申请创建、草稿改址和迁入执行也锁定目标家庭户行并重新确认 ACTIVE 状态，使这些入口与最后成员关闭操作串行。无需更改 Mapper 接口：`MigrationMapper.markHouseholdPending` 已使用可空 `Long oldHead`，能够表达无户主预期值。

## 验证结果

- 新增回归覆盖：完整无户主单成员迁入/迁出及后续销户提交；还有其他成员时保持 ACTIVE；残留有效户籍或 DRAFT 迁入时拒绝关闭并完整回滚；家庭户版本冲突时归档、户籍、成员、家庭户版本/状态及申请状态全部回滚。
- 既有户主最后迁出、户主与继任者交接和普通成员迁出回归通过。
- Phase03 定向测试：30 通过，0 失败，0 错误，0 跳过。
- `mvnw.cmd clean test`：439 通过，0 失败，0 错误，0 跳过；BUILD SUCCESS。
- 测试使用项目隔离 H2 数据库和合成数据，未连接真实人口数据库。
- `git diff --check` 通过。

## 范围与限制

- 未更改数据库 Schema、API DTO、前端或审批历史。
- 实际 MySQL 未验证。H2 MySQL 模式测试通过，但不能替代目标 MySQL 版本上的相关锁和条件更新集成验证。

## 修改文件

- `src/main/java/com/wjx871/population/migration/MigrationService.java`
- `src/main/resources/mapper/MigrationMapper.xml`
- `src/test/java/com/wjx871/population/migration/Phase03MigrationIntegrationTest.java`
- `doc/testing/2026-11-02-round2-headless-household-fix.md`
