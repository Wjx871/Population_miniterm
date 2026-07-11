# 注销管理 API

人员原因：`DEATH`、`DECLARED_DEAD`、`SETTLED_ABROAD`、`DUPLICATE_REGISTRATION`、`OTHER_APPROVED`。家庭户原因：`NO_ACTIVE_MEMBERS`、`HOUSEHOLD_MERGED`、`ADDRESS_INVALIDATED`、`OTHER_APPROVED`。这些是课程模拟规则，不等同于具体城市政务规定。

材料按原因要求死亡证明/宣告死亡文书/境外定居证明/重复登记证明，并配合申请人身份证明与户口簿；家庭户要求注销申请、户口簿及销户或合并证明。必需材料必须全部 `VERIFIED`。

| 方法 | URL | 权限 | 范围 | 说明 |
|---|---|---|---|---|
| POST | `/api/cancellations/person/applications` | `cancellation:person:create` | REGION/ALL | 创建人员注销草稿 |
| PUT | `/api/cancellations/person/applications/{id}` | 上述权限+`application:edit` | SELF | 修改本人草稿 |
| POST | `/api/cancellations/household/applications` | `cancellation:household:create` | REGION/ALL | 创建家庭户销户草稿 |
| PUT | `/api/cancellations/household/applications/{id}` | 上述权限+`application:edit` | SELF | 修改本人草稿 |
| GET | `/api/cancellations/applications/{id}` | `cancellation:view` | 申请数据范围 | 完整专业详情 |
| POST | `/api/cancellations/person/applications/{id}/execute` | `cancellation:execute` | REGION/ALL | 显式执行人员注销 |
| POST | `/api/cancellations/household/applications/{id}/execute` | `cancellation:execute` | REGION/ALL | 显式执行家庭户销户 |
| GET | `/api/cancellations` | `cancellation:view` | SELF/DEPARTMENT/REGION/ALL | 注销综合查询 |
| GET | `/api/household-archives` | `cancellation:archive:view` | REGION/ALL | 家庭户归档查询 |
| GET | `/api/household-archives/{id}` | `cancellation:archive:view` | REGION/ALL | 归档详情 |

人员创建示例：`{"personId":1,"cancelReasonCode":"DEATH","eventDate":"2026-07-13","newHeadPersonId":2,"title":"居民死亡注销申请","reason":"办理注销"}`。执行请求为 `{"version":2}`。

状态为 `DRAFT → UNDER_REVIEW → APPROVED → COMPLETED`，也可进入 `REJECTED/WITHDRAWN`。审批通过不修改户籍。冲突、重复执行或不满足前置条件返回 409；无权限返回 403。无完整信息查看权时身份证号显示为前四位、十个星号、后四位。
