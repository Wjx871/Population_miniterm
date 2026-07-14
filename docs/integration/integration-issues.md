# 前后端联调问题清单

历史问题保持可追溯；PASS 表示已修复并有回归证据。

## INT-001 通用申请列表返回 500

- 模块/页面：通用申请；Dashboard、ApplicationList、MigrationList
- 角色与请求：population；`GET /api/applications?page=0&size=5`
- 请求参数：`page=0,size=5`
- 原响应：500，MyBatis 无法映射旧 `BusinessType.RESIDENCE_PERMIT_APPLICATION`
- 根因：演示数据写入冻结枚举不存在的业务类型，分页参数本身合法。
- 修改：`doc/database/demo_data.sql`、`BackendV1ReleaseAuditTest` 及分页异常处理。
- 测试：真实 MySQL 幂等重放后返回 200；负 page、零 size、非整数 page 返回 ApiResponse 400。
- 状态：PASS

## INT-002 工作台显示原始 Vue 插值

- 模块/页面：首页；Dashboard
- 角色与请求：任意 `statistics:view`；前端渲染，无 HTTP 请求
- 请求参数/原响应：不适用；页面显示 `近{{ overview.periodDays || 30 }}日迁入/迁出`
- 根因：插值表达式放在普通属性字符串中。
- 修改：`Dashboard.vue` 使用 computed 和动态属性绑定。
- 测试：`dashboardRendering.test.js` 与 Vite build 通过。
- 状态：PASS

## INT-003 首页统计以虚假 0 覆盖错误

- 模块/页面：首页统计；Dashboard、DataDashboard
- 角色与请求：population；`GET /api/dashboard/overview`、`GET /api/dashboard/charts?days=30&regionLimit=8`
- 请求参数：默认 period/expiry，`days=30,regionLimit=8`
- 原响应：接口数据受旧 `VALID` 状态影响；前端失败时显示 0。
- 根因：演示状态不符合冻结枚举，页面未区分 null/error/真实 0。
- 修改：`demo_data.sql`、Dashboard 相关 adapter/view。
- 测试：真实 MySQL 为 `ACTIVE=1`；错误态显示 message/retry，前端测试通过。
- 状态：PASS

## INT-004 待办失败丢失服务端错误

- 模块/页面：首页待办；Dashboard
- 角色与请求：按权限；pending/my applications/expiring permits
- 请求参数：各模块默认筛选
- 原响应：401/403/500 被压成 Boolean，仅显示通用“加载失败”。
- 根因：`WorkItemList` error 属性不保留 message。
- 修改：`WorkItemList.vue`、`Dashboard.vue`、统一 `getApiErrorMessage`。
- 测试：服务端 message 与重试入口均有源码回归。
- 状态：PASS

## INT-005 认证保持与服务端退出未接入

- 模块/页面：认证；全局 store、router、MainLayout
- 角色与请求：五角色；原先缺少 `GET /api/auth/me`、`POST /api/auth/logout`
- 请求参数：Bearer token
- 原响应：刷新依赖 localStorage 旧权限快照，退出仅本地清 token。
- 根因：前端未接入 Backend V1 完整认证生命周期。
- 修改：`auth.js`、`request.js`、`user.js`、`userNormalizer.js`、router、MainLayout。
- 测试：五角色 login/me/logout 200；旧 token 401；重复 logout 不产生 500；后端定向 21 项通过。
- 状态：PASS

## INT-006 前端残留 Backend V1 不支持的旧接口

- 模块/页面：认证、人口、家庭户、隐藏用户管理
- 角色与请求：不适用；`POST /auth/register`、`GET /users`、人口/户籍 DELETE、`/api/residents`
- 请求参数/原响应：Backend V1 无正式契约，可能 404/405。
- 根因：前端阶段版本残留 API 导出、页面和注释。
- 修改：删除 register/users API 与路由页面；删除人口/家庭户物理删除；成员离户改正式 POST。
- 测试：`obsoleteContracts.test.js` 静态扫描；最终前端 105 项通过。
- 状态：PASS

## INT-007 行政区划与字典未完整适配

