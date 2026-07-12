# 第四阶段注销结构审计

- `BusinessType` 已包含 `PERSON_CANCELLATION`、`HOUSEHOLD_CANCELLATION`，但此前仅可创建无专业详情的通用申请。
- 当前不存在 `cancellation_record`、`household_archive`、注销专业 Mapper/Service/Controller；旧前端存在直接“删除/注销”占位交互，不能作为正式业务。
- `person` 主档永久保留。`status` 继续表达基础档案兼容状态，`current_status_code` 是户籍业务汇总状态，注销执行统一写 `CANCELLED/DECEASED`，禁止物理删除。
- `household` 主档永久保留，只有满足前置条件的 `PENDING_CANCELLATION` 才能执行为 `CANCELLED`。
- `residence` 是当前有效户籍；人员注销时复用 `residence_archive` 保存执行时快照后删除当前记录。
- 家庭户注销需要单独的 `household_archive`，因为它保存户号、户主、地址、区划、户类型及建立日期，职责不同于个人户籍快照。
- 通用提交校验复用 `ApplicationSubmissionValidator`，审批状态同步复用 `ApplicationStatusListener`，不在 `ApprovalService` 增加注销分支。
- 本阶段不物理删除 `person`、`household`、历史成员、归档、申请或审批记录，也不实现注销撤销。
