> S1 REVIEW DRAFT<br>
> 尚未完成全组及教师确认<br>
> 不得视为最终冻结接口

# 跨组接口契约评审稿

## 0. 契约范围与事实基线

本文所有新增路径、消息和字段均为 PROPOSED，不是当前实现，也没有获得全组冻结。目标场景是户籍迁入材料预审后，由工作人员核对并创建迁入 DRAFT。AI 只返回可解释的识别建议和业务规则提示。本稿同时作为后端 S2/S3 的推荐契约基线；无外部服务依赖的代码可先使用 Mock 和兼容适配，不以四位成员逐项签字作为开工门槛。

CURRENT 代码事实：

- 正式材料入口是 POST /api/applications/{id}/materials。MaterialService.upload 先读取现存 applicationId，校验申请所有者和 DRAFT/RETURNED 状态，然后写 application_material。
- V4_002_business_application_approval.sql 定义 application_material.application_id BIGINT NOT NULL，并外键引用 business_application.application_id。因此当前材料表不能在申请创建前直接承载新流程的材料。
- 正式材料记录含 materialType、materialName、文件名、内部存储路径、MIME、大小、SHA-256、requiredFlag、verifyStatus、uploadedBy。verifyStatus 在上传时为 PENDING。
- LocalFileStorageService 只允许 PDF、JPEG、PNG，使用服务端生成文件名和本地私有目录；路径不应返回客户端。
- 当前正式迁入入口 POST /api/migrations/in/applications 独立完成创建 DRAFT 所需的业务校验，不调用 AI、Python 或 MQ。
- 当前代码路径为 POST /api/persons/idcard-image（单数）；这是人口身份证影印本接口，不是通用迁入材料暂存接口。
- 当前无 AI task API、RabbitMQ 生产/消费、Python 文档预审或两个拟议 Python 内部接口。

所有新增客户端入口仍由 Java 承担业务授权。Python 只分析输入和返回建议；Python 返回值、OCR 文本、前端字段和候选编号都不能直接成为数据库主键或审批结论。

### 0.1 共用版本和响应约定（PROPOSED）

| 项 | 契约建议 |
|---|---|
| 客户端业务 API | JSON UTF-8；继续用当前 ApiResponse<T> 外层结构。上传返回 ApiResponse 元数据，不返回存储路径。 |
| 内部服务 API | JSON UTF-8；服务间认证；不把浏览器 Bearer Token 转发给 Python。内部错误体固定为 schemaVersion、requestId、taskId（若有关联）、error.code、error.retryable、error.safeMessage、traceId。 |
| 时间 | RFC 3339，带时区；Java 展示按现有 Asia/Shanghai 配置，存储使用明确时区。 |
| schemaVersion | 本文示例用字符串 1.0。兼容性规则见消息和内部接口章节；新增可选字段允许向后兼容，删除字段或改变字段含义须升主版本。 |
| traceId | Java 接收或生成并贯穿 HTTP、任务、MQ、Java-Python 调用和日志；不可作为授权凭据。 |
| 敏感内容 | 不记录证件原文、文件内容、真实文件路径、用户 Token、服务凭据或 Python 堆栈。 |

## A. 预上传材料及生命周期

### A.1 两种暂存方案比较

| 方案 | 做法 | 优点 | 代价与风险 | 结论 |
|---|---|---|---|---|
| A：通用预审材料记录 + 私有文件存储 | 正式申请前写独立的暂存元数据，记录随机材料 ID、创建者、文件元数据、生命周期和内部文件引用；任务只引用这些 ID。确认创建草稿时，在同一数据库事务创建业务申请、迁入详情、application_material，并把暂存记录标为 BOUND。 | 符合“先上传、后正式申请”；创建者和状态可查询；任务与文件引用可审计；外键语义清楚；不需要伪造 applicationId。 | 后续需新增迁移和过期清理；文件系统与数据库不能天然同事务，需要避免过早移动/删除。 | 推荐用于 S4；S1 只提议，不建表。 |
| B：先在后台创建正式 DRAFT，再将文件直接写入现有 application_material | 前端进入 AI 流程时先创建迁入 DRAFT，随后复用现有上传接口和材料规则。 | 复用现有外键、权限、存储和下载服务；文件表不需要独立引用关系。 | 正式申请在用户完成预审或显式确认前已存在；容易产生大量废弃草稿；流程语义变为“先建申请”；不满足本轮指定的先暂存、后建草稿体验。 | 可作为产品调整后的备选，需张蕊和业务负责人同意。 |
| C：复用 person_idcard_image 的 draft_uuid | 将身份证草稿表当作通用材料暂存。 | 已有 user_id、draft_uuid、SHA、文件引用及跨用户检查。 | 表名、OCR 字段、状态及业务用途均绑定身份证影印本；不能表达户口簿、申请表、地址证明和任务集合；容易混淆人口建档与迁入。 | 不建议复用；只借鉴其按创建者隔离和随机草稿标识思路。 |

首选 A 作为后端侧推荐基线（PROPOSED）。S2/S3 可据此完成独立接口和 Mock 工作；文件卷、留存期限和 Python 读取方式在真实服务接入时与相关主责人对齐。

### A.2 客户端暂存 API（PROPOSED）

| 方法与路径 | 用途 | 鉴权建议 |
|---|---|---|
| POST /api/ai/materials | multipart 上传一份预审材料，返回随机 stageMaterialId。 | Bearer JWT；建议同时要求 ai:precheck:create 和 migration:in:create。 |
| GET /api/ai/materials/{stageMaterialId} | 读取当前用户自己的暂存元数据和状态。 | Bearer JWT；默认只允许创建者读取。 |
| DELETE /api/ai/materials/{stageMaterialId} | 取消尚未绑定、也未被活动任务占用的暂存对象。 | Bearer JWT；默认只允许创建者操作。 |

上传请求字段：

| 字段 | 类型 | 必填 | 语义 |
|---|---|---:|---|
| file | multipart 文件 | 是 | 仅接受通过后端文件扩展名、MIME、大小和内容校验的文件；沿用现有 PDF/JPEG/PNG 限制作为首选。 |
| businessType | string | 是 | 首版限定 MIGRATION_IN。不得由模型改写。 |
| documentRoleHint | string | 否 | 前端或用户选择的文档类别提示；只作提示，不能直接变成最终 materialType。 |
| Idempotency-Key | header | 是（建议） | 限定同一用户、同一接口；用于网络重试，不用于跨用户去重。 |

成功响应建议返回 stageMaterialId、status=READY、displayName、contentType、sizeBytes、uploadedAt、expiresAt、schemaVersion。响应不得包含 storage_path、stored_filename、服务器绝对路径、访问令牌或证件内容。

暂存对象建议含 ownerUserId、ownerDepartmentId、ownerRegionCode 快照、stageMaterialId、displayName、contentType、sizeBytes、sha256、internalObjectRef、createdAt、expiresAt、status、boundApplicationId（绑定前为空）。对象 ID 使用至少 128 位随机值，不使用可顺序枚举的数据库主键。SHA 仅在服务器内部用于完整性和同一用户幂等核验，不作为可查询的跨用户去重接口。

### A.3 生命周期、归属和存储（PROPOSED）

| 状态 | 含义 | 允许的后继 |
|---|---|---|
| UPLOADING | 收到上传，尚未完成元数据登记。 | READY、UPLOAD_FAILED |
| READY | 文件已落入私有存储，归属及元数据已落库，可关联任务。 | IN_USE、BOUND、CANCELLED、EXPIRED |
| IN_USE | 至少一个非终态分析任务正在引用该材料。 | READY、BOUND、EXPIRED |
| BOUND | 已绑定正式 application_material；后续使用正式材料权限和生命周期。 | 由正式材料规则决定，不再由暂存清理器删除 |
| UPLOAD_FAILED | 上传或校验失败，不能被任务引用。 | 终态；由清理流程删除残留文件 |
| CANCELLED | 创建者取消且没有活动任务或正式绑定。 | 终态；保留短期审计元数据后清理文件 |
| EXPIRED | 超过配置期限，且没有活动任务或正式绑定。 | 终态；清理文件并保留必要审计摘要 |

