# 前后端接口契约矩阵（第一阶段初稿）

审计基线：`integration/frontend-backend-v1@6936fbc`，审计日期：2026-07-13。后端接口以 Controller、DTO/VO、自动化测试和 `doc/api/` 为准；前端调用以 `frontend/src/api/`、adapters、stores、router 和页面实际引用为准。

## 公共契约

- 基础地址：前端 Axios `baseURL=/api`，Vite 将 `/api` 代理到 `VITE_BACKEND_TARGET`（默认 `http://127.0.0.1:8080`）。API 文件只写去掉 `/api` 前缀的路径。
- 认证：除 `/api/auth/login`、`/api/health` 外均使用 `Authorization: Bearer <token>`。
- 响应：JSON 接口统一为 `{ code, message, data, timestamp }`；Axios 响应拦截器成功时统一返回 `data`，页面不得再次猜测 `response.data` 层级。文件下载明确使用 `rawResponse`。
- 分页：页面组件使用一基 `current`；`toSpringPageParams` 转换为 Spring Data 零基 `page=current-1`。响应使用 `content,totalElements,totalPages,number,size`，统一经 `normalizePageResult` 适配。
- 日期：`LocalDate` 使用 `yyyy-MM-dd`；`LocalDateTime` 查询参数与响应使用 ISO-8601。页面显示可格式化，但请求不得提交本地化日期或 `Invalid Date`。
- 错误：400 参数、401 认证、403 权限、404 资源/范围、409 状态或并发冲突、500 缺陷。页面优先显示后端 `message`，不得用空数组或虚假 0 覆盖失败。

## 矩阵

