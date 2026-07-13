# Backend V1 API 总索引

所有响应使用 `ApiResponse<T>`；除登录和健康检查外均需 Bearer JWT。以下为正式模块入口，细节以关联文档和 Controller 为准。

| 模块 | 正式入口 | 详细文档 |
|---|---|---|
| 认证 | `/api/auth/login`,`/me`,`/logout` | [auth-rbac-api.md](auth-rbac-api.md) |
| 用户、角色、权限 | 当前用户信息及初始化 RBAC；V1 无在线管理 CRUD | [auth-rbac-api.md](auth-rbac-api.md) |
| 人口 | `/api/persons` | [household-master-data-api.md](household-master-data-api.md) |
| 家庭户、成员、户主 | `/api/households` | [household-master-data-api.md](household-master-data-api.md) |
| 户籍、迁移、归档 | `/api/migrations`,`/api/residence-archives` | [household-migration-api.md](household-migration-api.md) |
| 注销 | `/api/cancellations`,`/api/household-archives` | [cancellation-management-api.md](cancellation-management-api.md) |
| 流动人口、居住证 | `/api/floating-registrations`,`/api/residence-permits` | [floating-residence-permit-api.md](floating-residence-permit-api.md) |
| 行政区划 | `/api/admin-regions` | [admin-region-api.md](admin-region-api.md) |
| 数据字典 | `/api/dictionaries` | [data-dictionary-api.md](data-dictionary-api.md) |
| 通用证件 | `/api/certificates`,`/api/persons/{id}/certificates` | [certificate-api.md](certificate-api.md) |
| 重点人口 | `/api/key-populations`,`/api/persons/{id}/key-populations` | [key-population-api.md](key-population-api.md) |
| 申请、材料、审批 | `/api/applications`,`/api/materials`,`/api/approvals` | [business-application-approval-api.md](business-application-approval-api.md) |
| 导出 | `/api/exports` | [export-audit-api.md](export-audit-api.md) |
| 综合查询 | `/api/query/persons`,`/households`,`/migration-history` | [query-statistics-api.md](query-statistics-api.md) |
| 统计 | `/api/statistics` | [query-statistics-api.md](query-statistics-api.md) |
| 操作与登录日志 | `/api/logs` | [log-api.md](log-api.md) |
| 健康检查 | `/api/health` | [backend-v1-deployment.md](../deployment/backend-v1-deployment.md) |

`/api/residents` 已在 Phase 08 删除，只能作为历史迁移说明出现，不得作为正式入口使用。