建议预审材料 READY 默认保留 24 小时；任务运行时延后删除；任务终结后最多再保留 24 小时供用户确认；AI 结果建议保留 7 日后清除或脱敏归档。以上期限是 PROPOSED 后端默认值；文件保留策略在实际部署时由数据运维核实，不作为 S2/S3 Mock 开工条件。取消任务功能不在首版状态机中；用户若要取消活动任务，暂存 DELETE 返回 409 MATERIAL_IN_USE，任务允许完成或按失败处理，不伪造 task 状态。

文件处理建议：

1. Java 校验文件名、MIME、内容格式、大小，生成随机内部文件名并保存到非 Web Root 的私有存储。
2. 每一暂存记录都绑定当前用户；查询、下载、任务引用均使用“对象 ID + 当前用户/受权数据范围”查询，不允许先按 ID 读取再在 Controller 做过滤。
3. 未授权 ID 与不存在 ID 对外统一返回 404 MATERIAL_NOT_FOUND，避免枚举存在性；同用户的过期、已绑定或不可用对象可返回明确 409。
4. 默认不跨用户 SHA 去重，防止通过重复响应探测其他人的上传。相同用户相同 Idempotency-Key 重传时返回同一结果；同 Key 不同文件返回 409 IDEMPOTENCY_KEY_REUSED。
5. 数据库写入失败时删除刚写的文件；删除也失败时记为孤儿并由后续清理任务按内部对象引用核对。不可因上传失败删除其他文件。
6. 推荐数据库事务内写 application_material 和 BOUND 状态；优先让预审对象与正式附件引用同一私有文件对象，不在事务提交前移动/删除文件。若最终设计必须复制文件，则通过可重放的绑定事件和补偿流程处理，不声称文件系统与 MySQL 原子提交。
7. 上传申请表可被识别和显示，但当前迁入材料规则没有 APPLICATION_FORM 标准码。建议把 APPLICATION_FORM 作为新候选标准码，首版 requiredFlag=false，作为后端候选类型；实际材料清单对接时与 AI/前端侧对齐。不能把申请表误映射成 MIGRATION_PROOF。
8. 客户端可以给 documentRoleHint，模型可以给 detectedType；最终 materialType 由 Java 白名单映射并按 MIGRATION_IN 规则计算。requiredFlag 不接受客户端或 AI 决定。

### A.4 材料类型到正式编码的建议映射（PROPOSED）

| AI/用户可识别文档类别 | 正式 materialType 建议 | 现有迁入规则中的用途 |
|---|---|---|
| IDENTITY_CARD_FRONT / IDENTITY_CARD_BACK | IDENTITY_PROOF | 迁入必需 |
| HOUSEHOLD_REGISTER_PAGE | HOUSEHOLD_BOOK | 与 ADDRESS_PROOF 二选一满足迁入基本材料规则 |
| ADDRESS_PROOF | ADDRESS_PROOF | 与 HOUSEHOLD_BOOK 二选一 |
| TRANSFER_CERTIFICATE / CROSS_DISTRICT_TRANSFER | MIGRATION_PROOF | IN_CITY_CROSS_DISTRICT 额外必需 |
| HOUSEHOLD_CONSENT | HOUSEHOLD_CONSENT | 现有码值用于迁出户主/成员同意场景，不自动作为迁入必需 |
| APPLICATION_FORM | APPLICATION_FORM（新码候选） | 当前迁入规则不要求此类型；是否纳入标准字典在材料规则实际对接时确认 |
| UNKNOWN / 多义类别 | 不映射 | 返回需人工分类；不能猜成某个必需材料 |

当前物理列 material_type 是 VARCHAR(50)，没有数据库 ENUM；实际迁入规则在 MigrationMaterialRequirementService.validateIn 中检查 requiredFlag=true 的 IDENTITY_PROOF、HOUSEHOLD_BOOK/ADDRESS_PROOF，以及跨区时 MIGRATION_PROOF。拟议 API 应让 Java 按业务规则设置 requiredFlag，而不是相信上传表单。

### A.5 Python 受控读取材料（PROPOSED）

RabbitMQ 事件只携带 taskId、eventId、taskType、schemaVersion、traceId、occurredAt 等必要元数据，不携带 stageMaterialId、证件原文、二进制内容、绝对路径、服务凭据或 JWT。消费者先用 taskId 读取 MySQL 中持久化的任务记录，再从该任务关联中取得 stageMaterialId；任务记录是材料引用的事实源。首选由 Python worker 使用独立服务身份经内网调用 Java 的受控内容流接口：

- 建议 Java 内部端点：POST /internal/ai/materials:resolve。
- 请求：taskId、stageMaterialId、requestId。stageMaterialId 必须来自该 taskId 读取到的持久化关联，不能由 MQ 消息或 Python 任意指定。
- Java 校验服务身份、任务与材料关联、任务未终结、材料创建者和状态；材料不存在或超出该任务引用集合时返回统一拒绝。
- 响应为文件流及可信 MIME、大小、短期 content digest；不返回文件系统路径。
- 接口仅允许有 document:read 能力的服务身份；采用 mTLS 或短时、定 audience、可轮换的服务令牌。令牌只在 TLS 服务间认证中使用，不写日志、不放 MQ。
- 共享只读文件卷是备选方案，挂载和双实例一致性在实际部署对接时与赵书亚对齐。若采用卷，Python 仍只收到内部对象引用，不能获得用户选择路径或绕过 Java 的用户对象授权。

