# Population_miniterm 第二批核心业务缺陷修复验证报告

## 基线与交付

- 起始基线：`integration/20261101-backend-p0-security`，`4b1e61a30ba21c5b144a68184167e38fb8005d06`
- 施工分支：`fix/20261101-core-business-round2`
- 施工 worktree：`D:\学习用\大二下\小学期-fix-20261101-core-business-round2`
- 最终业务实现 SHA（D 组提交）：`a1840f2aee473f3e7cda8b0fa7d1bd1138b236bc`
- A–D 修复各自独立提交；本报告和前端交接清单作为后续 docs-only 提交。
- 基线全量回归：418 项通过，0 失败、0 错误、0 跳过。

## 四组缺陷结论

### A：户主迁出业务闭环与成员关系

**结论：确认并修复。**

- 旧实现的完整流程中，户主迁出且仍有其他成员、未指定新户主但提供同意材料时，提交返回成功并进入审批；批准后执行返回 409，因为执行请求没有补录户主的字段。旧实现不可能在执行阶段安全补全该审批缺口。
- 旧实现执行户主交接只更新 `household.head_person_id`，新户主 `household_member.relationship` 仍为 `CHILD`，形成主从表分裂。
- 修复在提交阶段要求新户主为本户其他有效成员；执行阶段保留二次校验。在同一事务中提升新户主成员关系、核对唯一 ACTIVE HEAD、再按家庭户版本更新指针；任一失败整体回滚。
- 修复前，缺少新户主的提交断言实际收到 200；成员关系断言确认指针已更新而关系仍为 CHILD。修复后 Phase03 定向测试 23 项通过，覆盖正常迁出、户主交接、失败回滚、版本冲突及重复并发执行。
- 遗留风险：已经处于 APPROVED 且缺少有效新户主的历史申请仍不能执行。本轮不自动选择成员、不改写审批记录，也未新增历史单据补正流程；需后续设计受控补正及重新审批路径。

提交：`eabae6a7439d0034b23fa09d9abc27846a0a0776`。

### B：家庭户关系和空户状态

**结论：确认并修复。**

- 旧实现允许对 `head_person_id=NULL` 的家庭户直接新增 `relationship=HEAD`，但不更新主表指针；第二条 HEAD 也可进入。`changeHead` 随后调用单行查询并触发 MyBatis `selectOne()` 找到两行，返回 500。
- 普通成员离户未校验日期先后；`join_date=2025-01-01`、`leave_date=2024-12-31` 的请求旧实现返回 200 并写入 MOVED_OUT。
- 无户主家庭户最后一名成员直接离户后旧实现仍为 ACTIVE，无法创建合法销户申请。即使家庭户还存在另一条 ACTIVE residence 或待迁入申请，旧实现仍会离户。
- 修复禁止普通添加成员直接指定 HEAD；首次指定户主仍可经 `changeHead` 完成。户主变更前检查 ACTIVE HEAD 数量与主表指针一致，遇到历史分裂数据返回 409，不再触发 500。离户日期不得早于入户日期；相等和较晚日期保持可用。最后成员离户只有在指针无冲突、无当前有效 residence、无待迁入申请时才按家庭户版本转为 PENDING_CANCELLATION；保护条件不满足时拒绝并回滚。
- 修复前 Phase08 基线重现 23 项中 5 项失败；重复 ACTIVE HEAD 单测另行重现为预期 409、实际 500。修复后 Phase08 共 24 项通过，0 失败、0 错误、0 跳过，包含首次指定户主、合法销户提交、当前户籍保护、待迁入保护、日期边界和版本冲突。
- 正常创建空家庭户仍保持 ACTIVE；迁出业务原有最后户主迁出后转 PENDING_CANCELLATION 的路径未修改。

提交：`45b5ac18d150c5330f7c1455fdad25f960db8744`。

### C：RETURNED 材料补正与重新审批

**结论：确认并修复。**

