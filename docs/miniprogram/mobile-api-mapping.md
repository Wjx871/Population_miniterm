# 微信小程序接口映射

所有请求均通过 `miniprogram/services/request.js`，使用配置中的统一 `BASE_URL`、Bearer Token、`ApiResponse.data` 解包和统一错误处理。GET 参数会统一移除 `undefined`、`null` 和空字符串，同时保留合法分页参数。

| 页面/能力 | 正式接口 | 方法 | 权限 | 说明 |
| --- | --- | --- | --- | --- |
| 工作台概览 | `/api/dashboard/overview` | GET | `statistics:view` | 人口、审批、迁移、到期指标及生成时间 |
| 家庭户数量 | `/api/households` | GET | `household:view` | 读取分页总数，不伪造缺失值 |
| 我的处理中申请 | `/api/applications` | GET | `application:view` | 分别查询提交、审批中、已通过并汇总真实总数 |
| 人口列表/详情 | `/api/persons`、`/api/persons/{id}` | GET | `population:view` | 复用既有页面 |
| 人口综合档案 | `/api/query/person-profile/{id}` | GET | `population:view` | 家庭关系、当前居住证、迁移摘要 |
| 家庭户列表/详情/成员 | `/api/households`、`/api/households/{id}`、`/api/households/{id}/members` | GET | `household:view` | 复用既有页面 |
| 居住证列表/详情 | `/api/residence-permits`、`/api/residence-permits/{id}` | GET | `residence-permit:view` | 状态、有效期、脱敏编号 |
| 即将到期居住证 | `/api/residence-permits/expiring?days=30` | GET | `residence-permit:expiry:view` | 工作台跳转及签注候选 |
| 流动人口列表/详情 | `/api/floating-populations`、`/api/floating-populations/{id}` | GET | `floating:view` | 居住登记只读查询 |
| 迁移记录 | `/api/query/migration-history` | GET | `migration:view` | 只读列表和详情，不提供新增 |
| 申请列表/详情 | `/api/applications`、`/api/applications/{id}` | GET | `application:view` | 状态和基础信息 |
| 申请日志/材料 | `/api/applications/{id}/approval-logs`、`/api/applications/{id}/materials` | GET | 对应查看权限 | 时间线、审批结果、材料列表 |
| 材料下载 | `/api/materials/{id}/download` | GET | 对应材料权限 | 图片预览或文档打开 |
| 撤回申请 | `/api/applications/{id}/withdraw` | POST | `application:withdraw` | 仅后端允许状态显示按钮 |
| 创建签注草稿 | `/api/residence-permits/applications/{permitId}/endorsement` | POST | `residence-permit:apply` | 业务类型 `RESIDENCE_PERMIT_ENDORSEMENT` |
| 上传签注材料 | `/api/applications/{id}/materials` | POST multipart | `material:upload` | 两项正式必需材料 |
| 提交申请 | `/api/applications/{id}/submit` | POST | 正式提交权限 | 提交审批，不执行业务 |
| 审批列表/详情 | 现有 approval Service 映射 | GET | `approval:view` | 待审批和已审批 |
| 审批通过/驳回 | 现有 approval Service 映射 | POST | `approval:handle` | 只做审核决定 |

## 明确禁止的移动端调用

小程序没有接入 `/api/residence-permits/applications/{id}/endorse`，也没有接入迁入、迁出、注销等专业执行接口。审批通过不触发自动执行。

## 签注请求与材料

创建草稿使用正式 `PermitApplicationRequest` 中适合移动端的字段：`title`、`reason`、`remark`、`residenceBasisCode`。空的可选字段在调用前移除，不会变为字符串 `"undefined"`。

提交前必须上传：

- `CURRENT_RESIDENCE_PERMIT`：当前居住证。
- `CONTINUED_RESIDENCE_PROOF`：持续居住证明。

申请创建成功后使用正式返回的 `applicationId` 和 `applicationNo`，不生成前端编号。

## 后端影响

本轮未修改后端 Java、Mapper、数据库、正式路径、请求字段、响应结构、状态枚举、权限码、JWT 或审批执行流程。
