# S3 材料暂存与异步预审 API

S3 新增接口默认关闭：需显式设置 `AI_PRECHECK_ENABLED=true`。所有接口均使用现有 Bearer JWT；当前用户身份、组织与区划快照由服务端读取。没有新增 `/internal/**` 路由。

## 材料暂存

### `POST /api/ai/materials`

`multipart/form-data`，字段：

| 字段 | 必需 | 说明 |
|---|---|---|
| `file` | 是 | PDF、JPEG 或 PNG；服务端校验大小、扩展名、MIME 和文件签名。默认最大 10 MiB。 |
| `businessType` | 否 | 目前只接受 `MIGRATION_IN`，默认该值。 |
| `documentRoleHint` | 否 | `IDENTITY_CARD_FRONT`、`IDENTITY_CARD_BACK`、`HOUSEHOLD_REGISTER_PAGE`、`ADDRESS_PROOF`、`TRANSFER_CERTIFICATE`、`CROSS_DISTRICT_TRANSFER`、`APPLICATION_FORM` 或 `UNKNOWN`，默认 `UNKNOWN`。这是提示，不是识别结论。 |
| `Idempotency-Key` 请求头 | 是 | 1–128 位 ASCII 字母、数字、点、下划线或连字符。 |

首次成功返回 `201` 和材料摘要；相同 owner、endpoint、key 与规范化请求再次上传时返回 `200` 和原 `stageMaterialId` 及其当前状态。请求指纹包含服务端计算的文件 SHA-256、`MIGRATION_IN`、角色提示和检测到的内容类型；同键不同指纹返回 `409 IDEMPOTENCY_KEY_REUSED`。唯一约束为 `(owner_user_id, idempotency_endpoint, idempotency_key)`。幂等重试只读回原记录，不会重开 `CANCELLED`、`EXPIRED`、`UPLOAD_FAILED` 或其他不可用材料。

成功响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "stageMaterialId": "stg_0123456789abcdef0123456789abcdef",
    "status": "READY",
    "businessType": "MIGRATION_IN",
    "documentRoleHint": "IDENTITY_CARD_FRONT",
    "originalFilename": "synthetic-proof.pdf",
    "contentType": "application/pdf",
    "fileSize": 128,
    "sha256": "...",
    "createdAt": "2026-11-04T12:00:00",
    "expiresAt": "2026-11-05T12:00:00"
  }
}
```

原始文件仅存于私有存储目录，API 不返回对象键或绝对路径。文件名会被安全化并仅用于显示。`GET /api/ai/materials/{stageMaterialId}` 只能由创建者查看；`DELETE` 只可取消仍可用的暂存材料。其他用户和不存在的 ID 使用相同 `404`。删除只改变暂存状态并安排物理文件删除；正在使用或已绑定材料不会被删除。

## 预审任务

### `POST /api/ai/tasks`

需要 `ai:precheck:create` 与 `migration:in:create`。首版只接受：

```json
{
  "requestSchemaVersion": "1.0",
  "taskType": "DOCUMENT_PRECHECK",
  "businessType": "MIGRATION_IN",
  "migrationType": "OUTSIDE_CITY",
  "stageMaterialIds": ["stg_0123456789abcdef0123456789abcdef"],
  "idempotencyKey": "precheck-request-20261104-a"
}
```

材料数为 1–10，ID 不可重复，并须全部属于当前用户、尚未过期且处于 `READY`。任务幂等作用域为当前用户、任务类型、endpoint 与 key；规范化指纹包括版本、任务/业务类型、迁入类型及排序后的暂存 ID。相同请求返回原任务；同键不同请求返回 `409 IDEMPOTENCY_KEY_REUSED`。

只有任务、材料占用、容量预留、Outbox 事件和接受审计都在同一个数据库事务提交后，接口才返回 `202 Accepted`；Broker 发布不在 HTTP 请求的接受事务内。容量不足为 `429 AI_PRECHECK_CAPACITY_REACHED`，不会返回 taskId。成功示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "task_0123456789abcdef0123456789abcdef",
    "taskType": "DOCUMENT_PRECHECK",
    "status": "PENDING",
    "createdAt": "2026-11-04T12:00:00",
    "requestSchemaVersion": "1.0",
    "resultSchemaVersion": "1.0",
    "traceId": "...",
    "pollAfterMs": 1000
  }
}
```

### `GET /api/ai/tasks/{taskId}`

需要 `ai:precheck:view`，且只有创建者能读取；跨用户和不存在任务都返回统一 `404`。任务状态为 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED` 之一。只有 `SUCCEEDED` 返回 `result`，只有 `FAILED` 返回安全 `failure`。成功只表示结构化分析流程完成，不表示材料合格、身份真实或业务获批。Mock 结果会包含 `mode=MOCK`，并明确提示人工复核。

## 权限

迁移 `V4_015_ai_precheck_async.sql` 只向 `HOUSEHOLD_MANAGER` 和 `SYSTEM_ADMIN` 授予新增 AI 预审权限。创建任务还要求既有 `migration:in:create`。查看和材料删除都再次核验当前用户所有权。传统材料、人口、迁入和审批 API 的权限与语义未改变。
