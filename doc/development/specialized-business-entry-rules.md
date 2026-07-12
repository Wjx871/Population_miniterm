# 专业业务入口规则

通用申请只保存标题、申请人、目标对象和原因，不负责专业业务字段。专业申请除创建 `business_application` 外，还必须在同一事务中创建一一对应的详情记录。

当前必须使用专业入口的类型：

- `MIGRATION_IN`：创建 `business_application` 与 `migration_in`
- `MIGRATION_OUT`：创建 `business_application` 与 `migration_out`
- `PERSON_CANCELLATION`：创建 `business_application` 与 `cancellation_record`
- `HOUSEHOLD_CANCELLATION`：创建 `business_application` 与 `cancellation_record`

仅创建 `business_application` 会丢失迁移区划、家庭户、注销原因、事件日期等执行条件，即使审批成为 `APPROVED`，专业执行接口也找不到详情，形成无法闭环的孤立申请。因此后端通过 `SpecializedBusinessTypeRegistry` 集中拒绝通用创建和修改，前端通用选择器也不展示这些类型；前端限制不替代后端校验。

后续加入 `FLOATING_REGISTRATION`、`RESIDENCE_PERMIT_APPLICATION`、重点人口或敏感导出等带详情表的业务时，应先在注册表登记，然后提供同事务创建通用申请和专业记录的服务入口，并补充通用入口拒绝、专业入口成功及审批执行闭环测试。已有孤立申请不在本修复中删除或改写。
