# 流动人口与居住证 API

流动人口登记是当前区域居住事实，不是户籍迁入；居住登记也不等于取得居住证。所有接口使用 JWT、现有 `ApiResponse` 和数据范围规则。身份证、手机号和普通角色可见的证件号会脱敏。

| 方法 | URL | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/floating-registrations/applications` | `floating:create` | 创建通用申请及登记详情 |
| PUT | `/api/floating-registrations/applications/{id}` | `floating:edit` + `application:edit` | 修改本人草稿 |
| GET | `/api/floating-registrations/applications/{id}` | `floating:view` | 专业申请详情 |
| POST | `/api/floating-registrations/applications/{id}/execute` | `floating:execute` | 显式生成 ACTIVE 登记 |
| GET | `/api/floating-populations` | `floating:view` | 按数据范围分页查询 |
| GET | `/api/floating-populations/{id}` | `floating:view` | 当前或历史登记详情 |
| POST | `/api/floating-populations/{id}/close` | `floating:close` | 关闭登记并联动证件 |
| POST | `/api/residence-permits/applications/first-issue` | `residence-permit:apply` | 首次申领 |
| POST | `/api/residence-permits/applications/{permitId}/endorsement` | `residence-permit:apply` | 创建签注申请 |
| POST | `/api/residence-permits/applications/{permitId}/cancellation` | `residence-permit:apply` | 创建注销申请 |
| PUT | `/api/residence-permits/applications/{id}` | `residence-permit:apply` + `application:edit` | 修改本人草稿 |
| GET | `/api/residence-permits/applications/{id}` | `residence-permit:view` | 专业申请详情 |
| POST | `/api/residence-permits/applications/{id}/issue` | `residence-permit:issue` | 显式签发 |
| POST | `/api/residence-permits/applications/{id}/endorse` | `residence-permit:endorse` | 显式签注 |
| POST | `/api/residence-permits/applications/{id}/cancel` | `residence-permit:cancel` | 显式注销 |
| GET | `/api/residence-permits` | `residence-permit:view` | 证件分页查询 |
| GET | `/api/residence-permits/{id}` | `residence-permit:view` | 证件详情 |
| GET | `/api/residence-permits/{id}/logs` | `residence-permit:log:view` | 生命周期时间线 |
| GET | `/api/residence-permits/expiring` | `residence-permit:expiry:view` | 即将到期列表 |

创建登记请求包含 `personId/sourceRegionCode/currentRegionCode/currentAddress/residenceReasonCode/residenceProofType/arrivalDate/title/reason`。执行请求为 `{"version":2}`。关闭请求示例：`{"reasonCode":"LEFT_REGION","comment":"已离开本区","version":0}`。签发请求示例：`{"issuingAuthority":"示例公安机关","version":2}`。

专业业务类型为 `FLOATING_REGISTRATION`、`RESIDENCE_PERMIT_FIRST_ISSUE`、`RESIDENCE_PERMIT_ENDORSEMENT`、`RESIDENCE_PERMIT_CANCELLATION`，通用创建接口返回 400。字段校验返回 400；无权限返回 403；不存在返回 404；状态、版本、材料或唯一性冲突返回 409。

材料规则为课程模拟规则：登记需要身份、地址及事由材料；首次申领增加照片；签注需要当前证和持续居住证明；注销需要当前证和注销申请。批准前必需材料必须 VERIFIED。

自动到期按配置 cron 扫描，提醒接口默认返回未来配置天数内的 ACTIVE 证件。所有打印信息必须显示：**课程模拟系统生成，不作为真实政务证件**。