- 旧实现的迁出申请完成 `DRAFT → UNDER_REVIEW → APPROVED → RETURNED` 后，申请人删除需替换的已核验材料和上传补正材料都返回 409；通用提交接口虽允许 RETURNED 重提，材料接口却只接受 DRAFT。
- 修复只将材料上传/删除开放到 DRAFT 和 RETURNED，并继续验证申请所有权；UNDER_REVIEW、APPROVED、COMPLETED 等状态仍不能修改材料。迁出业务字段编辑继续由 DRAFT 门禁控制。
- 全流程测试验证：旧核验材料可删除，替换材料新建为 PENDING；重提复用同一 `approval_id`，原有 RETURN、SUBMIT、APPROVE 记录保留；新材料未核验时审批被拒绝，独立核验后再次审批通过，显式迁出执行后通用申请和 `migration_out` 均为 COMPLETED。
- 专业状态的实测行为：退回时 `migration_out` 保持 APPROVED；RETURNED 重提触发状态监听后变为 UNDER_REVIEW，二次批准后恢复 APPROVED。
- 修复前流程测试失败：删除和补传都收到 409。修复后 Phase03 迁移和 Phase12 退回流程共 32 项通过；最终 Phase03 全类 25 项通过。

提交：`9b5081b04f44e716a92ed5cf6ba5121d004ced40`。

### D：材料核验防自审

**结论：确认并修复。**

- 旧实现中 SYSTEM_ADMIN 是 L3 且有 `material:verify` 权限；作为迁出申请人并上传材料后，可以在申请进入 UNDER_REVIEW 时将自己材料核验为 VERIFIED。旧接口实际返回 200，写入 verify_user_id 并新增成功核验日志。
- 修复在 mapper 更新前同时拒绝申请人和材料上传者核验；Controller 的 `material:verify` 权限检查、L3 要求以及 Mapper 的 `verify_status='PENDING'` 条件保留。
- 修复后测试确认：申请人自审为 403，材料仍为 PENDING、核验人为空且没有成功日志；测试另将申请人快照与上传者拆开，确认上传者仍被拒绝；无核验权限 viewer 被 403 拒绝；独立 approver 可核验；再次核验返回 409，不能覆盖结论。
- 修复前 D 组测试失败：预期 403，实际 200。修复后 Phase03 全类 25 项通过。

提交：`a1840f2aee473f3e7cda8b0fa7d1bd1138b236bc`。

## 回归结果

| 检查 | 结果 |
| --- | --- |
| 修复前基线 `mvnw clean test` | 418 通过，0 失败，0 错误，0 跳过 |
| A 修复后 Phase03 定向回归 | 23 通过，0 失败，0 错误，0 跳过 |
| B 修复前 Phase08 重现 | 23 项中 5 项失败；另有 1 项重复户主重现：预期 409、实际 500 |
| B 修复后 Phase08 | 24 通过，0 失败，0 错误，0 跳过 |
| C 修复后 Phase03 + Phase12 | 32 通过，0 失败，0 错误，0 跳过 |
| D 修复后 Phase03 | 25 通过，0 失败，0 错误，0 跳过 |
| 最终 `.\mvnw.cmd clean test` | 434 通过，0 失败，0 错误，0 跳过；BUILD SUCCESS |

所有新增测试使用 Spring Boot Test、MockMvc、JdbcTemplate 和 H2 合成数据。未连接真实人口数据库。

## 修改范围与限制

- 修改后端：`MigrationSubmissionValidator.java`、`MigrationService.java`、`MigrationMapper.java`、`MigrationMapper.xml`、`HouseholdSnapshot.java`、`MigrationTransactionHook.java`、`HouseholdService.java`、`HouseholdMapper.java`、`HouseholdMapper.xml`、`MaterialService.java`，以及 Phase03/Phase08 两个集成测试类。
- 未修改 API 路径、请求 DTO 或数据库 Schema；修改的是既有 API 的状态校验和事务结果。
- 未修改前端。静态检查发现的前端协作项见 `2026-11-01-core-business-round2-frontend-handoff.md`。
- H2 回归不能替代 MySQL 锁、隔离级别和并发行为验证；本轮没有连接 MySQL。
- 审计原报告未覆盖；未触碰 OCR、导出、统计、Redis、行政区划权限或前端实现。

## 提交顺序

1. A：`eabae6a7439d0034b23fa09d9abc27846a0a0776`
2. B：`45b5ac18d150c5330f7c1455fdad25f960db8744`
3. C：`9b5081b04f44e716a92ed5cf6ba5121d004ced40`
4. D：`a1840f2aee473f3e7cda8b0fa7d1bd1138b236bc`
