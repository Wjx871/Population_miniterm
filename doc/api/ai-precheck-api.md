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

首次上传只有在数据库状态为 `READY` 且私有对象确认存在后才返回 `201`。相同 owner、endpoint、key 与规范化请求只有在原材料仍为 `READY`、未过期且对象存在时才返回 `200` 和原 `stageMaterialId`。`UPLOADING` 返回 `409 MATERIAL_UPLOAD_IN_PROGRESS`，带 `Retry-After: 1`，提示约 1 秒后用同一 key 重试；`UPLOAD_FAILED`、`CANCELLED`、`EXPIRED`、`IN_USE` 或 `BOUND` 均返回 `409` 并保留原状态。READY 记录对应对象缺失时会标成 `UPLOAD_FAILED`，返回 `409 MATERIAL_OBJECT_MISSING`。幂等重试不重新激活任何不可用材料。

请求指纹包含服务端计算的文件 SHA-256、规范化业务类型、角色提示、检测到的内容类型、NFC 规范化展示文件名和小写文件扩展名。同键任一指纹字段不同均返回 `409 IDEMPOTENCY_KEY_REUSED`。文件名保留大小写用于展示；路径分隔符、Windows 路径保留字符、控制/格式字符和过长名称会被拒绝。Unicode NFC 等价名称会存储为同一展示名并产生相同语义指纹。唯一约束为 `(owner_user_id, idempotency_endpoint, idempotency_key)`；并发重放对现有行做数据库行锁复核，重复请求只删除自己生成的临时对象。

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

## S4-A 实体解析预览

### `POST /api/ai/tasks/{taskId}/resolution-preview`

需要 `ai:precheck:view` 与 `migration:in:create`。任务必须属于当前用户、为 `DOCUMENT_PRECHECK` / `MIGRATION_IN` 且处于 `SUCCEEDED`；请求中的 `taskVersion` 必须等于服务端当前版本。非本人任务与不存在任务统一 `404`，未完成任务、版本冲突和业务上下文冲突返回 `409`。未知 `requestSchemaVersion` 返回 `422`。

```json
{
  "requestSchemaVersion": "1.0",
  "taskVersion": 2,
  "corrections": [
    { "fieldCode": "person.name", "value": "示例姓名" },
    { "fieldCode": "person.idCard", "value": "110105194912310011" },
    { "fieldCode": "household.householdNo", "value": "S4A-H-ALPHA" }
  ],
  "migrationType": "OUTSIDE_CITY",
  "toRegionCode": "110105",
  "transferBatchNo": null
}
```

仅接受 `person.name`、`person.idCard`、`household.householdNo` 和 `household.address` 四种修正字段；同一字段不可重复。修正值只作为查询文本，经 NFC/空白规范化与字段校验后使用。人员按规范化证件号在当前 `DataScopeCriteria` 内精确查询，并要求姓名一致；目标户优先按户号精确查询。地址只能在当前范围内作为辅助候选提示，所有候选仍须由用户显式选择。客户端不能提交人员/家庭户主键、实体版本事实、权限范围或候选集合。

`resolutionStatus` 为 `RESOLVED_SINGLE`、`SELECTION_REQUIRED` 或 `NEEDS_CORRECTION`。唯一候选也必须显式选择。识别字段缺失/冲突、人员未建档或不匹配、已有当前户籍/有效成员关系、目标户不为 `ACTIVE`、区划不一致、跨区批次无效/已使用等情况会返回安全 warning；未匹配和超出范围使用一致的 `ENTITY_NOT_FOUND`，不暴露越权实体。`NEEDS_CORRECTION` 可含部分候选，但 `confirmable=false`，后续确认校验必须拒绝该会话。

响应仅返回必要的脱敏展示字段、`entityVersion`、不透明 candidateToken 和安全 warning，不返回数据库主键、完整证件号或完整地址；AI 预览地址始终脱敏，即便调用者具备敏感信息完整查看权限。`resolutionToken` 与 candidateToken 均为高熵随机值；数据库只保存 SHA-256 摘要。candidateToken 绑定父会话及实体类型。会话默认 5 分钟过期，可用 `AI_RESOLUTION_TOKEN_TTL` 配置。同一用户/任务再次生成预览时，事务会撤销该任务此前所有仍有效会话；旧 resolutionToken 和 candidateToken 不能与新会话组合。