| 编号 | 模块 | 前端页面 | 前端调用 | 后端实际接口 | HTTP Method | 请求参数 | 响应结构 | 权限码 | 当前状态 | 处理结论 |
|---|---|---|---|---|---|---|---|---|---|---|
| C-001 | 登录 | Login | `/auth/login` | `/api/auth/login` | POST | username,password | LoginResponse | 匿名 | PASS | 路径、字段和解包一致 |
| C-002 | 当前用户 | 全局 store | 无刷新校验 | `/api/auth/me` | GET | Bearer | UserInfo | authenticated | FRONTEND_FIX | 刷新后应调用 me 校验服务端会话 |
| C-003 | 退出登录 | MainLayout/store | 仅清本地 token | `/api/auth/logout` | POST | Bearer | null | authenticated | FRONTEND_FIX | 先调用服务端撤销 JWT，再清本地状态 |
| C-004 | 首页统计 | Dashboard/DataDashboard | `/dashboard/overview`,`/charts` | 同前缀 `/api` | GET | periodDays,expiryDays / days,regionLimit | Dashboard VO | `statistics:view` | PASS | 演示数据修正并受控重放后，概览与图表口径一致 |
| C-005 | 待审批列表 | Dashboard/ApprovalList | `/approvals/pending` | `/api/approvals/pending` | GET | 无 | List | `approval:view` | PASS | 非审批角色不发请求 |
| C-006 | 行政区划 | 暂无 | 无 | `/api/admin-regions/**` | GET/POST/PUT | 查询或 DTO+version | List/Region | `region:view/manage` | NOT_IMPLEMENTED | 后端已完成，前端 API 与页面待实现 |
| C-007 | 数据字典 | 证件页；管理页占位 | `/dictionaries/{type}` | `/api/dictionaries/**` | GET/POST/PUT | type/code 或 DTO | List/Page/Item | `dictionary:view/manage` | FRONTEND_FIX | 查询适配可用；管理页与 CRUD 未实现 |
| C-008 | 人口主档 | PersonList | `/persons/**` | `/api/persons/**` | GET/POST/PUT | Spring page + PersonRequest | Page/Person | `population:view/edit` | FRONTEND_FIX | 主流程匹配；删除导出函数是废弃接口，须移除 |
| C-009 | 家庭户 | HouseholdList/Detail | `/households/**` | `/api/households/**` | GET/POST/PUT | page、Household DTO、version | Page/Household | `household:view/edit` | CONTRACT_MISMATCH | 前端仍有 DELETE；离户应 POST `/members/{id}/leave` 且带日期/version；户主变更需专用接口 |
| C-010 | 迁入 | MigrationApply/List | `/migrations/in/applications/**` | 同前缀 `/api` | POST/PUT/GET | MigrationInRequest/version | 专业详情 | `migration:in:create/view/execute` | PASS | 创建、更新、详情、显式 execute 路径一致 |
| C-011 | 迁出 | MigrationApply/List | `/migrations/out/applications/**` | 同前缀 `/api` | POST/PUT/GET | MigrationOutRequest/version | 专业详情 | `migration:out:create/view/execute` | PASS | 创建、更新、详情、显式 execute 路径一致 |
| C-012 | 人员/家庭户注销 | 暂无 | 无 | `/api/cancellations/**` | GET/POST/PUT | 专业申请 DTO/version | Page/Detail | `cancellation:*` | NOT_IMPLEMENTED | 后端完成，前端未提供入口 |
| C-013 | 流动人口 | Floating 页面 | `/floating-registrations`,`/floating-populations` | 同前缀 `/api` | GET/POST/PUT | 专业 DTO/version | Page/Detail | `floating:*` | PASS | 创建、详情、关闭、显式 execute 已对齐 |
| C-014 | 居住证 | Permit 页面 | `/residence-permits/**` | 同前缀 `/api` | GET/POST/PUT | 专业 DTO/version | Page/Detail/Log | `residence-permit:*` | PASS | 首次、签注、注销和日志路径一致 |
| C-015 | 通用证件 | CertificateList | `/certificates/**` | 同前缀 `/api` | GET/POST/PUT | page、证件 DTO/version | Page/Certificate | `certificate:view/edit` | PASS | 居住证由专业入口拒绝 |
| C-016 | 重点人口建档 | 占位路由 | 无 | `/api/key-populations/register-applications/**` | POST/GET | Register DTO/version | Detail | `key-population:*` | NOT_IMPLEMENTED | 前端权限码仍用旧 `key:*`，不得启用占位页 |
| C-017 | 重点人口解除 | 占位路由 | 无 | `/api/key-populations/{id}/release-applications` 等 | POST/GET | Release DTO/version | Detail/History | `key-population:*` | NOT_IMPLEMENTED | 后端闭环完成，前端未实现 |
| C-018 | 通用申请 | Application 页面 | `/applications/**` | 同前缀 `/api` | GET/PUT/DELETE/POST | filters+page / Application DTO | Page/Detail | `application:*` | PASS | `page=0` 返回 200；旧枚举已修；非法 page/size 统一返回 400 |
| C-019 | 业务审批 | Approval 页面 | `/approvals/**` | 同前缀 `/api` | GET/POST | decision comment,version | List/Detail | `approval:view/handle` | PASS | 审批不授予专业 execute |
| C-020 | 显式执行 | 申请详情 handlers | 按业务模块 execute | 各专业 `/execute`,`/issue`,`/endorse`,`/cancel` | POST | version | null/业务结果 | 专业 execute | FRONTEND_FIX | 迁移、流动、居住证已接；注销、重点人口、敏感导出缺失 |
| C-021 | 综合查询 | ComprehensiveQuery | `/queries/persons/**` | `/api/queries/persons/**` | GET | filters,page,sort | Page/Profile | `population:view` | PASS | 注意另有 Phase11 `/api/query/**`，当前页面使用聚合 profile 接口 |
| C-022 | 统计分析 | DataDashboard | `/dashboard/**` | `/api/dashboard/**` | GET | days/limits | Overview/Charts | `statistics:view` | PASS | 旧 `/statistics/summary|charts` 仍存在但页面未引用 |
| C-023 | 数据导出 | 暂无 | 无 | `/api/exports/**` | GET/POST | module,filters,fields,version | Page/Log/File | `data:export:*` | NOT_IMPLEMENTED | 后端完成，前端无页面/API |
| C-024 | 操作日志 | 暂无 | 无 | `/api/logs/operations`,`/logins` | GET | filters,page,size | Page | `log:view` | NOT_IMPLEMENTED | 前端旧 `/statistics/logs` 不是正式日志查询入口 |
| C-025 | 健康检查 | 暂无 | 无 | `/api/health` | GET | 无 | health object | 匿名 | NOT_IMPLEMENTED | 部署可用，前端暂无状态页 |
| C-026 | 材料 | 多个专业申请页 | `/applications/{id}/materials`,`/materials/**` | 同前缀 `/api` | GET/POST/DELETE | multipart/verify DTO | List/Material/File | `material:*` | PASS | blob 错误单独规范化 |
| C-027 | 用户管理 | UserList（菜单隐藏） | `/users` | Backend V1 无在线用户 CRUD | GET | page | 无 | 前端旧码 `system:user:view` | OUT_OF_SCOPE | 移除正式路由/API 引用，不伪造后端 |
| C-028 | 注册 | 无页面 | `/auth/register` | Backend V1 无此接口 | POST | 注册 DTO | 无 | 无 | OUT_OF_SCOPE | 删除未使用 API 导出 |
| C-029 | 旧人口接口 | 无正式页面 | 未发现 `/api/residents` | Backend V1 已移除 | — | — | — | — | PASS | 继续用静态回归禁止重新引入 |

## 第一阶段结论

首轮覆盖了要求的 24 个核心模块，并额外列出材料、用户管理、注册和旧人口接口。`PASS` 仅表示当前静态契约与已执行的只读验证一致，不代表复杂写业务已完成端到端验收；后续按 A-E 阶段逐项将矩阵状态收敛。
