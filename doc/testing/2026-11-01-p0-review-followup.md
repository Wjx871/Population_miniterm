# Population Miniterm P0 复核补丁报告

## 范围与结论

- 起点：`d1b23206367642aac27553adea61e43c0b37a908`；事故恢复后的 `main`：`b54579b6b7f8b1feeda60f43906da05fb2ffda99`。
- 本分支只处理敏感导出申请人授权复核、浮动人口/居住证 SELF 归属、导出日志可见性复核和 RBAC 测试契约。
- 未改 HTTP API、正式权限配置或数据库 Schema；测试使用 H2 与合成数据。

## 敏感导出申请人授权

旧实现只按申请时 `export_scope` 与 `business_application` 快照构造范围。失败优先测试在申请人从 ALL 降至 SELF 后，由管理员执行时复现旧实现仍返回 200 并生成导出；账号停用、角色停用、失去 `data:export:sensitive:apply`、部门/区划变化也都曾被旧实现放行。

现在执行前通过 `AuthService` 按申请人 ID 重新读取启用状态、当前角色、数据范围与有效权限；当前授权收窄、申请权限撤销、部门/区划快照变化或无法验证时返回 409。授权扩大时仍以原批准范围构造单一 `DataScopeCriteria`，计数和 Excel 查询共用该范围。拒绝时不创建导出日志或文件，申请状态保持 APPROVED。

申请人不存在通过直接调用范围校验的失败关闭测试覆盖。测试 Schema 对申请人有外键，因此没有构造违反外键的 HTTP 申请记录。

## SELF 对象归属

源码没有单句定义办结记录的 SELF 归属。现有契约以 `business_application.applicant_user_id` 标识申请所有人；`floating_population.operator_id` 和 `residence_permit_application.operator_id` 则在执行时由执行用户写入，用于记录实际经办人。注销、归档等导出 SELF 条件也关联申请人。按此契约，浮动登记和居住证办结记录的 SELF 归属取原申请人，执行人保留为经办审计信息。

旧实现测试以申请人 A、执行人 B 的合成记录复现了列表/详情错归 B、A 无法关闭本人记录、B 可对 A 的记录发起操作。修复后，列表、详情、关闭/发起操作、居住证日志和导出 SQL 均使用 `source_application_id` 关联的申请人；另一 SELF 用户仍被拒绝。

## 导出日志详情与 QUERY_VIEWER

`V4_012_dashboard_viewer_scope.sql` 将 QUERY_VIEWER 范围设为 ALL；`V4_006_export_audit_permission.sql` 授予 `data:export:log:view`；导出 API 文档明确日志按 SELF/DEPARTMENT/REGION/ALL 查询。因此 viewer 查看 ALL 范围的导出日志详情属于现行授权契约，不能将该读取权限等同系统管理员权限。测试确认 viewer 可读范围内的 `filterSnapshot` 和 `exportedFields`，但不能下载他人的敏感导出；真正 DEPARTMENT 合成用户访问其他部门详情被拒绝。

过滤器由 `ExportPolicy` 限制为姓名、区划、状态和日期条件，不接受身份证号、电话、SQL 或表达式；日志 DTO 不包含存储路径或结果数据。`filterSnapshot` 仍是查询条件审计元数据，应按现有 `data:export:log:view` 和数据范围保护。

测试库 Schema 与 V4_012 使用 ALL。仓库全量初始化 SQL 和部分权限矩阵文档仍有旧的 DEPARTMENT 表述；本补丁不改生产权限或初始化权限，只把 RBAC 测试改为当前 V4_012 契约，并新增真正的 DEPARTMENT 角色验证隔离。

## 测试

- 原 d1 基线 `AuthRbacIntegrationTest`：16 项中 2 项失败，viewer 登录断言期待 DEPARTMENT、日志隔离也错误使用 viewer；响应实际是 ALL，且日志包含其他操作者。
- 修复后定向测试：`AuthRbacIntegrationTest` 17/17、`ExportDataScopeAuditIntegrationTest` 14/14、`FloatingSelfScopeAuditIntegrationTest` 7/7。
- P0 回归定向测试：`Phase04CancellationIntegrationTest` 26/26、`Phase08PersonIdentityIntegrationTest` 6/6、`Phase06ExportAuditIntegrationTest` 26/26。
- 全量 `.\mvnw.cmd clean test`：397 项通过，0 失败、0 错误、0 跳过。
- 环境限制：仅在隔离 H2 测试库验证；未连接真实人口数据库，也未执行生产 MySQL。