当前 SecurityConfig 对 /api/** 要求认证，但 anyRequest().permitAll()。所以未来增加 /internal/** 时必须显式定义服务间认证/授权过滤器和路由规则；仅依靠“路径不对浏览器公开”或内网部署不构成授权。

## B. AI 异步任务 API 与状态（PROPOSED）

### B.1 创建任务

POST /api/ai/tasks 创建材料预审任务；建议要求 ai:precheck:create 与 migration:in:create。数据库提交任务和 Outbox 事件后返回 HTTP 202；Broker 暂时不可用时，若任务和 Outbox 已持久化，仍可受理并保持 PENDING。

请求示例：

~~~json
{
  "requestSchemaVersion": "1.0",
  "taskType": "DOCUMENT_PRECHECK",
  "businessType": "MIGRATION_IN",
  "migrationType": "OUTSIDE_CITY",
  "stageMaterialIds": ["stg_opaque_a1"],
  "idempotencyKey": "client-generated-uuid"
}
~~~

| 字段 | 类型 | 必填 | 约定 |
|---|---|---:|---|
| requestSchemaVersion | string | 是 | 首版为 1.0；未知主版本返回 422 UNSUPPORTED_SCHEMA_VERSION。 |
| taskType | enum | 是 | 首版仅 DOCUMENT_PRECHECK。 |
| businessType | enum | 是 | 首版仅 MIGRATION_IN。 |
| migrationType | enum | 是 | OUTSIDE_CITY 或 IN_CITY_CROSS_DISTRICT；用于生成材料规则和后续确认上下文。 |
| stageMaterialIds | string[] | 是 | 1—10 个去重的不透明 ID；每项都必须属于当前用户且为 READY。 |
| idempotencyKey | string | 是 | 用户侧唯一键；键作用域为 userId + taskType + endpoint；同键同请求返回同一 taskId，同键不同规范化请求返回 409。 |

任务持久化记录保存 migrationType、stageMaterialIds、创建者、规则版本及幂等请求摘要；MQ 只引用 taskId。成功返回 envelope 的 data 建议含 taskId、taskType、status=PENDING、acceptedAt、taskSchemaVersion=1.0、resultSchemaVersion=1.0、traceId、pollAfterMs。受理只说明请求已经持久化，不表示材料齐全、AI 已完成或预审通过。

### B.2 查询任务

GET /api/ai/tasks/{taskId} 返回状态、结果或失败摘要。建议默认仅创建者可见；尚未绑定正式 application 的任务没有可据以扩大访问范围的业务申请。若以后允许主管人员查看，必须增加显式任务查看权限，并以任务创建时保存的组织/区划快照重新执行 DataScope 校验。禁止通过任务列表或错误差异枚举其他用户任务。

成功 data 建议字段：

| 字段 | 类型 | 空值/语义 |
|---|---|---|
| taskId | string | 不透明任务 ID。 |
| status | enum | PENDING、RUNNING、SUCCEEDED 或 FAILED。 |
| progress | integer/null | 0—100 的估计处理进度；null 表示未提供，不代表失败或成功。不能用它代替状态。 |
| result | object/null | 仅 SUCCEEDED 且调用人获授权时返回；包含 resultSchemaVersion 和 AI 建议。 |
| failure | object/null | 仅 FAILED 时返回安全错误码、可重试性与简短说明；不返回堆栈、证件原文或内部路径。 |
| createdAt / startedAt / completedAt | timestamp/null | 未开始或未完成时对应字段为 null。 |
| traceId | string | 跨进程追踪 ID，不是授权令牌。 |
| taskSchemaVersion / resultSchemaVersion | string | 标识请求/存储和结果结构版本。 |

### B.3 状态机、重试和错误语义（PROPOSED）

~~~mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING: 消费者取得任务
    RUNNING --> SUCCEEDED: 结果持久化成功
    RUNNING --> PENDING: 可恢复失败且尚有重试次数
    RUNNING --> FAILED: 永久错误或重试耗尽
    SUCCEEDED --> [*]
    FAILED --> [*]
~~~

仅 PENDING、RUNNING、SUCCEEDED、FAILED 四个任务状态。RUNNING 回到 PENDING 仅代表受限的自动重试或工作租约过期回收；最多建议 3 次处理尝试。SUCCEEDED 和 FAILED 为终态。需要再次分析时创建新 taskId 和新幂等键，不重开旧任务。

| 状态/条件 | 行为 | 用户理解 |
|---|---|---|
| PENDING | 等待消息发送或 worker；允许用户查询。 | 已受理，尚无预审结论。 |
| RUNNING | worker 正在读取材料或分析；记录开始时间和尝试次数。 | 正在处理，不是预审通过。 |
| RUNNING→PENDING | 暂时网络超时、Python 503 或 worker 租约失效，且仍有尝试次数；使用退避后再投递。 | 处理中/等待重试。 |
| SUCCEEDED | 结构校验通过且分析结果与任务状态同事务持久化。 | 分析完成。结果仍可能包含材料缺失、字段冲突、低置信度或需人工核对。 |
| FAILED | 输入永久无效、未知 schema、结果不可解析，或有限重试耗尽。 | 分析没有完成；显示安全失败原因。 |
| SUCCEEDED 后的人工修正 | 创建独立确认动作/草稿，不改写历史任务状态。 | 任务仍为 SUCCEEDED；用户确认版本另行审计。 |

“任务已受理”不等于“预审通过”。SUCCEEDED 表示计算流程完成，不表示材料合格、字段真实、正式申请可提交或业务获批。模型结果和材料缺失/冲突都应作为结果字段展示，不能把 task 状态扩展成业务审批状态。

建议错误响应如下；实际 HTTP 状态按公共 API 统一封装：

| HTTP | code | retryable | 说明 |
|---:|---|---:|---|
| 400 | INVALID_REQUEST | false | 字段缺失、格式错误或枚举非法。 |
| 401 | AUTHENTICATION_REQUIRED | false | JWT 缺失、过期或无效。 |
| 403 | PERMISSION_DENIED | false | 已识别用户但缺少业务权限。 |
| 404 | RESOURCE_NOT_FOUND | false | 任务/材料不存在或不属于调用人，统一响应以避免枚举。 |
| 409 | IDEMPOTENCY_KEY_REUSED | false | 同一幂等键对应不同请求。 |
| 409 | MATERIAL_NOT_READY | false | 材料过期、已绑定、已取消或不能用于该业务。 |
| 413 | FILE_TOO_LARGE | false | 上传体超过配置上限。 |
| 415 | UNSUPPORTED_MEDIA_TYPE | false | 上传格式不在允许范围。 |
| 422 | UNSUPPORTED_SCHEMA_VERSION | false | 请求 schema 主版本不受支持。 |
| 429 | RATE_LIMITED | true | 服务实施限流时超出当前用户/服务配额。 |
| 503 | PERSISTENCE_UNAVAILABLE | true | 无法可靠写入任务或 Outbox；未受理，不返回 taskId。 |

失败对象建议：code、retryable、safeMessage、failedAt、attemptCount、traceId。超时和内部异常不得包含 URL 凭据、物理文件路径、原始 Python 响应或 Java 堆栈。

### B.4 任务权限与结果可见性

- Java 从 Spring Security 当前用户上下文取得 userId，不采信 body 中的 ownerUserId、role、department 或 DataScope。
- 创建任务时一次性验证所有 stageMaterialIds 均属于当前调用人、业务类型匹配、状态为 READY、未过期且没有被其他活动任务排他占用。
- task 记录所有者和创建时组织/区划快照用于审计；授权判断仍由 Java 执行。DataScope 不从模型输出或请求参数读取。
- 查询、结果下载和确认创建草稿都要再次校验任务所有者/权限；未知 taskId 和越权 taskId 对普通用户返回相同 404。
- 结果默认仅创建者读取。授权 L3 核验人员只能在正式申请已创建并进入既有流程后使用现有材料核验权限；不能因其能核验 application_material 自动获得预审任务访问权。
- 任务结果不得包含可直接用来创建数据库主键的 personId 或 toHouseholdId。候选解析由 Java 重新查库并输出受控显示值或短期候选引用。

## C. RabbitMQ 消息契约（PROPOSED；只定义数据和行为）

### C.1 消息体

首版事件名建议 DOCUMENT_PRECHECK_REQUESTED，schemaVersion 为 1.0。

~~~json
{
  "eventId": "evt-demo-001",
  "taskId": "task-demo-001",
  "taskType": "DOCUMENT_PRECHECK",
  "schemaVersion": "1.0",
  "traceId": "trace-demo-001",
  "occurredAt": "2026-11-03T10:30:00+08:00",
  "producer": "population-java"
}
~~~

消息不得出现身份证号、姓名、户号、OCR 字段、文件二进制、原始文件名、存储路径、访问令牌或凭据。消费者依据 taskId 读取 Java 持久化任务，依据任务关联的 stageMaterialId 走受控文件流。

### C.2 可靠性和重复处理

1. 在同一 MySQL 事务中写 AI task 与 outbox event；仅提交后才由发布器尝试发送。
2. 发布器使用 RabbitMQ publisher confirm；只有确认成功后标记该 outbox event 已发布。确认丢失时允许重复投递。
3. 消息投递语义按至少一次设计。消费者用 eventId/taskId、任务状态版本和 inbox/唯一约束做幂等；同一 task 不得产生两份有效结果或两份业务草稿。
4. 消费者先取得任务租约并以条件更新 PENDING→RUNNING，再读取材料和调用 Python。重复消息发现任务已 RUNNING、SUCCEEDED 或 FAILED 时不得重复执行写操作；活动租约冲突时确认消息已由当前 worker 处理。
5. 只有结果、失败原因和任务状态已持久化后才 ACK。若在 ACK 前进程中断，Broker 可重投，消费者按任务状态和幂等记录恢复。
6. 对网络超时、Python 503 等临时故障最多 3 次尝试，退避间隔建议 2s、10s、30s 并加抖动；输入不合法或 schema 不支持不重试。次数和间隔属于 PROPOSED 后端起点，真实 Broker/Python 接入时可按环境和推理耗时调整。
7. 超过重试次数或 poison message 进入死信；同步把任务置为 FAILED、failureCode=RETRY_EXHAUSTED 或 MESSAGE_SCHEMA_UNSUPPORTED，保留 traceId 和最后错误分类。死信不是唯一业务记录，用户可查任务状态。
8. Worker 启动时恢复过期租约和待发 Outbox；Broker 临时不可用不丢数据库中的任务。运维摘要需显示待发事件、Ready/Unacked/DLQ 计数和最旧等待时间。
9. 消息 schema 采用只增兼容：字段只可追加为可选；消费者在部署期间同时支持当前及紧邻的前一兼容版本。未知主版本进入 DLQ 并告警，不静默忽略字段或猜测语义。

内部交换机、队列、绑定、持久化等级及具体 Broker 参数留给 S3/赵书亚定稿；S1 不创建组件、数据库表、队列或配置。

## D. Java→Python 文档分析（PROPOSED）

内部接口：POST /internal/document-analysis。只允许经过服务间认证的 Java 任务消费者调用；浏览器不能直连 Python，也不能提交 Python 接受的主键或文件路径。

### D.1 请求

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-schema-001",
  "taskId": "task-schema-001",
  "traceId": "trace-schema-001",
  "businessType": "MIGRATION_IN",
  "migrationType": "OUTSIDE_CITY",
  "materialRequirements": [
    {
      "ruleId": "MIGRATION_IDENTITY",
      "allOf": [{"materialType": "IDENTITY_PROOF", "minCount": 1}]
    },
    {
      "ruleId": "MIGRATION_BOOK_OR_ADDRESS",
      "anyOf": [
        {"materialType": "HOUSEHOLD_BOOK", "minCount": 1},
        {"materialType": "ADDRESS_PROOF", "minCount": 1}
      ]
    },
    {
      "ruleId": "MIGRATION_TRANSFER_PROOF",
      "when": {"migrationType": "IN_CITY_CROSS_DISTRICT"},
      "allOf": [{"materialType": "MIGRATION_PROOF", "minCount": 1}]
    }
  ],
  "materials": [
    {
      "materialRef": "stg_schema_id_1",
      "contentType": "image/jpeg",
      "sizeBytes": 245000,
      "sourceRoleHint": "IDENTITY_CARD_FRONT"
    },
    {
      "materialRef": "stg_schema_book_1",
      "contentType": "application/pdf",
      "sizeBytes": 420000,
      "sourceRoleHint": "HOUSEHOLD_REGISTER_PAGE"
    }
  ]
}
~~~

| 字段 | 类型 | 必填 | 语义 |
|---|---|---:|---|
| schemaVersion | string | 是 | 内部请求结构版本。 |
| requestId / taskId / traceId | string | 是 | Java 生成并贯穿全链路的关联值。 |
| businessType | enum | 是 | 首版 MIGRATION_IN。 |
| migrationType | enum | 是 | Java 从持久化任务上下文传入，值为 OUTSIDE_CITY 或 IN_CITY_CROSS_DISTRICT。 |
| materialRequirements | object[] | 是 | Java 根据 businessType、migrationType 和现行规则计算的约束。allOf 表示全部满足；anyOf 表示至少满足其中一个分支。此处 ruleId 是规则标识，HOUSEHOLD_BOOK_OR_ADDRESS 不是 materialType。 |
| materials | object[] | 是 | 仅列任务持久化记录所关联且服务端授权读取的材料引用；每项含 opaque materialRef、由 Java 校验的 MIME/大小和非权威用户提示类别。 |
| sourceRoleHint | enum/string | 否 | 用户/前端建议的类别，明确不可信；Python 可返回自己的 detectedType。 |

materialRequirements 内每个 materialType 都必须是当前业务允许的正式标准码；逻辑组合名称只出现在 ruleId。Python 只能用这些规则产生缺件提醒，不拥有规则最终决定权。Java 通过 taskId 读取持久化 migrationType 和材料关联后构造请求，不从 MQ 读取材料列表。

Java-Python 请求不带用户 Token、数据库主键、绝对路径或直接凭据。文件通过 A.5 的受控 Java 内容接口取得。
### D.2 响应与空值规则

建议响应字段：

| 字段 | 类型 | 语义 |
|---|---|---|
| schemaVersion、requestId、taskId、traceId | string | 与请求关联，Java 必须校验 requestId/taskId 一致。 |
| analysisDisposition | enum | ANALYZED、NEEDS_REVIEW、INSUFFICIENT_INPUT。只描述分析输出状态，不是审批结论。 |
| materials | object[] | 每份输入的 materialRef、detectedType、typeConfidence、字段列表。 |
| fields[].fieldCode | string | 版本化字段路径，例如 person.name、person.idCard、household.householdNo、application.reason。 |
| fields[].value | string/number/date/null | null 表示没有可用提取值；空字符串不作为“未识别”的替代。 |
| fields[].fieldStatus | enum | PRESENT、NOT_FOUND、UNREADABLE、NOT_APPLICABLE。 |
| fields[].confidence | number/null | 0—1 的识别置信度；null 表示模型未给出该指标，0 是明确最低分。不是字段正确概率。 |
| fields[].source | object/null | 有证据时包含 materialRef、page（从 1 开始）和归一化 boundingBox x/y/width/height；无证据时为 null。 |
| alerts[] | object[] | 缺件、低置信度、跨材料冲突、无法识别、类型不符；包含 code、severity、fieldCodes、materialRefs、safeMessage；材料规则告警另含 ruleId 和 alternatives，alternatives 只列有效 materialType 候选码。 |
| modelVersion、ocrEngineVersion、ruleSetVersion | string/null | 记录实际使用的模型、OCR 与规则版本；未使用时为 null。 |
| elapsedMs | integer | Python 服务内部耗时；Java 另记录完整调用耗时。 |
| error.materialRefs | string[]/optional | 失败时可原样回显本次请求引用，便于追踪；必须是本请求 materials 的子集，不能生成新引用。 |

Python 的材料类型分类是建议。Java 负责把 detectedType 映射到 A.4 标准码，应用提交仍以 Java MigrationMaterialRequirementService 的规则重算。Python 返回 200 表示结构化分析成功，不表示预审通过。HTTP 4xx/5xx 用 D.3 错误体表达。

### D.3 虚构请求/响应样例

以下四组均是完整的 Java→Python 请求和对应响应。requestId、taskId、traceId 一一对应；响应只能引用该组请求中的 materialRef，字段来源也必须指向本组输入。

#### 1. 正常识别

请求：

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-001",
  "taskId": "task-demo-001",
  "traceId": "trace-demo-001",
  "businessType": "MIGRATION_IN",
  "migrationType": "OUTSIDE_CITY",
  "materialRequirements": [
    {"ruleId": "MIGRATION_IDENTITY", "allOf": [{"materialType": "IDENTITY_PROOF", "minCount": 1}]},
    {"ruleId": "MIGRATION_BOOK_OR_ADDRESS", "anyOf": [{"materialType": "HOUSEHOLD_BOOK", "minCount": 1}, {"materialType": "ADDRESS_PROOF", "minCount": 1}]}
  ],
  "materials": [
    {"materialRef": "stg_demo_id_1", "contentType": "image/jpeg", "sizeBytes": 245000, "sourceRoleHint": "IDENTITY_CARD_FRONT"},
    {"materialRef": "stg_demo_book_1", "contentType": "application/pdf", "sizeBytes": 420000, "sourceRoleHint": "HOUSEHOLD_REGISTER_PAGE"}
  ]
}
~~~

响应：

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-001",
  "taskId": "task-demo-001",
  "traceId": "trace-demo-001",
  "analysisDisposition": "ANALYZED",
  "materials": [
    {
      "materialRef": "stg_demo_id_1",
      "detectedType": "IDENTITY_CARD_FRONT",
      "typeConfidence": 0.98,
      "fields": [
        {
          "fieldCode": "person.name",
          "value": "示例甲",
          "fieldStatus": "PRESENT",
          "confidence": 0.98,
          "source": {"materialRef": "stg_demo_id_1", "page": 1, "boundingBox": {"x": 0.12, "y": 0.18, "width": 0.20, "height": 0.05}}
        },
        {
          "fieldCode": "person.idCard",
          "value": "SIMULATED-ID-001",
          "fieldStatus": "PRESENT",
          "confidence": 0.93,
          "source": {"materialRef": "stg_demo_id_1", "page": 1, "boundingBox": {"x": 0.55, "y": 0.44, "width": 0.30, "height": 0.06}}
        }
      ]
    },
    {
      "materialRef": "stg_demo_book_1",
      "detectedType": "HOUSEHOLD_REGISTER_PAGE",
      "typeConfidence": 0.95,
      "fields": [
        {
          "fieldCode": "person.name",
          "value": "示例甲",
          "fieldStatus": "PRESENT",
          "confidence": 0.94,
          "source": {"materialRef": "stg_demo_book_1", "page": 2, "boundingBox": {"x": 0.10, "y": 0.22, "width": 0.18, "height": 0.05}}
        }
      ]
    }
  ],
  "alerts": [],
  "modelVersion": "demo-doc-model-1",
  "ocrEngineVersion": "demo-ocr-1",
  "ruleSetVersion": "migration-material-rules-1",
  "elapsedMs": 842
}
~~~

#### 2. 材料缺失

请求只包含身份证材料；materialRequirements 中的 anyOf 规则仍要求户口簿或地址证明至少一项。

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-002",
  "taskId": "task-demo-002",
  "traceId": "trace-demo-002",
  "businessType": "MIGRATION_IN",
  "migrationType": "OUTSIDE_CITY",
  "materialRequirements": [
    {"ruleId": "MIGRATION_IDENTITY", "allOf": [{"materialType": "IDENTITY_PROOF", "minCount": 1}]},
    {"ruleId": "MIGRATION_BOOK_OR_ADDRESS", "anyOf": [{"materialType": "HOUSEHOLD_BOOK", "minCount": 1}, {"materialType": "ADDRESS_PROOF", "minCount": 1}]}
  ],
  "materials": [
    {"materialRef": "stg_demo_id_2", "contentType": "image/jpeg", "sizeBytes": 232000, "sourceRoleHint": "IDENTITY_CARD_FRONT"}
  ]
}
~~~

响应：

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-002",
  "taskId": "task-demo-002",
  "traceId": "trace-demo-002",
  "analysisDisposition": "NEEDS_REVIEW",
  "materials": [
    {
      "materialRef": "stg_demo_id_2",
      "detectedType": "IDENTITY_CARD_FRONT",
      "typeConfidence": 0.90,
      "fields": []
    }
  ],
  "alerts": [
    {
      "code": "MATERIAL_REQUIREMENT_UNSATISFIED",
      "severity": "WARNING",
      "ruleId": "MIGRATION_BOOK_OR_ADDRESS",
      "alternatives": ["HOUSEHOLD_BOOK", "ADDRESS_PROOF"],
      "fieldCodes": [],
      "materialRefs": [],
      "safeMessage": "尚未提供户口簿或地址证明。"
    }
  ],
  "modelVersion": "demo-doc-model-1",
  "ocrEngineVersion": "demo-ocr-1",
  "ruleSetVersion": "migration-material-rules-1",
  "elapsedMs": 321
}
~~~

#### 3. 低置信度与字段冲突

两份输入分别识别出冲突姓名；Python 不选择哪份材料正确。

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-003",
  "taskId": "task-demo-003",
  "traceId": "trace-demo-003",
  "businessType": "MIGRATION_IN",
  "migrationType": "OUTSIDE_CITY",
  "materialRequirements": [
    {"ruleId": "MIGRATION_IDENTITY", "allOf": [{"materialType": "IDENTITY_PROOF", "minCount": 1}]},
    {"ruleId": "MIGRATION_BOOK_OR_ADDRESS", "anyOf": [{"materialType": "HOUSEHOLD_BOOK", "minCount": 1}, {"materialType": "ADDRESS_PROOF", "minCount": 1}]}
  ],
  "materials": [
    {"materialRef": "stg_demo_id_3", "contentType": "image/jpeg", "sizeBytes": 244000, "sourceRoleHint": "IDENTITY_CARD_FRONT"},
    {"materialRef": "stg_demo_book_3", "contentType": "application/pdf", "sizeBytes": 418000, "sourceRoleHint": "HOUSEHOLD_REGISTER_PAGE"}
  ]
}
~~~

响应：

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-003",
  "taskId": "task-demo-003",
  "traceId": "trace-demo-003",
  "analysisDisposition": "NEEDS_REVIEW",
  "materials": [
    {
      "materialRef": "stg_demo_id_3",
      "detectedType": "IDENTITY_CARD_FRONT",
      "typeConfidence": 0.91,
      "fields": [
        {
          "fieldCode": "person.name",
          "value": "示例甲",
          "fieldStatus": "PRESENT",
          "confidence": 0.58,
          "source": {"materialRef": "stg_demo_id_3", "page": 1, "boundingBox": {"x": 0.1, "y": 0.2, "width": 0.2, "height": 0.05}}
        }
      ]
    },
    {
      "materialRef": "stg_demo_book_3",
      "detectedType": "HOUSEHOLD_REGISTER_PAGE",
      "typeConfidence": 0.88,
      "fields": [
        {
          "fieldCode": "person.name",
          "value": "示例乙",
          "fieldStatus": "PRESENT",
          "confidence": 0.61,
          "source": {"materialRef": "stg_demo_book_3", "page": 2, "boundingBox": {"x": 0.2, "y": 0.3, "width": 0.2, "height": 0.05}}
        }
      ]
    }
  ],
  "alerts": [
    {
      "code": "FIELD_CONFLICT",
      "severity": "WARNING",
      "fieldCodes": ["person.name"],
      "materialRefs": ["stg_demo_id_3", "stg_demo_book_3"],
      "safeMessage": "不同材料中的姓名识别结果不一致，请人工对照原图。"
    },
    {
      "code": "LOW_CONFIDENCE",
      "severity": "WARNING",
      "fieldCodes": ["person.name"],
      "materialRefs": ["stg_demo_id_3", "stg_demo_book_3"],
      "safeMessage": "姓名识别置信度较低，请人工核对。"
    }
  ],
  "modelVersion": "demo-doc-model-1",
  "ocrEngineVersion": "demo-ocr-1",
  "ruleSetVersion": "migration-material-rules-1",
  "elapsedMs": 1104
}
~~~

#### 4. AI 服务失败

请求包含的引用仍可追踪；失败响应不伪造材料分析结果。

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-004",
  "taskId": "task-demo-004",
  "traceId": "trace-demo-004",
  "businessType": "MIGRATION_IN",
  "migrationType": "OUTSIDE_CITY",
  "materialRequirements": [
    {"ruleId": "MIGRATION_IDENTITY", "allOf": [{"materialType": "IDENTITY_PROOF", "minCount": 1}]},
    {"ruleId": "MIGRATION_BOOK_OR_ADDRESS", "anyOf": [{"materialType": "HOUSEHOLD_BOOK", "minCount": 1}, {"materialType": "ADDRESS_PROOF", "minCount": 1}]}
  ],
  "materials": [
    {"materialRef": "stg_demo_id_4", "contentType": "image/jpeg", "sizeBytes": 239000, "sourceRoleHint": "IDENTITY_CARD_FRONT"}
  ]
}
~~~

响应：

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-004",
  "taskId": "task-demo-004",
  "traceId": "trace-demo-004",
  "error": {
    "code": "MODEL_UNAVAILABLE",
    "retryable": true,
    "materialRefs": ["stg_demo_id_4"],
    "safeMessage": "文档分析服务暂时不可用，请稍后查看任务状态。"
  }
}
~~~

失败不是分析结果；Java 消费者按 retryable 和有限次数决定重试，最后一次才将任务置为 FAILED。错误体不包含真实地址、凭据、栈信息、原始文件名或证件字段。
### D.4 认证、超时与责任

内部服务需独立认证，最小授权限定 document:analyze；服务凭据只保存在受保护的运行环境，不进代码、浏览器、MQ 消息、日志或响应。首轮超时建议 connect 2 秒、document-analysis read 120 秒，单次调用由任务消费者承载而不阻塞用户 HTTP 请求；数值作为后端 Mock/适配起点；worker 租约和 AI 侧并发上限在真实 Python/Broker 联调时按实测调整。HTTP 超时按临时失败重试，若服务返回格式不合法则记录 AI_INVALID_RESPONSE 并按非重试或一次有限重试处理。正式规则仍由 Java 校验。

## E. Java→Python 政策问答（PROPOSED）

内部接口：POST /internal/policy-query，由 Java 现有政策助手编排；浏览器继续调用 Java 当前的 POST /api/assistant/policy/query。

### E.1 请求和响应字段

请求建议字段：schemaVersion、requestId、traceId、question、history（最多 6 轮）、businessType（可空）、regionCode（可空）、locale。question 最大长度建议沿用当前 500 字符限制。Java 清除或拒绝包含证件号、手机号、用户凭据等敏感个案数据；policy query 不读取人口主档。

成功响应建议字段：

| 字段 | 语义 |
|---|---|
| schemaVersion、requestId、traceId | 版本与链路校验字段。 |
| citationIdsUsed | string[] | 本答案实际引用的 citations.sourceId；Java 校验后重新编号引用标记。 |
| outcome | ANSWERED、INSUFFICIENT_EVIDENCE；服务错误由调用层表示。 |
| answer | 基于检索证据生成的简短政策回答；用 [[sourceId]] 标记引用；无依据时明确说无法确认并令 citationIdsUsed 为空。 |
| citations[] | sourceId、title、section、category、sourceType、excerpt、sourceVersion、effectiveFrom、effectiveTo、regionCode、retrievedAt。category 用于 Java 现有意图过滤。 |
| knowledgeBaseVersion | Python 当前使用的知识库快照/索引版本。 |
| modelVersion、retrievalVersion、rerankerVersion | 本次实际组件版本；未使用的组件为 null。 |
| evidenceLevel | SUFFICIENT、PARTIAL、INSUFFICIENT；检索分数不作为答案正确概率。 |
| elapsedMs | Python 本次处理耗时。 |

建议 connect 超时 2 秒、整体 read 超时 8 秒，不自动长时间重试。数值作为后端默认适配起点，真实 Python 接入时根据实测延迟调整。sourceMode 由 Java 调用路径设置而不是由 Python 自报。引用应是前端可展示的来源与版本，不返回内部绝对路径或服务凭据。

### E.2 Java 降级及对外模式

CURRENT 调用链必须保留。PolicyAssistantController.query 调用 PolicyTaskAgentService.execute；该方法负责意图识别、追问改写、查询扩展、调用 PolicyAssistantService.query、融合 citations、计算 evidenceStatus、权限过滤 nextActions，并由 buildSummary 构造 answerSummary。当前实现不会直接把 QueryResponse.answer 写入 AgentResponse.answerSummary；仅修改 PolicyAssistantService 去请求 Python，不能让 Python answer 自然进入当前公共响应。

PROPOSED Java→Python 兼容适配流程：

1. Java 仍先运行现有敏感信息规则。命中后不把问题发送 Python，维持现有 SAFE_REFUSAL 语义。
2. 保留 PolicyTaskAgentService 的 intent 分类、standaloneQuestion 改写、查询扩展和有限历史处理。它为每个查询调用 Java PolicyAssistantService；启用 Python 的请求由该服务代理到 /internal/policy-query。
3. Java 校验 Python 的 requestId/schemaVersion、citationIdsUsed 和 citations。Python 只能提供回答与证据，不提供业务动作，不提供授权身份。
4. PolicyAssistantService.QueryResponse.answer 已是当前 Java DTO 字段；Python adapter 将通过 schema 校验的 Python answer 填入该字段，并由 Java 设置 sourceMode/evidenceLevel 元数据，而不是新造一个不被后续链路读取的字段。Java 不直接信任 evidenceLevel：先验证引用和意图匹配，再映射到现有 AgentResponse.evidenceStatus；证据不匹配时降级为 PARTIAL/INSUFFICIENT，Python confidence 不作为答案正确性依据。若 Python 不可用或响应无效，PolicyAssistantService 改用纯 Java 本地检索结果；该 fallback 不再调用 legacy 外部 LLM，避免第二个远程依赖造成不确定延迟。
5. 每条 Python citation 映射到现有 PolicyAssistantService.Citation：Java 生成 index；title、section、category、sourceType 映射同名字段；sourceVersion 映射 version；sourceId 映射 logicalPath；excerpt 映射 summary。category 必须存在，供 PolicyTaskAgentService.matchesIntent 使用。effectiveFrom/effectiveTo/regionCode 可作为 Java Citation 的可选扩展字段保留，不替换已有字段。
6. Java 按 sourceId 融合引用、重新编号；只接受 citationIdsUsed 全部能映射到最终引用集的 answer。将 Python answer 中的 citation sourceId 标记重写为 Java 编号（如 [1]）；无效、缺失引用或被意图过滤的引用导致 answer 丢弃并走本地 fallback。
7. PolicyTaskAgentService 继续用 Java 计算 conditions、requiredMaterials、steps、warnings；asksStatus 命中时仍附加 Java 维护的业务状态说明。nextActions 继续由 getAllowedActions(intent, 当前用户权限) 生成，忽略 Python 建议动作。
8. 有效且有足够证据的 Python 回答成为 AgentResponse.answerSummary 的主体，并保留已重编号引用；Java 的业务状态提示可追加。Java fallback 使用现有 buildSummary。证据不足时不展示未经支持的 Python answer，使用 Java 的安全无依据说明。

mode、sourceMode、evidenceStatus 各自职责如下：

| mode | sourceMode | evidenceStatus | 语义 |
|---|---|---|---|
| AI_ASSISTED | PYTHON_HYBRID_RAG | SUFFICIENT/PARTIAL | Python answer 已通过 Java 的引用校验，并作为 answerSummary 主体。 |
| RETRIEVAL_ONLY | JAVA_LOCAL_FALLBACK | SUFFICIENT/PARTIAL | Python 超时、5xx、未配置或响应非法；Java 本地检索提供 answerSummary。 |
| RETRIEVAL_ONLY | PYTHON_HYBRID_RAG 或 JAVA_LOCAL_FALLBACK | INSUFFICIENT | 已有检索路径但证据不足；Java 不展示生成式结论，needHumanHelp=true。 |
| AI_ASSISTED | JAVA_LEGACY_AI_ASSISTED | SUFFICIENT/PARTIAL | Python 未启用时保留当前可选 Java legacy LLM 路径；不是 Python 混合检索。 |
| SAFE_REFUSAL | JAVA_SENSITIVE_GUARD | INSUFFICIENT | 仅限现有 PolicyAssistantService 敏感输入规则；不扩展为普通无依据或服务错误状态。 |
| SERVICE_ERROR（新增值） | SERVICE_ERROR | INSUFFICIENT | Python 路径失败且 Java 本地检索本身也不可用；返回安全错误说明和人工渠道，needHumanHelp=true。 |

现有 mode 的 SAFE_REFUSAL 不用于普通“没有证据”或“服务不可用”。新增 SERVICE_ERROR 是明确的新枚举值；旧客户端应把未知 mode 按安全提示展示。sourceMode 标明实际路径，evidenceStatus 仍表示 Java 校验后的证据程度；Python 的 confidence/retrieval score 不作为答案正确概率。若 Python 未启用，保留当前 Java 检索及可选 legacy LLM 行为，并使用单独的 sourceMode=JAVA_LEGACY_AI_ASSISTED 标识该旧路径，不将其称为 Python RAG。
## F. OCR 文本到可信迁入实体（PROPOSED）

实体解析只由 Java 完成。Python 输出 person.name、person.idCard、household.householdNo、household.address 等文本和来源；不会提供 personId、toHouseholdId 或任何数据库主键。Java 从当前 AuthenticatedUser/DataScopeCriteria 构造授权查询，候选必须限定在调用人有权访问的范围内。

现有 MigrationInRequest 字段包括 personId、migrationType、fromRegionCode、fromAddress、toRegionCode、toHouseholdId、inDate、reason、title、remark、transferBatchNo 和版本字段。人工确认后 Java 从授权候选令牌恢复 personId/toHouseholdId，并重建迁入请求；模型文本和客户端字段都不是主键来源。

| 场景 | Java 可信规则 | 是否可继续/建议交互 | 错误语义建议 |
|---|---|---|---|
| 身份证号与人员匹配 | 规范化文本后在授权范围内精确查询；使用 PersonMapper 的 scoped query 思路，不使用无范围全表搜索。 | 唯一匹配返回一个 person candidateToken；用户仍须在确认页明确选择。 | 仅允许范围内候选；无权资源与不存在统一 404 ENTITY_NOT_FOUND。 |
| 姓名与证件号不一致 | 对照同一人员记录中的姓名；不可用模型建议覆盖数据库姓名，也不可自动更新人员主档。 | 停止草稿创建；用户修正后重新预览候选。 | 409 PERSON_NAME_MISMATCH，字段和值脱敏。 |
| 无人员匹配 | 不自动新建人口；先引导走已有授权的人口建档流程，再重新进入迁入。 | 返回无候选和安全建档引导，不发出可确认 token。 | 404 ENTITY_NOT_FOUND 或明确的 PERSON_NOT_FOUND（仅限已授权范围且不泄露范围外存在性）。 |
| 多个人员候选 | 精确证件号原则上应唯一；若历史异常造成多条，仅返回权限内最少展示字段和短时 candidateToken。 | 返回全部权限内候选；用户必须选择其中一个，不能由模型或排序结果代选。 | 200 + resolutionStatus=SELECTION_REQUIRED；候选显示掩码字段，不回传数据库 ID。 |
| 目标户号/地址无匹配 | 先按授权范围精确查户号；地址模糊结果只能作提示，必须由用户选择当前授权的 ACTIVE 户。 | 无户时先使用现有建户业务，不在 AI 确认中自动建户；不发出可确认 token。 | 404 TARGET_HOUSEHOLD_NOT_FOUND；不可泄露其他区县户号。 |
| 多个户匹配或仅地址匹配 | 地址不是稳定唯一键；用 DataScope 过滤候选，显示必要的掩码户号/区划/地址片段。 | 返回权限内候选及各自 candidateToken；用户必须选择一个。 | 200 + resolutionStatus=SELECTION_REQUIRED；未经授权候选不计数、不显示。 |
| 目标户非 ACTIVE | Java 重查并锁定目标户；状态必须为 ACTIVE。 | 不可继续；提示选择其他有效户或先走授权的家庭户维护流程。 | 已授权但非 ACTIVE 返回 409 TARGET_HOUSEHOLD_INACTIVE。 |
| toRegionCode 与目标户区划冲突 | Java 从授权目标户读取 regionCode，要求与用户确认的 toRegionCode 相等，并校验用户可办理该区域。不可使用 OCR 地址推断区划后直接写库。 | 用户重新选择候选或区划并重新预览。 | 409 TARGET_REGION_MISMATCH；无权区划按统一 404/403 策略隐藏。 |
| 人员已有有效户籍或有效家庭成员关系 | 创建预览和最终确认时均重新检查 residence 与 ACTIVE membership；执行前仍由现有业务再校验。 | 不可继续；按人工业务流程先处理当前户籍/关系。AI 不发起迁出或覆盖登记。 | 409 PERSON_ALREADY_REGISTERED。 |
| IN_CITY_CROSS_DISTRICT 批次 | Java 要求 transferBatchNo 对应已完成迁出批次、相同人员/目标区划、尚未使用；数据库读取和锁/唯一约束由后续实现评审。 | 不匹配或已使用时停止，提示经办人员核实原迁出流程。 | 409 TRANSFER_BATCH_INVALID 或 TRANSFER_BATCH_ALREADY_USED；不返回其他人的批次明细。 |
| DataScope 越权 | 人员、目标户和批次每次查询都按 ALL/DEPARTMENT/REGION/SELF 规则过滤；不能把“AI 找到”当作授权。 | 未授权对象不展示候选；用户需转交本范围内经办人。 | 未知、越权或令牌不属于当前调用人时统一 404，避免枚举。 |
| 预览后数据变化 | 确认时重查并锁定人员、家庭户、批次、当前户籍；比较候选实体版本、任务版本和当前 DataScope。 | 任一变化即停止写入，刷新并重新预览/选择。 | 409 BUSINESS_STATE_CHANGED 或 RESOLUTION_SCOPE_CHANGED。 |

### F.1 resolution-preview 与候选令牌

POST /api/ai/tasks/{taskId}/resolution-preview 接收 taskVersion、用户修正字段和业务上下文（migrationType、toRegionCode 等）。Java 用任务结果中的识别文本与用户修正值执行授权查询，再返回：

- resolutionStatus：RESOLVED_SINGLE（唯一候选，仍需用户选择）、SELECTION_REQUIRED（多候选）、NEEDS_CORRECTION（识别值冲突/无候选）之一。
- resolutionToken：一次预览会话的不透明随机令牌，推荐 5 分钟过期。
- personCandidates[] / targetHouseholdCandidates[]：仅包含当前权限范围内的候选；每项带 candidateToken、掩码展示字段和 entityVersion。
- requiredSelections：需要用户选择的实体类别。
- warnings：需确认的字段差异、未匹配或业务提示。

唯一候选也发 candidateToken，前端显式展示并要求用户选择，不能只因候选数量为 1 而静默选中。无匹配时不返回候选令牌；多候选时不排序代选。

resolutionToken 与 candidateToken 是不同令牌：

| 令牌 | 绑定内容 | 用途 |
|---|---|---|
| resolutionToken | 当前 userId、taskId、taskVersion、规范化修正字段摘要、当前 DataScope/权限指纹、候选集及过期时间。 | 确认这次预览会话，防止把另一用户/任务/字段的候选带入。 |
| candidateToken | 所属 resolutionToken、实体类型（person/household）、服务端实体主键、entityVersion、授权范围指纹和过期时间。实体主键只保存在 Java 服务端，不编码进令牌明文。 | 表达用户选择的一个授权候选，不向浏览器传 personId 或 toHouseholdId。 |

令牌使用高熵随机值并由服务端保存/校验；不能把签名可解码的主键放入 token。令牌绑定当前用户、任务和数据范围；确认时再次检查权限与实体状态。用户修改预览字段后必须重新调用 resolution-preview，不能沿用旧 resolutionToken。

预览响应示例（全部候选数据为虚构且已脱敏）：

~~~json
{
  "taskId": "task-demo-confirm",
  "taskVersion": 3,
  "resolutionStatus": "RESOLVED_SINGLE",
  "resolutionToken": "res_opaque_demo",
  "expiresAt": "2026-11-03T11:00:00+08:00",
  "requiredSelections": ["person", "targetHousehold"],
  "personCandidates": [
    {
      "candidateToken": "pc_opaque_demo_1",
      "display": {"name": "示例甲", "idCardMasked": "SIMULATED-****-001"},
      "entityVersion": 2
    }
  ],
  "targetHouseholdCandidates": [
    {
      "candidateToken": "hc_opaque_demo_1",
      "display": {"householdNoMasked": "示例户****421", "regionCode": "110101", "addressMasked": "示例街道*号", "status": "ACTIVE"},
      "entityVersion": 5
    }
  ]
}
~~~

若 person/household 任一候选为空，返回对应安全错误和必要人工指引；不能返回范围外是否存在或数量。用户未选择、选择令牌类型不匹配、令牌来自别的预览/用户/任务或一次提交多个候选时，Java 拒绝确认。令牌过期要求重新预览；候选或数据版本变化返回 409 并要求重新选择。

静态源码差异：MigrationService.createIn 当前调用 persons.selectById、lockActiveHousehold，并未在该方法中显式带 DataScope；审计账本 #21 还记录 executeIn 未比较目标户区划和迁入区划。S1 只在新解析流程契约中要求受控解析与区划核验，不修改现有人工接口，也不把该问题写成已修复。

## G. 人工确认与 DRAFT 创建（PROPOSED）

### G.1 确认请求与候选选择

POST /api/ai/tasks/{taskId}/confirm 要求 Bearer JWT、ai:precheck:confirm 和 migration:in:create。只有 status=SUCCEEDED 的 DOCUMENT_PRECHECK 任务可继续。

确认请求包含 resolutionToken、selectedCandidates.person、selectedCandidates.targetHousehold；两个值都是本次 resolution-preview 返回的 candidateToken。请求中禁止出现 personId、toHouseholdId、person 主键或 household 主键。Java 由服务端令牌记录解析所选实体，再次校验授权范围、实体版本、ACTIVE 状态、区划、当前户籍和批次。

confirmedFields 只包含用户已确认的非主键业务字段（如 migrationType、toRegionCode、inDate、reason、title、remark、transferBatchNo）以及本次预览已绑定的字段修正。字段修正值与 resolutionToken 中的摘要不一致时，返回 409 RESOLUTION_INPUT_CHANGED，用户需重新预览。

materialBindings 中的 materialType 只是用户对材料类别的选择/修正建议，不是正式写库值。Java 必须验证 stageMaterialId 属于当前用户和当前任务、材料仍可用，并把用户建议映射到标准码白名单；最终 materialType 和 requiredFlag 由 Java 根据业务类型、migrationType、授权材料分类及现有 MigrationMaterialRequirementService 规则计算。忽略客户端/AI 提交的 requiredFlag，不允许写入未知或未授权的材料引用。

确认请求示例：

~~~json
{
  "requestSchemaVersion": "1.0",
  "taskVersion": 3,
  "resolutionToken": "res_opaque_demo",
  "selectedCandidates": {
    "person": "pc_opaque_demo_1",
    "targetHousehold": "hc_opaque_demo_1"
  },
  "idempotencyKey": "client-confirm-uuid",
  "confirmedFields": [
    {"fieldCode": "person.name", "value": "示例甲"},
    {"fieldCode": "migration.migrationType", "value": "OUTSIDE_CITY"},
    {"fieldCode": "migration.toRegionCode", "value": "110101"},
    {"fieldCode": "migration.inDate", "value": "2026-11-03"},
    {"fieldCode": "migration.reason", "value": "虚构事由"}
  ],
  "acknowledgedAlerts": [],
  "materialBindings": [
    {"stageMaterialId": "stg_demo_id_1", "materialType": "IDENTITY_PROOF"}
  ]
}
~~~

### G.2 选择错误、令牌过期与写入结果

| 条件 | 规则 |
|---|---|
| 候选选择缺失 | 必须恰有一个 person token 和一个 targetHousehold token；缺失返回 422 CANDIDATE_SELECTION_REQUIRED。 |
| 选择多个或令牌类型不符 | 每类是单个字符串字段；数组/多个令牌、person token 用于 household 等返回 400 INVALID_CANDIDATE_SELECTION。 |
| 令牌不存在、越权或非当前 task | 对外统一 404 CANDIDATE_NOT_FOUND/RESOLUTION_NOT_FOUND，避免枚举候选。 |
| 令牌过期或已失效 | 返回 409 RESOLUTION_EXPIRED；必须重新运行 resolution-preview 并重新选择。 |
| DataScope/权限变化 | 对令牌重新核验当前权限；不再有权时撤销令牌并返回统一 404 或 409 RESOLUTION_SCOPE_CHANGED，不泄露原候选信息。 |
| 候选记录/迁入业务状态变化 | 事务内重新读取并锁定实体、检查 entityVersion、当前户籍、ACTIVE household、区划和批次；改变时返回 409 BUSINESS_STATE_CHANGED。 |
| 幂等 | Idempotency-Key 在 userId + taskId 范围唯一；同键同请求返回首次 applicationId；同键不同请求 409 IDEMPOTENCY_KEY_REUSED。任务最多绑定一个草稿；换键重复确认返回 409 TASK_ALREADY_CONSUMED。相同幂等请求的重试返回已有结果，不重复消费令牌创建草稿。 |
| 用户字段变化 | 任一 confirmedFields 与 resolutionToken 绑定摘要不一致时返回 409 RESOLUTION_INPUT_CHANGED；重新预览。 |
| 创建业务 | 仅创建迁入 DRAFT；不调用 submit、approval 或 executeIn。申请、迁入详情、正式材料绑定和审计在同一数据库事务。 |
| 材料绑定 | Java 根据业务类型、migrationType、用户确认后的标准码和当前材料规则计算 requiredFlag；verifyStatus 一律初始化 PENDING。 |
| 事务/文件补偿 | DB 回滚时暂存文件保持 READY/IN_USE 并可重试；不能先删除文件。提交后再标记 BOUND；需要复制时使用可重放事件/补偿，不能假设文件系统与 MySQL 原子提交。 |

成功首次创建返回 HTTP 201：applicationId、applicationNo、migrationDetailId、status=DRAFT、version、材料标准码/requiredFlag 的服务端计算结果（verifyStatus=PENDING）及 warnings。响应可供用户检查结果，但不代替提交或审批。

流程铁律：创建 DRAFT 后，仍由申请人走 POST /api/applications/{id}/submit；申请进入 UNDER_REVIEW 后由合规 L3 人员通过现有材料核验接口人工核验；随后按现有审批接口审批；批准后再由获授权人员显式调用 POST /api/migrations/in/applications/{id}/execute。不得由 AI 任务、确认接口或 Python 绕过这些步骤。

双轨并行：现有 POST /api/migrations/in/applications 必须继续可单独创建 DRAFT；传统人工路径不依赖 AI、RabbitMQ、Python、Redis 或预审材料服务。新增入口失败、AI 不可用或 Broker 停机时，传统迁入入口保持既有行为。
## H. 运维聚合接口与跨实例边界（PROPOSED）

建议 Java 提供受保护的 GET /api/ops/overview?window=5m，权限码建议 ops:overview:view。角色映射由赵书亚与张锦源确认；不得默认向所有已登录用户开放。浏览器和 JavaFX 只访问 Java 聚合接口，不直连 Actuator、Prometheus、RabbitMQ 管理端、Redis 或数据库端口。

建议返回：

~~~json
{
  "schemaVersion": "1.0",
  "observedAt": "2026-11-03T10:35:00+08:00",
  "instanceId": "population-java-demo-a",
  "windowSeconds": 300,
  "services": {
    "java": "UP",
    "python": "DEGRADED",
    "mysql": "UP",
    "redis": "UP",
    "rabbitmq": "UP"
  },
  "requests": {"count": 120, "errorRate": 0.02, "p50Ms": 18, "p95Ms": 180},
  "tasks": {"pending": 4, "running": 2, "succeeded": 30, "failed": 1, "oldestPendingSeconds": 12}
}
~~~

字段不含用户名、证件、文件路径、连接串、密码、Token、消息体或单任务材料结果。服务不可达时用 DOWN/DEGRADED 与 observedAt 说明数据新鲜度，不把未采集显示成 0。任务指标按最小聚合窗口统计；是否按 DataScope 分桶由实际权限需求决定，默认不提供用户级明细。

建议职责：赵书亚负责指标名称、采集、窗口、真实采样和部署暴露；张锦源负责 Java Controller 鉴权、SecurityConfig 复核和结果字段最小化；张蕊负责 Web/JavaFX 展示与失败状态；王家兴提供 Python 健康与耗时指标。Actuator 管理端口只在服务内部/运维网络开放，聚合 API 不转发管理凭据。

## I. 当前 API 对照与接口索引维护

CURRENT 已有：
- POST /api/migrations/in/applications：独立人工迁入草稿入口。
- POST /api/applications/{id}/materials、GET /api/applications/{id}/materials、GET /api/materials/{id}/download、DELETE /api/materials/{id}、POST /api/materials/{id}/verify：正式申请材料。
- POST /api/applications/{id}/submit：现有申请提交。
- POST /api/approvals/{id}/approve：现有审批入口。
- POST /api/migrations/in/applications/{id}/execute：显式迁入执行。
- POST /api/assistant/policy/query：现有 Java 助手。
- POST /api/persons/idcard-image：既有身份证影印本同步 OCR。
- GET /api/health：公开健康摘要，仅展示数据库、Redis 状态和缓存模式。

以上路由在 doc/api/backend-api-index.md 中并未全部索引：助手和身份证影印本接口漏列；课程路线图对身份证接口的复数路径也与 Controller 不符。S1 文档建议记录真实路由，实际 API 总索引更新安排在后续接口施工阶段。

## S1 后续边界

本文没有实现 Controller、Python 客户端、RMI、RabbitMQ、任务表、迁移 SQL、文件清理器、SecurityConfig、Docker、监控或前端页面。后端可将拟议 schema 和状态作为 S2/S3 Mock 及接口适配输入；真实跨组服务接入时再处理差异，完整冻结/验收仍需对应接口和教师口径有证据。
