# 第一阶段：认证与 RBAC 仓库审计

## 1. 审计基线

- 起始分支：`main`；起始提交：`5ef41cb`。开始工作前的 6 项文档变更经确认后单独提交，随后功能分支基于 `origin/main@d1f946c` 创建。
- 后端：Java 17、Spring Boot 3.5.3、Spring Web、Validation、普通 MyBatis 3.0.5、MySQL 8、Log4j2；分页对象来自 Spring Data Commons，并未使用 JPA。
- 前端：Vue 3、Vite 8、Element Plus、Pinia、Vue Router、Axios、ECharts。
- 测试：JUnit 5、Spring Boot Test、H2（MySQL 模式）。审计时已有应用上下文及 Person/Resident Mapper 测试。

## 2. 原有结构与状态

主包为 `com.wjx871.population`，按 `auth`、`common`、`person`、`resident`、`stats`、`system` 组织；Mapper SQL 位于 `src/main/resources/mapper`。前端统一位于 `frontend`，已有登录页、Pinia 用户状态、Axios 封装和路由守卫。

数据库已有 `sys_user`、`sys_role`、`sys_permission`、`sys_role_permission` 和 `operation_log`。联合表使用 `(role_id, permission_id)` 联合主键，符合唯一性要求。原结构缺少部门、角色编码/等级/数据范围、权限类型/状态、用户部门及最后登录信息，日志也缺少请求上下文字段。

原登录页虽然调用 `/api/auth/login`，但后端仅在 Controller 中硬编码 `admin/123456` 并返回固定字符串 `mock-jwt-token-123456`；没有 BCrypt、JWT 验签、Security 过滤链、接口认证或方法权限控制。Axios 已有 token 请求头和路由守卫雏形，但响应模型与 401/403 处理不完整。统一返回格式是 `ApiResponse<T>(code, message, data, timestamp)`，该结构继续保留。

## 3. 目标差异与本阶段修改

本阶段增量加入：`sys_department`；角色等级 `L1/L2/L3`；数据范围 `ALL/DEPARTMENT/REGION/SELF`；五类角色、权限和测试账号；BCrypt、真实 JWT、无状态 Spring Security、JSON 401/403、登录/失败/退出/越权日志；认证用户上下文及数据范围条件；真实前端登录与退出；H2 集成测试。

代表性权限覆盖：

- `GET /api/persons`：`population:view`，L1 可访问。
- `POST /api/persons`：`population:edit`，L1 被拒绝，具备权限的 L2/L3 可访问。
- `GET /api/statistics/logs`：`log:view`，并作为数据范围过滤示例。

除上述代表性接口外，既有业务接口目前只受“必须登录”的 URL 级保护，尚未逐个添加业务权限注解。后续应按模块逐步覆盖，不能误认为当前已经完成全接口细粒度授权。

## 4. 数据范围约定

- `ALL`：不增加范围条件。
- `DEPARTMENT`：按当前用户 `department_id` 过滤。
- `REGION`：按部门的 `region_code` 过滤。
- `SELF`：按 `operator_id`、`created_by` 或日志的 `user_id` 过滤。

本阶段仅在最近操作日志查询中示范该规则，没有重写人口、户籍、迁移和证件模块的全部 SQL。

## 5. 明确不修改的内容

本阶段不实现通用申请、材料管理、审批流、销户、户籍归档、迁入迁出事务重构、敏感导出审批或重点人口审批；不删除现有人口、居民、户籍、迁移和证件模块；不改为 JPA/MyBatis-Plus；不重做前端页面及视觉样式。

## 6. 后续审批模块预留

后续审批应复用 `AuthenticatedUser`、`RoleLevel`、`DataScope`、`CurrentUserContext` 和权限编码，业务表建议统一预留 `applicant_id`、`operator_id/created_by`、`department_id`、`region_code`、`approval_status`、`current_approver_id`、`submitted_at`、`approved_at` 与版本字段。审批操作需新增独立申请/流程/意见表，并通过服务接口衔接，不能把流程状态直接堆进现有 Controller。本阶段没有提前创建这些业务表。
