# 第三阶段迁移与户籍代码审计

## 现状结论

- `person` 是人口基础档案，已有完整后端；演示模块 `residents` 仍并存，但不参与户籍迁移事务。本阶段不删除或合并二者。
- `household`、`household_member`、`residence`、`migration_in`、`migration_out` 已存在于初始化 SQL，但除统计查询外没有迁移后端业务模块。
- `residence` 尚未被业务代码使用，可低风险增量完善并正式定义为“每人至多一条的当前有效户籍登记表”；不另建职责重复的 `residence_registration`。
- 历史记录不能留在当前表。本阶段新增只增不改的 `residence_archive`，迁出时先保存快照再移除 `residence`。
- 既有迁移前端直接调用不存在的 `/migrations/in|out` 接口，含删除操作，迁出还接受客户端原户信息。本阶段改为申请制，原户籍只由后端读取。
- 新增/升级的业务状态统一使用英文稳定编码。

## 增量升级与兼容

- `residence` 补齐地址、区划、登记类型、经办人、版本并增加 `UNIQUE(person_id)`。
- `migration_in/out` 增加唯一 `application_id`、区划地址快照、跨区批次、状态、执行时间及乐观锁。
- `household_member` 使用 `ACTIVE/LEFT`，补齐加入/离开日期与版本；`household` 本阶段只写 `ACTIVE/PENDING_CANCELLATION`。
- `person.current_status_code` 仅作户籍汇总展示，不替代人口档案状态。
- 保留阶段一、二认证、材料、审批和通用申请接口。迁移专业数据存专业表，通过提交校验器和状态监听器接入审批。