- 模块/页面：公共基础数据；RegionManagement、DictionaryManagement、公共选择器
- 角色与请求：五角色查询；viewer 构造有效写请求
- 请求参数：区划树、字典 type/code、合法 Region DTO
- 原响应：页面/缓存对 Axios 原始响应和业务 data 的解包规则不统一。
- 根因：公共组件、adapter 和缓存来自不同开发批次。
- 修改：合入 `origin/develop@4334e20` 稳定参考数据，冲突中保留完整认证生命周期；修正 `referenceDataCache`。
- 测试：前端 96+ 项；真实查询 200，viewer 有效写 403；Redis 关闭回源 MySQL。
- 状态：PASS

## INT-008 家庭户 DTO、权限和正式操作不一致

- 模块/页面：家庭户；HouseholdList、HouseholdDetail、HouseholdForm
- 角色与请求：五角色读；viewer 写；`POST /members/{id}/leave`、`POST /change-head`
- 请求参数：户号、区划、户类型、日期、status、version；离户日期/version；新户主/reason/version
- 原响应：前端缺失 DTO 字段，使用旧权限码，户主变更入口缺失。
- 根因：前端早期契约落后于 Phase 08 正式 Controller。
- 修改：household API/adapter/form/list/detail 与 `household:edit` 权限。
- 测试：人口/户籍/证件后端 40 项；真实五角色读 200、viewer 人口写 403；前端契约测试通过。
- 状态：PASS

## INT-009 Phase 11 查询和日志入口缺失

- 模块/页面：综合查询、统计、日志；ComprehensiveQuery、HouseholdQuery、MigrationHistoryQuery、LogQuery
- 角色与请求：五角色；`/api/query/persons`、`/households`、`/migration-history`、`/statistics/overview`、`/logs/**`
- 请求参数：Spring page、筛选、日期区间、日志类型/结果/IP
- 原响应：家庭户/迁移/日志无页面；人口查询失败被空数组和 0 覆盖。
- 根因：前端仅有旧聚合人口查询和统计大屏。
- 修改：新增 query/log API、adapter、页面和权限路由；人口列表切 Phase 11 正式入口并保留聚合档案详情。
- 测试：后端定向 22 项；三类查询和统计五角色 200；日志 viewer/admin 200，其余 403；前端失败/空结果回归。
- 状态：PASS

## INT-010 注销、重点人口与导出没有前端闭环

- 模块/页面：复杂业务；CancellationManagement、KeyPopulationManagement、ExportManagement、ApplicationDetail
- 角色与请求：viewer/approver 越权；专业 create/detail/execute/download
- 请求参数：专业 DTO、材料类型、applicationId、最新 version
- 原响应：无专业创建页；重点人口使用旧 `key:*`；申请详情无法加载专业记录或 execute。
- 根因：前端复杂业务范围尚未补齐。
- 修改：新增三类 API/页面、正式权限码、动态材料规则、`directBusinessHandler` 和专业详情面板。
- 测试：Phase 03/04/05/06/10 共 159 项；五角色列表 200；viewer 注销创建 403；approver 创建/普通导出及三类 execute 均 403；前端 handler 契约通过。
- 状态：PASS

## INT-011 401 重复提示与 403 会话边界

- 模块/页面：全局 Axios；所有受保护页面
- 角色与请求：任意；并发 401、合法登录后的 403
- 请求参数：失效 token 或权限不足请求
- 原响应：可能重复提示；403 处理与认证失效职责混杂。
- 根因：缺少全局 401 去重与明确的 401/403分支。
- 修改：`request.js`、user store；401 清会话并去重，403 保持登录态并跳转 403。
- 测试：`authLifecycle.test.js`、真实旧 token 401、viewer/approver 403。
- 状态：PASS

## INT-012 浏览器交互式五角色回归无法运行

