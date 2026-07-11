# 第五阶段现状审计

## 结论

- `floating_population` 原先仅存在于初始化 SQL 和统计 SQL，字段为来源地、地址、登记日期和中文状态，没有业务 Controller、Service 或 Mapper，因此未形成可执行闭环。本阶段保留表名并增量升级为当前/历史登记模型。
- 仓库原先没有 `residence_permit`、专业申请表或生命周期日志。本阶段新增独立数据源，不把新居住证写入 `certificate`。
- `certificate` 仍用于身份证等通用证件。已有“居住证/临时居住证”记录不删除、不改写，视为 legacy 只读数据；通用前端保留查询显示，但新增选择器移除这两个类型。人工迁移时应核验人员、有效期和当前状态后，通过专门模块补录，禁止直接复制为 ACTIVE。
- `BusinessType`、`SpecializedBusinessTypeRegistry`、`ApplicationSubmissionValidator`、`ApplicationStatusListener` 已提供专业入口、提交校验和审批状态同步扩展点。本阶段四类业务均登记为专业业务，同一事务创建通用申请和专业详情。
- `application_material` 使用字符串材料类型，可增量增加课程模拟材料，无需改表。审批服务在批准前统一要求必需材料 VERIFIED。
- 当前数据范围通过 `AuthenticatedUser`、`DataScopeCriteria` 和 Mapper SQL 实施。本阶段查询按 SELF/DEPARTMENT/REGION/ALL 限制，执行操作再次校验区划。
- 既有业务仍有少量 `LocalDate.now()`。第五阶段日期计算全部使用可注入 `Clock`，期限由配置类集中读取。
- `person` 是唯一人员身份主档；流动登记必须关联已有、未注销、未死亡的人员。它不创建第二套人员档案。
- `residence` 是本地户籍，`floating_population` 是当前区域居住登记。流动登记不创建或修改 `residence`、`household_member`。
- 流动登记是居住证申请的前置依据，但不等同于居住证。审批通过只批准业务，显式执行才生成登记或改变证件。
- 本阶段只生成带免责声明的课程模拟登记信息，不生成官方印章、二维码、真实卡面或具有法律效力的电子证照。

## 增量策略

保留人员、户籍、审批、迁移、注销和历史证件数据；新增专业申请、居住证和不可变生命周期日志。旧流动人口行在 MySQL 迁移中标记 `LEGACY-FR-*` 并转换当前标志，迁移后由人工核验区划和事由。当前环境没有真实 MySQL 8 实例，迁移脚本只完成静态检查和 H2 等价结构测试。
