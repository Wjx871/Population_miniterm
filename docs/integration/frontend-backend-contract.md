# 前后端接口契约矩阵

审计分支：`integration/frontend-backend-v1`；收口日期：2026-07-13。后端 Controller、DTO/VO、权限注解与自动化测试为契约基准，前端 API、adapter、store、router 和页面按此适配。

## 公共契约

- 前端 Axios `baseURL=/api`，API 文件不重复书写 `/api` 前缀。
- JSON 统一消费 `ApiResponse.data`；文件下载使用 blob/raw response，不猜测多层 `data`。
- UI 分页一基 `current` 经 `toSpringPageParams` 转为 Spring 零基 `page`；响应统一消费 `content/totalElements/number/size`。
- `LocalDate` 为 `yyyy-MM-dd`，`LocalDateTime` 为 ISO-8601。
- 400/401/403/404/409/500 分别表示参数、认证、授权、资源/数据范围、业务/并发冲突和系统错误；成功空结果与请求失败必须分开显示。
- 刷新时使用 `/auth/me` 恢复服务端身份；退出调用 `/auth/logout`，随后清理 token、Pinia 与参考数据缓存。
- 专业申请审批通过后不会自动执行；迁移、注销、流动人口、居住证、重点人口和敏感导出必须由专业权限显式 execute，重复 execute 由后端返回 409。

## 矩阵

| 编号 | 模块 | 前端入口 | 后端正式接口 | 权限 | 状态 | 验证结论 |
|---|---|---|---|---|---|---|
| C-001 | 登录 | Login | `POST /api/auth/login` | 匿名 | PASS | 五个演示账号均为 200 |
| C-002 | 当前用户 | 全局 store/router | `GET /api/auth/me` | authenticated | PASS | 刷新只信任 token，并从服务端恢复角色、权限、数据范围 |
| C-003 | 退出登录 | MainLayout/store | `POST /api/auth/logout` | authenticated | PASS | 旧 token 变为 401，重复退出不产生 500 |
| C-004 | 首页统计 | Dashboard/DataDashboard | `/api/dashboard/**` | `statistics:view` | PASS | 失败不伪造 0，提供明确重试 |
| C-005 | 待审批 | Dashboard/ApprovalList | `/api/approvals/**` | `approval:view/handle` | PASS | 非审批角色不展示操作，后端独立拒绝越权 |
| C-006 | 行政区划 | RegionManagement/RegionCascader | `/api/admin-regions/**` | `region:view/manage` | PASS | 树、CRUD、编码与层级限制一致 |
| C-007 | 数据字典 | DictionaryManagement/DictionarySelect | `/api/dictionaries/**` | `dictionary:view/manage` | PASS | 提交 code，中文 label 仅展示；缓存失败不污染 |
| C-008 | 人口主档 | PersonList | `/api/persons/**` | `population:view/edit` | PASS | 列表、详情、创建、更新、脱敏和 409 一致，无物理删除 |
| C-009 | 家庭户 | HouseholdList/Detail | `/api/households/**` | `household:view/edit` | PASS | DTO、version、成员离户和专用户主变更已对齐，无物理删除 |
| C-010 | 迁入 | MigrationApply/List | `/api/migrations/in/applications/**` | `migration:*` | PASS | 专业创建、材料、审批和显式 execute 闭环 |
| C-011 | 迁出 | MigrationApply/List | `/api/migrations/out/applications/**` | `migration:*` | PASS | 专业创建、材料、审批和显式 execute 闭环 |
| C-012 | 人员/家庭户注销 | CancellationManagement | `/api/cancellations/**` | `cancellation:*` | PASS | 两类专业入口、动态材料规则、详情和显式 execute 已接入 |
| C-013 | 流动人口 | Floating 页面 | `/api/floating-registrations/**`,`/api/floating-populations/**` | `floating:*` | PASS | 登记、关闭、材料与显式 execute 一致 |
| C-014 | 居住证 | ResidencePermit 页面 | `/api/residence-permits/**` | `residence-permit:*` | PASS | 首次、签注、注销、到期和日志一致 |
| C-015 | 通用证件 | CertificateList | `/api/certificates/**` | `certificate:view/edit` | PASS | DTO、日期、注销和脱敏一致；居住证禁止走通用入口 |
| C-016 | 重点人口建档 | KeyPopulationManagement | `/api/key-populations/register-applications/**` | `key-population:*` | PASS | 正式权限码、必需材料、审批和 execute 已接入 |
| C-017 | 重点人口解除/历史 | KeyPopulationManagement | `/api/key-populations/{id}/release-applications`,`/{id}/history` | `key-population:*` | PASS | 解除申请、显式 execute 和历史展示已接入 |
| C-018 | 通用申请 | Application 页面 | `/api/applications/**` | `application:*` | PASS | 专业类型仅由专业入口创建，分页和状态一致 |
| C-019 | 审批 | Approval 页面 | `/api/approvals/**` | `approval:view/handle` | PASS | 审批不隐式授予专业 execute |
| C-020 | 显式执行 | ApplicationDetail handlers | 各专业 execute/issue/endorse/cancel | 专业权限 | PASS | 全部专业类型根据最新 version 执行，完成后重新读取双状态 |
| C-021 | 人口综合查询 | ComprehensiveQuery | `/api/query/persons`,`/api/queries/persons/{id}` | `population:view` | PASS | Phase 11 列表筛选与聚合档案详情并存 |
| C-022 | 家庭户/迁移查询 | HouseholdQuery/MigrationHistoryQuery | `/api/query/households`,`/migration-history` | `household:view`,`migration:view` | PASS | 分页、布尔真值、日期范围和数据范围一致 |
| C-023 | 统计分析 | Dashboard/DataDashboard | `/api/dashboard/**`,`/api/statistics/**` | `statistics:view` | PASS | 真实 0 与错误态区分 |
| C-024 | 操作/登录日志 | LogQuery | `/api/logs/operations`,`/logins` | `log:view` | PASS | viewer/admin 可见，其他角色 403；详情由后端脱敏 |
| C-025 | 数据导出 | ExportManagement | `/api/exports/**` | `data:export:*` | PASS | 普通脱敏导出与敏感申请/审批/execute/download 分离 |
| C-026 | 健康检查 | 部署/联调门禁 | `GET /api/health` | 匿名 | PASS | MySQL 正常，Redis 关闭使用 `MYSQL_FALLBACK` |
| C-027 | 材料 | 申请页/ApplicationDetail | `/api/applications/{id}/materials`,`/api/materials/**` | `material:*` | PASS | multipart、核验、删除和下载契约一致 |
| C-028 | 用户/角色在线 CRUD | 无菜单/路由/API | Backend V1 未冻结此能力 | — | OUT_OF_SCOPE | 已删除前端旧 `/users`，不伪造后端能力 |
| C-029 | 注册与旧人口接口 | 无入口 | Backend V1 无 `/auth/register`、`/api/residents` | — | OUT_OF_SCOPE | 已从可执行前端删除并有静态回归 |

## 状态汇总

- 总数：29
- PASS：27
- FRONTEND_FIX：0
- BACKEND_FIX：0
- CONTRACT_MISMATCH：0
- NOT_IMPLEMENTED：0
- OUT_OF_SCOPE：2
- BLOCKED：0

矩阵中的 PASS 已通过自动化契约测试、真实 MySQL API 验证或两者共同验证。浏览器交互式点击回归受本地浏览器控制运行时故障影响，单独记录在问题清单，不改变已验证的接口契约状态。