- 模块/页面：全前端；菜单、路由、按钮、刷新与交互点击
- 角色与请求：viewer、population、household、approver、admin；浏览器控制运行时初始化
- 请求参数：本地浏览器会话
- 原响应：`failed to write kernel assets: path not found`
- 根因：Codex 内置浏览器控制运行时的本地环境故障，不是项目代码异常。
- 修改：未修改系统环境；改用真实 MySQL API、store/router 权限测试、源码契约测试和 production build 提供替代证据。
- 测试：后端 323 项、前端 105 项、五角色真实 API 与构建均通过；缺少本轮浏览器点击/录像证据。
- 状态：BLOCKED（外部工具证据缺口）

## 范围结论

- 用户、角色、部门在线 CRUD 与公开注册未纳入 Backend V1：OUT_OF_SCOPE，前端无菜单、路由或 API。
- 未修改数据库结构、未删除合法数据、未放宽权限。
- `backup/` 原有未跟踪数据库备份保持原样且未提交。

## INT-013 最终汇合产生并行业务实现

- 模块/页面：注销、重点人口、敏感导出、Application Handler。
- 原响应：语义合并后同时存在最新前端专用 Handler/完整页面，以及联调分支的 `directBusinessHandler`、并行 API 和简化管理页。
- 根因：Git 可自动保留 add/add 文件，但无法判断哪套实现是最终事实。
- 修改：以 `develop` 的专用 Handler 注册表和完整页面为唯一实现，保留联调分支的正式接口、权限、错误处理和执行契约；删除未引用副本，并将契约测试改为验证最终注册表。
- 测试：前端 189 项全部通过，production build 成功；全局扫描无重复路由、冲突标记或旧可执行接口。
- 状态：PASS

第三阶段没有新增代码级 BLOCKED。INT-012 的浏览器控制运行时故障仍是唯一外部证据缺口，需要人工五角色浏览器验收补齐。

## 人工验收问题集中修复（2026-07-14）

| 问题 | 根因与修改 | 数据/HTTP | 测试 | 状态 |
|---|---|---|---|---|
| FUNC-CANCELLATION-001 | 只读详情错误调用带 `FOR UPDATE` 的户籍查询；拆分非锁定详情查询与执行行锁 | 不改数据；500→200，缺失仍 404 | Phase04 注销详情回归 | CLOSED |
| FUNC-PERMIT-001 | 700001 原缺少居住证专业申请记录；接口继续使用通用 applicationId，幂等 DEMO SQL 已补齐专业链路 | SQL 重复执行无重复数据；专业详情 200 | Phase05 200/404 契约 + 正式库接口复核 | CLOSED |
| FUNC-MIGRATION-001 | 选择器发送中文状态“正常”而后端要求 `ACTIVE`，且未传区划、吞掉失败 | 不改数据；搜索 200，失败与空结果分离 | 前端搜索契约 + Playwright | CLOSED |
| FUNC-MIGRATION-002 | 人员选择后未查询当前有效户籍 | 不补造王家兴户籍；前端提前阻止，后端 409 保留 | 前端资格契约 | CLOSED |
| UI-FUNC-001 | 人口抽屉仍为开发占位文案 | 复用脱敏综合档案接口，增加 loading/empty/error/retry | 前端关联信息契约 | CLOSED |
| KNOWN-WARN-001 | 审批表格由运行时 template 字符串声明 | 改为正式 `ApprovalTable.vue` SFC | 单测 + Playwright warning 门禁 | CLOSED |
| UI-WARN-002 | `el-radio-button` 用 label 充当值 | 改用 value，PERSON/HOUSEHOLD 不变 | 单测 + Playwright warning 门禁 | CLOSED |
| UI-DISPLAY-002 | 状态映射不完整、列宽不足 | StatusTag 统一映射，状态列 130px + tooltip | 状态/源码契约 + Playwright | CLOSED |
| UI-DISPLAY-003 | 迁移表单 120px 标签宽度不足 | 迁入/迁出统一 136px，并设置 nowrap | 样式契约 + Playwright | CLOSED |

最终门禁已完成：后端 326/326、前端 195/195、Playwright 20/20 全部通过，HTTP 500、控制台 error、`pageerror` 以及两类目标 warning 均为 0。用户执行幂等 DEMO SQL 后，`DEMO-PERMIT-001` 专业详情接口复核为 200，全部 9 项最终状态为 CLOSED。
