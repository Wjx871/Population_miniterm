# 重点人口 API

所有接口使用 `ApiResponse<T>`、Bearer JWT 和 `@PreAuthorize`。查询权限为 `key-population:view`：`GET /api/key-populations`、`/{recordId}`、`/{recordId}/history`、`GET /api/persons/{personId}/key-populations`。列表支持人员、姓名、身份证、类型、关注等级、状态、责任部门、区划、建档日期和分页条件。数据范围在 Mapper SQL 中关联 person/residence 执行；越权详情返回 404。身份证、手机和地址默认脱敏，完整值仅向 `sensitive-data:view-full` 开放。

## 建档

1. `POST /api/key-populations/register-applications`（`key-population:apply`）创建 DRAFT 专业申请。
2. 通过通用材料接口上传并核验必需的 `KEY_POPULATION_BASIS` 和 `SITUATION_DESCRIPTION`。
3. `POST /api/key-populations/register-applications/{applicationId}/submit` 提交现有审批流程。
4. approver 使用现有 `/api/approvals/{approvalId}/approve|reject`；审批不会写入当前记录。
5. APPROVED 后，`POST /api/key-populations/register-applications/{applicationId}/execute`（`key-population:execute`，请求 `{"version":2}`）显式建档。

类型来自启用的 `KEY_POPULATION_TYPE` 字典；关注等级为 `LOW/MEDIUM/HIGH`。人员不存在/越权为 404，类型或日期错误为 400，重复 ACTIVE、处理中申请或状态/版本冲突为 409。

## 解除

`POST /api/key-populations/{recordId}/release-applications` 创建草稿；材料、提交和审批规则同建档。审批通过仍保持 ACTIVE；只有 `POST /api/key-populations/release-applications/{applicationId}/execute` 才更新为 RELEASED、填写解除原因/日期并追加历史。重复解除或重复执行返回 409，没有 DELETE 接口。

专业申请详情分别为 `GET /api/key-populations/register-applications/{id}` 和 `/release-applications/{id}`。当前状态为 DRAFT → UNDER_REVIEW → APPROVED/REJECTED → 显式执行后 COMPLETED；历史事件为 REGISTERED、RELEASED，只追加且快照不含身份证、手机或地址。