确认服务在同一数据库事务内调用校验原语并完成草稿和材料绑定；该原语使用 `Propagation.MANDATORY`，不会单独提交或提前释放锁。令牌错误返回语义：缺失 resolutionToken、其他用户或其他任务的 resolutionToken、缺失或类型不符 candidateToken 均统一 404；仍存在的已过期/撤销会话及不可确认的 `NEEDS_CORRECTION` 会话返回 409。缺少确认权限返回 403；越权资源统一 404，DataScope、任务或实体快照变化安全返回 409。安全保留期清理后已删除的令牌按不存在返回 404。确认成功后 session 标记为 CONSUMED，并在安全保留期后仅清理 session/candidate；确认回执、正式申请与材料绑定事实独立保留。

令牌会话保留策略默认启用：每小时最多删除 100 条已超过 30 天安全保留期的过期 ACTIVE、REVOKED 或 CONSUMED 会话，并通过外键级联清理候选，不删除确认回执、材料绑定或预审审计。可通过 `AI_RESOLUTION_CLEANUP_ENABLED`、`AI_RESOLUTION_SESSION_RETENTION`、`AI_RESOLUTION_CLEANUP_BATCH_SIZE`、`AI_RESOLUTION_CLEANUP_POLL_MS` 配置；测试和演示使用 H2。

规范化输入摘要绑定 taskId/taskVersion、迁入类型、目标区划、迁出批次、完整任务 OCR 结果 SHA-256、解析/修正字段和冲突集合；另一个摘要绑定当前用户、权限、DataScope、部门、区划与授权区划集合。人员候选快照包含 person.version、状态、全部户籍与成员关系；家庭户快照包含 household.version/status/regionCode、成员关系、户籍及在途迁入。确认在事务中再次锁定并核对任务、会话、令牌、当前权限与范围、人员状态/户籍/有效成员关系、家庭户状态/区划/版本/成员/户籍、迁出批次状态及使用情况。

S4-A 只负责实体解析预览和事务校验原语；S4-B 确认入口及草稿/材料绑定见本节后文。真实 Python 联调、真实 RabbitMQ 联调和前端联调均为 `NOT_VERIFIED`。

## 权限

V4_015 只向 `HOUSEHOLD_MANAGER` 和 `SYSTEM_ADMIN` 授予 AI 预审创建/查看权限；V4_018 向同样的最小角色集授予 `ai:precheck:confirm`。创建任务要求 `ai:precheck:create` 与 `migration:in:create`；S4-A 预览要求 `ai:precheck:view` 与 `migration:in:create`；S4-B 确认要求 `ai:precheck:confirm` 与 `migration:in:create`。各入口都会核验当前用户所有权。传统材料、人口、迁入和审批 API 的权限与语义未改变。

## S4-B 显式确认并创建迁入草稿

### `POST /api/ai/tasks/{taskId}/confirm`

仅在 `AI_PRECHECK_ENABLED=true` 时注册。需要 Bearer JWT、`ai:precheck:confirm` 和 `migration:in:create`；任务须属于当前用户且为 `DOCUMENT_PRECHECK`、`MIGRATION_IN`、`SUCCEEDED`。新权限由 V4_018 仅授予现有 `HOUSEHOLD_MANAGER`、`SYSTEM_ADMIN` 角色。确认只创建 `business_application.status=DRAFT` 与 `migration_in.business_status=DRAFT`。

确认请求使用 schema 1.0，接受 S1 G.1 的 `fieldCode/value` 列表。Controller 对此接口单独拒绝未知 JSON 属性和重复 JSON 键；确认字段代码也必须在服务端白名单内。不能提交 personId、toHouseholdId、applicationId、materialId、ownerUserId、DataScope、entityVersion、requiredFlag、核验结果或执行状态。

