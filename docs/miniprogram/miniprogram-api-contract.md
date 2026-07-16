# 微信小程序 V1 API 契约

审计基线：`origin/develop@47fa77f`（2026-07-14）。契约来源为当前 Controller、DTO/VO、Service 数据范围与脱敏逻辑、权限注解及 `docs/integration/frontend-backend-contract.md`，不依据旧前端猜测接口。

## 公共协议

- Base URL 由 `miniprogram/config/index.js` 统一提供；路径均包含 `/api`。
- 除登录和匿名健康检查外，请求头携带 `Authorization: Bearer <token>`。
- JSON 成功响应为 `{ code, message, data, timestamp }`，业务层消费 `data`。Spring 分页为零基 `page`，响应为 `Page` 的 `content/totalElements/number/size`。
- `400` 展示后端 `message`；`401` 清理会话并只跳转一次登录页；`403` 提示权限不足但不退出；`404` 提示记录不存在或无权查看；`409` 展示业务/版本冲突；`500` 显示系统服务异常，不伪装为空数据。
- 数据范围由服务端按 `ALL/DEPARTMENT/REGION/SELF` 执行。客户端只展示范围，不自行放宽或复制判断规则。

## 页面接口矩阵

| 页面/能力 | Method 与路径 | 请求 | 响应 `data` | 权限 | 数据范围 | 状态 |
|---|---|---|---|---|---|---|
| 登录 | `POST /api/auth/login` | `{username,password}` | `LoginVO {token,tokenType,expiresIn,user}` | 匿名 | — | 已实现 |
| 恢复身份 | `GET /api/auth/me` | — | `UserVO`，含角色、权限、数据范围 | authenticated | 当前用户 | 已实现 |
| 退出 | `POST /api/auth/logout` | — | `null` | authenticated | 当前用户 | 已实现 |
| 健康状态 | `GET /api/health` | — | 健康摘要 | 匿名 | — | 已实现 |
| 工作台统计 | `GET /api/dashboard/overview?periodDays=30&expiryDays=30` | 查询参数 | `registeredPopulation,pendingApprovals` 等 | `statistics:view` | 服务端范围 | 已实现 |
| 家庭户总数 | `GET /api/households?page=0&size=1` | Spring 分页 | `Page<HouseholdView>`，读取 `totalElements` | `household:view` | 服务端范围 | 已实现 |
| 人口列表 | `GET /api/persons` | `name,idCard,status,page,size` | `Page<Person>` | `population:view` | 服务端范围并脱敏 | 已实现 |
| 人口详情 | `GET /api/persons/{id}` | 路径 ID | `Person` | `population:view` | 服务端范围并脱敏 | 已实现 |
| 家庭户列表 | `GET /api/households` | `householdNo,headPersonName,status,page,size` | `Page<HouseholdView>` | `household:view` | 服务端范围并脱敏地址 | 已实现 |
| 家庭户详情 | `GET /api/households/{id}` | 路径 ID | `HouseholdView` | `household:view` | 服务端范围并脱敏 | 已实现 |
| 家庭户成员 | `GET /api/households/{id}/members` | 路径 ID | `HouseholdMemberView[]` | `household:view` | 服务端范围并脱敏 | 已实现 |
| 我的申请 | `GET /api/applications` | `applicationNo,businessType,status,applicantName,page,size` | `Page<ApplicationView>` | `application:view` | 申请人/审批查看权限与服务端范围 | 已实现 |
| 申请详情 | `GET /api/applications/{id}` | 路径 ID | `ApplicationView` | `application:view` | 服务端范围 | 已实现 |
| 审批轨迹 | `GET /api/applications/{id}/approval-logs` | 路径 ID | `ApprovalLogView[]` | `application:view` 或 `approval:view` | 服务端范围 | 已实现 |
| 申请材料 | `GET /api/applications/{id}/materials` | 路径 ID | `MaterialView[]` | `material:view` | 申请可见性 | 已实现 |
| 待审批 | `GET /api/approvals/pending` | — | `ApprovalSummary[]` | `approval:view` | 服务端范围 | 已实现 |
| 已审批 | `GET /api/approvals/processed` | — | `ApprovalSummary[]` | `approval:view` | 服务端范围 | 已实现 |
| 审批详情 | `GET /api/approvals/{id}` | 路径 ID | `{approval,application,materials,logs}` | `approval:view` | 服务端范围 | 已实现 |
| 通过 | `POST /api/approvals/{id}/approve` | `{comment,version}` | `null` | `approval:handle` 且 L3 | 服务端范围、不可自审、材料已核验 | 已实现 |
| 驳回 | `POST /api/approvals/{id}/reject` | `{comment,version}`，意见必填 | `null` | `approval:handle` 且 L3 | 服务端范围、不可自审 | 已实现 |
| 材料下载 | `GET /api/materials/{id}/download` | 路径 ID | 文件流 | `material:view` | 申请可见性 | 已实现 |

## 专业详情只读映射

通用申请详情先加载成功；随后按 `businessType` 和当前权限尝试加载专业摘要。专业记录 404/403 时页面保留通用详情并明确提示，不白屏。

| 业务类型 | Method 与路径 | 权限 |
|---|---|---|
| `MIGRATION_IN/OUT` | `GET /api/migrations/applications/{applicationId}` | `migration:view` |
| `PERSON_CANCELLATION/HOUSEHOLD_CANCELLATION` | `GET /api/cancellations/applications/{applicationId}` | `cancellation:view` |
| `FLOATING_REGISTRATION` | `GET /api/floating-registrations/applications/{applicationId}` | `floating:view` |
| 居住证三类申请 | `GET /api/residence-permits/applications/{applicationId}` | `residence-permit:view` |
| `KEY_POPULATION_REGISTER` | `GET /api/key-populations/register-applications/{applicationId}` | `key-population:view` |
| `KEY_POPULATION_RELEASE` | `GET /api/key-populations/release-applications/{applicationId}` | `key-population:view` |
| `SENSITIVE_DATA_EXPORT` | `GET /api/exports/applications/{applicationId}` | 对应敏感导出/审批权限 |

## 状态与并发

- 申请：`DRAFT 草稿`、`SUBMITTED 已提交`、`UNDER_REVIEW 审批中`、`APPROVED 已通过`、`REJECTED 已驳回`、`WITHDRAWN 已撤回`、`COMPLETED 已完成`、`CANCELLED 已取消`。
- 审批：`PENDING 待审批`、`APPROVED 已通过`、`REJECTED 已驳回`、`CANCELLED 已取消`。
- 家庭户：`ACTIVE 有效`、`PENDING_CANCELLATION 待注销`、`ARCHIVED 已归档`、`CANCELLED 已注销`。
- 材料核验状态以服务端枚举为准并在客户端中文化。
- 审批操作必须提交详情最新 `approval.version`。重复审批或版本变化返回 `409`，刷新详情后再处理。
- 审批通过仅将申请置为 `APPROVED`；不会自动执行专业业务，也不向审批人授予任何专业 `execute` 权限。

## 后端变更结论

V1 所需接口均已存在，本阶段新增接口 0、修改接口 0、后端业务代码修改 0。
