# 前后端联调问题清单

## INT-001 通用申请列表返回 500

- 问题编号：INT-001
- 模块：通用申请 / 工作台
- 页面：Dashboard、ApplicationList、MigrationList
- 账号：population
- 请求：`/api/applications?page=0&size=5`
- 方法：GET
- 请求参数：`page=0,size=5`
- 响应状态：500
- 响应内容：`{"code":500,"message":"服务器处理请求失败"...}`
- 后端异常：`No enum constant ... BusinessType.RESIDENCE_PERMIT_APPLICATION`
- 根因：`doc/database/demo_data.sql` 写入了冻结枚举不存在的旧业务类型；MyBatis 映射 `business_type` 时失败。`page=0` 本身符合 Spring 零基分页契约。
- 归属：数据库演示数据
- 修复方案：演示数据改为 `RESIDENCE_PERMIT_FIRST_ISSUE`，幂等更新同时修复 `business_type`；增加静态回归测试。已安装演示库需在受控验证中重放修正后的演示脚本。
- 修改文件：`doc/database/demo_data.sql`、`BackendV1ReleaseAuditTest.java`
- 验证结果：修正脚本已在真实 MySQL 幂等重放；相同请求返回 200、1 条记录。另新增全局分页参数校验，负 page、零 size 和非整数值返回 400。
- 状态：PASS

## INT-002 工作台动态标题渲染原始插值

- 问题编号：INT-002
- 模块：首页
- 页面：Dashboard
- 账号：任意具有 `statistics:view` 的账号
- 请求：无
- 方法：前端渲染
- 请求参数：无
- 响应状态：不适用
- 响应内容：页面显示 `近{{ overview.periodDays || 30 }}日迁入/迁出`
- 后端异常：无
- 根因：Vue 插值表达式放在普通属性字符串中，不会被再次解析。
- 归属：前端
- 修复方案：使用 computed 生成标题并通过 `:label` 绑定；增加源码级回归测试。
- 修改文件：`Dashboard.vue`、`dashboardRendering.test.js`
- 验证结果：前端单元测试和 Vite 构建通过，页面源码不再包含错误属性插值。
- 状态：PASS

## INT-003 首页统计出现误导性 0

- 问题编号：INT-003
- 模块：首页统计
- 页面：Dashboard / DataDashboard
- 账号：population
- 请求：`/api/dashboard/overview`、`/api/dashboard/charts?days=30&regionLimit=8`
- 方法：GET
- 请求参数：默认 period/expiry；`days=30,regionLimit=8`
- 响应状态：200
- 响应内容：概览 `activeResidencePermits=0`，状态分布同时返回 `VALID=1`
- 后端异常：无
- 根因：演示脚本将居住证状态写为旧值 `VALID`，冻结业务代码只认 `ACTIVE`；另工作台对统计失败没有显式错误区。
- 归属：数据库 / 前端
- 修复方案：演示状态改为 `ACTIVE` 并允许幂等重放；工作台用 null 占位和明确错误/重试，不以 0 覆盖失败。
- 修改文件：`demo_data.sql`、`Dashboard.vue`
- 验证结果：真实 MySQL 重放后概览 `activeResidencePermits=1`、`expiringResidencePermits=1`，图表为 `ACTIVE=1`；前端针对性测试通过。
- 状态：PASS

## INT-004 待办加载失败仅显示通用重试

- 问题编号：INT-004
- 模块：首页待办
- 页面：Dashboard
- 账号：按权限触发
- 请求：pending applications / my applications / expiring permits
- 方法：GET
- 请求参数：各模块默认筛选
- 响应状态：401/403/500 等
- 响应内容：原页面只显示布尔错误和“加载失败，可重试”
- 后端异常：按具体请求
- 根因：`WorkItemList` 的 error 属性只有 Boolean，页面丢弃服务端 message。
- 归属：前端
- 修复方案：error 支持字符串，调用方使用统一 `getApiErrorMessage`，保留重试。
- 修改文件：`WorkItemList.vue`、`Dashboard.vue`
- 验证结果：前端单元测试和 Vite 构建通过，工作台显示服务端 message 并保留重试。
- 状态：PASS

## INT-005 认证保持与服务端退出未接入

- 问题编号：INT-005
- 模块：认证
- 页面：全局 store / MainLayout
- 账号：全部
- 请求：缺少 `/api/auth/me` 与 `/api/auth/logout`
- 方法：GET / POST
- 请求参数：Bearer token
- 响应状态：尚未调用
- 响应内容：尚未调用
- 后端异常：无
- 根因：前端仅持久化登录响应和本地清除 token，刷新时不向后端校验，退出时不进入 JWT 撤销链。
- 归属：前端
- 修复方案：阶段 A 接入 me/logout，并测试刷新保持与旧 token 401。
- 修改文件：待阶段 A
- 验证结果：未验证
- 状态：FRONTEND_FIX

## INT-006 前端残留 Backend V1 不支持的调用

- 问题编号：INT-006
- 模块：人口、家庭户、用户、认证
- 页面：API 层及隐藏 UserList
- 账号：不适用
- 请求：`DELETE /persons/{id}`、`DELETE /households/{id}`、`GET /users`、`POST /auth/register`
- 方法：GET/POST/DELETE
- 请求参数：不适用
- 响应状态：404/405 或未触发
- 响应内容：Backend V1 无对应正式接口
- 后端异常：无
- 根因：前端阶段版本保留旧接口导出和注释。
- 归属：前端
- 修复方案：后续模块阶段删除旧导出/路由，离户改用专用 POST；用户在线 CRUD 标记 OUT_OF_SCOPE。
- 修改文件：待后续阶段
- 验证结果：静态审计已定位；未发现 `/api/residents` 调用。
- 状态：CONTRACT_MISMATCH