```json
{
  "requestSchemaVersion": "1.0",
  "taskVersion": 3,
  "resolutionToken": "res_opaque_from_preview",
  "selectedCandidates": {
    "person": "pc_opaque_from_preview",
    "targetHousehold": "hc_opaque_from_preview"
  },
  "idempotencyKey": "confirm-synthetic-20261105-a",
  "confirmedFields": [
    {"fieldCode": "person.name", "value": "合成姓名"},
    {"fieldCode": "person.idCard", "value": "合成环境中的证件值"},
    {"fieldCode": "household.householdNo", "value": "S4A-H-ALPHA"},
    {"fieldCode": "migration.migrationType", "value": "OUTSIDE_CITY"},
    {"fieldCode": "migration.toRegionCode", "value": "110105"},
    {"fieldCode": "migration.fromAddress", "value": "合成迁出地址"},
    {"fieldCode": "migration.inDate", "value": "2026-11-05"},
    {"fieldCode": "migration.reason", "value": "合成迁入事由"},
    {"fieldCode": "migration.title", "value": "合成迁入申请"}
  ],
  "acknowledgedAlerts": [],
  "materialBindings": [],
  "householdEvidenceType": null
}
```

`person.name`、`person.idCard`、`household.householdNo`、`household.address`、迁入类型、目标区划和迁出批次是预览绑定输入。preview 与 confirm 调用同一规范化器；其摘要包含 taskId/taskVersion、完整任务 OCR 结果 SHA-256、规范化的 OCR/纠正解析字段、冲突集合、迁入类型、目标区划和批次。确认端从持久化任务结果重新计算并比较；变化返回 `409 RESOLUTION_INPUT_CHANGED`。仅保存摘要，不记录证件原文或完整地址。确认还必须显式提交迁入业务字段；`fromRegionCode` 与 `remark` 可空，遵循现有 `MigrationInRequest` 校验，不做服务端推断。

首次成功返回 `201`，含 applicationId、applicationNo、migrationDetailId、创建状态 DRAFT、当前申请状态、正式材料摘要与缺件 warnings。相同用户/任务/key/规范化请求回放返回 `200` 和原回执；回执在权限与任务归属核验后读取，不依赖解析令牌仍有效。回执返回 `creationStatus=DRAFT` 及实时 `currentStatus`。同 key 不同请求返回 `409 IDEMPOTENCY_KEY_REUSED`；同任务换 key 再确认返回 `409 TASK_ALREADY_CONSUMED`。幂等回执按任务唯一；任务行锁和数据库唯一约束共同防止并发创建多份草稿。

当前 S4-A 规定含 warning 的会话不可确认，所以 `acknowledgedAlerts` 必须为空；不能借此绕过 `NEEDS_CORRECTION`。缺少显式 person/targetHousehold 候选返回 `422 CANDIDATE_SELECTION_REQUIRED`；越权/不存在任务、候选或暂存材料统一 `404`；权限不足 `403`；过期/撤销/不可确认会话、字段摘要、实体或业务状态变化均安全返回 `409`。

### 正式材料绑定

`materialBindings` 的 `stageMaterialId` 必须属于当前用户及当前成功任务、业务类型为 `MIGRATION_IN`、状态为未过期 `READY`，且文件真实存在并通过大小/SHA-256 校验。绑定顺序按 stage ID 排序。UNKNOWN 必须由用户显式选择白名单标准码；APPLICATION_FORM 不能改标为 MIGRATION_PROOF。`householdEvidenceType` 只接受 `HOUSEHOLD_BOOK` 或 `ADDRESS_PROOF`：两类均有时必须显式选择其一；仅有一类时自动选唯一项；均缺失时允许 DRAFT 并返回 warning。Java 计算 requiredFlag，每条必需规则只选一个确定性代表文件；所有 AI 绑定材料的 verifyStatus 均为 PENDING。

正式材料保留受控的 `AI_STAGE` 来源和不透明 stage 引用，并由数据库绑定表解析真实暂存对象键。读取适配器不依赖受 `population.ai.precheck.enabled` 条件控制的 `AiPrivateFileStorageService`。AI 关闭后既有绑定材料仍可通过正式材料权限下载；客户端不能传路径或对象键。BOUND 状态不会被 S3 暂存清理器选择。删除 DRAFT/RETURNED 材料时先在数据库事务中移除正式材料并把绑定标为 RELEASE_PENDING；提交后清理私有对象。失败后保留可重试记录，清理任务可在进程重启后恢复。普通 LOCAL 材料仍使用原存储行为。

V4_018 新增确认回执、暂存绑定和正式材料来源字段及确认权限；应用后再部署 S4-B 代码。H2/Mock 测试不代表 MySQL 实际迁移、RabbitMQ、Python OCR 或前端联调通过；这些真实验收项按演示记录保持 `NOT_VERIFIED`。
