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

RabbitMQ 消息只带 taskId 和不透明 stageMaterialId，不传证件原文、二进制内容、绝对路径、服务凭据或 JWT。首选由 Python worker 使用独立服务身份经内网调用 Java 的受控内容流接口：

- 建议 Java 内部端点：POST /internal/ai/materials:resolve。
- 请求：taskId、stageMaterialId、requestId。
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
  "stageMaterialIds": ["stg_opaque_a1"],
  "idempotencyKey": "client-generated-uuid"
}
~~~

| 字段 | 类型 | 必填 | 约定 |
|---|---|---:|---|
| requestSchemaVersion | string | 是 | 首版为 1.0；未知主版本返回 422 UNSUPPORTED_SCHEMA_VERSION。 |
| taskType | enum | 是 | 首版仅 DOCUMENT_PRECHECK。 |
| businessType | enum | 是 | 首版仅 MIGRATION_IN。 |
| stageMaterialIds | string[] | 是 | 1—10 个去重的不透明 ID；每项都必须属于当前用户且为 READY。 |
| idempotencyKey | string | 是 | 用户侧唯一键；键作用域为 userId + taskType + endpoint；同键同请求返回同一 taskId，同键不同规范化请求返回 409。 |

成功返回 envelope 的 data 建议含 taskId、taskType、status=PENDING、acceptedAt、taskSchemaVersion=1.0、resultSchemaVersion=1.0、traceId、pollAfterMs。受理只说明请求已经持久化，不表示材料齐全、AI 已完成或预审通过。

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
  "requestId": "req-demo-001",
  "taskId": "task-demo-001",
  "traceId": "trace-demo-001",
  "businessType": "MIGRATION_IN",
  "expectedMaterialTypes": ["IDENTITY_PROOF", "HOUSEHOLD_BOOK_OR_ADDRESS_PROOF"],
  "materials": [
    {
      "materialRef": "stg_opaque_a1",
      "contentType": "image/jpeg",
      "sizeBytes": 245000,
      "sourceRoleHint": "IDENTITY_CARD_FRONT"
    }
  ]
}
~~~

| 字段 | 类型 | 必填 | 语义 |
|---|---|---:|---|
| schemaVersion | string | 是 | 内部请求结构版本。 |
| requestId / taskId / traceId | string | 是 | Java 生成并贯穿全链路的关联值。 |
| businessType | enum | 是 | 首版 MIGRATION_IN。 |
| expectedMaterialTypes | string[] | 是 | Java 根据当前迁入规则计算；Python 仅生成缺件提醒，不拥有规则最终决定权。 |
| materials | object[] | 是 | 仅列任务允许读取的材料引用；每项包含 opaque materialRef、由 Java 校验的 MIME/大小和用户提示类别。 |
| sourceRoleHint | enum/string | 否 | 用户/前端建议的类别，明确不可信；Python 可返回自己的 detectedType。 |

Java-Python 请求不带用户 Token、数据库 ID、绝对路径或直接凭据。文件通过 A.5 的受控 Java 内容接口取得。

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
| alerts[] | object[] | 缺件、低置信度、跨材料冲突、无法识别、类型不符；包含 code、severity、fieldCodes、materialRefs、safeMessage。 |
| modelVersion、ocrEngineVersion、ruleSetVersion | string/null | 记录实际使用的模型、OCR 与规则版本；未使用时为 null。 |
| elapsedMs | integer | Python 服务内部耗时；Java 另记录完整调用耗时。 |

Python 的材料类型分类是建议。Java 负责把 detectedType 映射到 A.4 标准码，应用提交仍以 Java MigrationMaterialRequirementService 的规则重算。Python 返回 200 表示结构化分析成功，不表示预审通过。HTTP 4xx/5xx 用 D.3 错误体表达。

### D.3 虚构响应样例

#### 1. 正常识别（值为虚构演示内容）

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
          "source": {
            "materialRef": "stg_demo_id_1",
            "page": 1,
            "boundingBox": {"x": 0.12, "y": 0.18, "width": 0.20, "height": 0.05}
          }
        },
        {
          "fieldCode": "person.idCard",
          "value": "SIMULATED-ID-001",
          "fieldStatus": "PRESENT",
          "confidence": 0.93,
          "source": {
            "materialRef": "stg_demo_id_1",
            "page": 1,
            "boundingBox": {"x": 0.55, "y": 0.44, "width": 0.30, "height": 0.06}
          }
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
          "source": {
            "materialRef": "stg_demo_book_1",
            "page": 2,
            "boundingBox": {"x": 0.10, "y": 0.22, "width": 0.18, "height": 0.05}
          }
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

Java 规则预计身份材料及户口簿/地址证明；这里只上传身份材料。Python 提示缺件，Java 在提交申请时仍重新验证：

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
      "code": "MATERIAL_MISSING",
      "severity": "WARNING",
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

名字只用于虚构冲突示例；后端不得自动选择一份材料作为正确值：

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

失败不是分析结果；Java 消费者按 retryable 和有限次数决定重试，最后一次才将任务置为 FAILED：

~~~json
{
  "schemaVersion": "1.0",
  "requestId": "req-demo-004",
  "taskId": "task-demo-004",
  "traceId": "trace-demo-004",
  "error": {
    "code": "MODEL_UNAVAILABLE",
    "retryable": true,
    "safeMessage": "文档分析服务暂时不可用，请稍后查看任务状态。"
  }
}
~~~

错误体不包含真实地址、凭据、栈信息、原始文件名或证件字段。若同一任务重试耗尽，Java 持久化 failureCode=RETRY_EXHAUSTED，任务状态成为 FAILED。

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
| outcome | ANSWERED、INSUFFICIENT_EVIDENCE；服务错误由调用层表示。 |
| answer | 基于检索证据生成的简短政策回答；无依据时明确说无法确认。 |
| citations[] | sourceId、title、section、excerpt、sourceVersion、effectiveFrom、effectiveTo、regionCode、retrievedAt。 |
| knowledgeBaseVersion | Python 当前使用的知识库快照/索引版本。 |
| modelVersion、retrievalVersion、rerankerVersion | 本次实际组件版本；未使用的组件为 null。 |
| evidenceLevel | SUFFICIENT、PARTIAL、INSUFFICIENT；检索分数不作为答案正确概率。 |
| elapsedMs | Python 本次处理耗时。 |

建议 connect 超时 2 秒、整体 read 超时 8 秒，不自动长时间重试。数值作为后端默认适配起点，真实 Python 接入时根据实测延迟调整。引用应是前端可展示的来源与版本，不返回内部绝对路径或服务凭据。

### E.2 Java 降级及对外模式

Java 继续拥有公共 API 响应格式和 PolicyTaskAgentService.AgentResponse 的现有字段语义。调用 Python 超时、5xx、服务未配置或响应 schema 无效时，Java 执行现有 PolicyAssistantService.query 本地知识检索，再由 PolicyTaskAgentService 组装当前意图、证据、citations、warnings、nextActions、needHumanHelp 和 trace。Java 本地检索当前按 Markdown 知识块和中文二元组计分，并非 Python 混合向量检索或重排序。

建议在保持现有 AgentResponse 字段语义的基础上增加 sourceMode，不直接重解释既有 mode。保留当前 mode 的 AI_ASSISTED、RETRIEVAL_ONLY、SAFE_REFUSAL 语义；用 evidenceStatus 表示证据是否充分，用 sourceMode 表示服务来源：

| mode | evidenceStatus | sourceMode | 含义 |
|---|---|---|---|
| AI_ASSISTED | SUFFICIENT/PARTIAL | PYTHON_HYBRID_RAG | Python 有证据并给出回答。 |
| RETRIEVAL_ONLY | SUFFICIENT/PARTIAL | JAVA_LOCAL_FALLBACK | Python 不可用，由 Java 本地检索回答；明确提示降级。 |
| SAFE_REFUSAL | INSUFFICIENT | PYTHON_HYBRID_RAG | Python 检索未得到足以支持确定回答的证据。 |
| SAFE_REFUSAL | INSUFFICIENT | JAVA_LOCAL_FALLBACK | Java 本地降级检索未得到足以支持确定回答的证据。 |
| SAFE_REFUSAL | INSUFFICIENT | SERVICE_ERROR | Python 与 Java 本地检索均未能提供可用结果；对外保留 ApiResponse，显示安全失败信息和人工渠道建议。 |

该方案保持现有 mode/evidenceStatus 字段语义，通过新增 sourceMode 区分 Python、Java fallback 和两条路径都失败。无依据时 evidenceStatus=INSUFFICIENT；服务完全不可用时 mode=SAFE_REFUSAL、sourceMode=SERVICE_ERROR、needHumanHelp=true。旧客户端可忽略新增 sourceMode。

## F. OCR 文本到可信迁入实体（PROPOSED）

实体解析只由 Java 完成。Python 输出 person.name、person.idCard、household.householdNo、household.address 等文本和来源；不会提供 personId、toHouseholdId 或任何数据库主键。Java 从当前 AuthenticatedUser/DataScopeCriteria 构造授权查询，候选必须限定在调用人有权访问的范围内。

现有 MigrationInRequest 字段包括 personId、migrationType、fromRegionCode、fromAddress、toRegionCode、toHouseholdId、inDate、reason、title、remark、transferBatchNo 和版本字段。人工确认后 Java 必须重新构造此请求所需的主键和枚举，而不是复制模型响应。

| 场景 | Java 可信规则 | 是否可继续/建议交互 | 错误语义建议 |
|---|---|---|---|
| 身份证号与人员匹配 | 规范化文本后在授权范围内精确查询；使用 PersonMapper 的 scoped query 思路，不使用无范围全表搜索。 | 唯一匹配且后续字段一致时可进入确认预览；仍须用户显式确认。 | 仅允许范围内候选；无权资源与不存在统一 404 ENTITY_NOT_FOUND。 |
| 姓名与证件号不一致 | 对照同一人员记录中的姓名；不可用模型建议覆盖数据库姓名，也不可自动更新人员主档。 | 停止草稿创建；让用户对照原材料修正识别值或联系有权经办人。 | 409 PERSON_NAME_MISMATCH，字段和值脱敏。 |
| 无人员匹配 | 不自动新建人口；先引导走已有授权的人口建档流程，再重新进入迁入。 | 不能继续迁入草稿；人工建档后重新上传/解析或选择授权人员。 | 404 ENTITY_NOT_FOUND 或明确的 PERSON_NOT_FOUND（仅限已授权范围且不泄露范围外存在性）。 |
| 多个人员候选 | 精确证件号原则上应唯一；若历史异常造成多条，仅返回权限内最少展示字段和短时 candidateToken。 | 用户从授权候选中人工选择；不能使用模型猜测。 | 409 ENTITY_AMBIGUOUS；候选使用短时不透明令牌，不回传身份证号或数据库 ID。 |
| 目标户号/地址无匹配 | 先按授权范围精确查户号；地址模糊结果只能作提示，必须由用户选择当前授权的 ACTIVE 户。 | 可选现有授权 ACTIVE 户；无户时先使用现有建户业务，不在 AI 确认中自动建户。 | 404 TARGET_HOUSEHOLD_NOT_FOUND；不可泄露其他区县户号。 |
| 多个户匹配或仅地址匹配 | 地址不是稳定唯一键；用 DataScope 过滤候选，显示必要的掩码户号/区划/地址片段。 | 用户明确选择合法候选；若超出权限则无候选可见。 | 409 ENTITY_AMBIGUOUS，未经授权候选不计数、不显示。 |
| 目标户不存在或非 ACTIVE | Java 重查并锁定目标户；状态必须为 ACTIVE。 | 不可继续；提示选择其他有效户或先走授权的家庭户维护流程。 | 不存在/越权返回统一 404；已授权但非 ACTIVE 返回 409 TARGET_HOUSEHOLD_INACTIVE。 |
| toRegionCode 与目标户区划冲突 | Java 从受权目标户读取 regionCode，要求与请求 toRegionCode 相等，并校验用户可办理该区域。不可使用 OCR 地址推断区划后直接写库。 | 用户重新选目标户或在有权范围内选择正确区划。 | 409 TARGET_REGION_MISMATCH；无权区划按统一 404/403 策略隐藏。 |
| 人员已有有效户籍或有效家庭成员关系 | 创建预览和最终确认时均重新检查 residence 与 ACTIVE membership；执行前仍由现有业务再校验。 | 不可继续；按人工业务流程先处理当前户籍/关系。AI 不发起迁出或覆盖登记。 | 409 PERSON_ALREADY_REGISTERED。 |
| IN_CITY_CROSS_DISTRICT 批次 | Java 要求 transferBatchNo 对应已完成迁出批次、相同人员/目标区划、尚未使用；数据库读取和锁/唯一约束由后续实现评审。 | 不匹配或已使用时停止，提示经办人员核实原迁出流程。 | 409 TRANSFER_BATCH_INVALID 或 TRANSFER_BATCH_ALREADY_USED；不返回其他人的批次明细。 |
| DataScope 越权 | 人员、目标户和批次每次查询都按 ALL/DEPARTMENT/REGION/SELF 规则过滤；不能把“AI 找到”当作授权。 | 未授权对象不展示候选；用户需转交本范围内经办人。 | 统一 404 以避免枚举，确知已授权资源但权限不符时可按现有 API 返回 403。 |
| 预览后数据变化 | 预览返回短期 resolutionToken，绑定 userId、taskId、task result version、候选和实体 version；确认事务内锁定/重查人员、家庭户、批次、当前户籍。 | 数据变化时停止写入，让用户刷新并重新确认。 | 409 BUSINESS_STATE_CHANGED。 |

静态源码差异：MigrationService.createIn 当前调用 persons.selectById、lockActiveHousehold，并未在该方法中显式带 DataScope；审计账本 #21 还记录 executeIn 未比较目标户区划和迁入区划。S1 只在新契约中要求受控解析与区划核验，不修改现有人工接口，也不把该问题写成已修复。

建议的短期候选句柄不能让用户传任意数据库主键。候选显示字段仅限经授权后的掩码名称、掩码户号和区划，身份证号不展示。每次确认仍重查库。

## G. 人工确认与 DRAFT 创建（PROPOSED）

### G.1 流程接口

建议新增：

- POST /api/ai/tasks/{taskId}/resolution-preview：按当前用户权限和任务结果查询实体并返回字段差异、缺件和短时 resolutionToken。
- POST /api/ai/tasks/{taskId}/confirm：用户明确确认或修正识别值、选择授权实体，并请求创建迁入 DRAFT。

两者都要求 Bearer JWT、ai:precheck:confirm 和 migration:in:create；Java 还需单独确认用户有权选择该人员、目标户和区划。仅有浏览权限或仅有 AI task 查看权限不能创建申请。

确认请求示意：

~~~json
{
  "requestSchemaVersion": "1.0",
  "taskVersion": 3,
  "resolutionToken": "short-lived-opaque-reference",
  "idempotencyKey": "client-confirm-uuid",
  "confirmedFields": [
    {"fieldCode": "person.name", "value": "示例甲", "source": "AI_CORRECTED_BY_USER"},
    {"fieldCode": "migration.toRegionCode", "value": "110101", "source": "USER_SELECTED"}
  ],
  "acknowledgedAlerts": ["LOW_CONFIDENCE"],
  "materialBindings": [
    {"stageMaterialId": "stg_opaque_a1", "materialType": "IDENTITY_PROOF", "required": true}
  ]
}
~~~

server 创建的 personId、toHouseholdId 等主键不从模型字段读取。resolutionToken 只引用 Java 在授权范围内确认的候选；最终写入前 Java 重新查库并在事务中核对实体当前状态。

### G.2 规则与结果

| 条件 | 规则 |
|---|---|
| 任务状态 | 只允许 status=SUCCEEDED、taskType=DOCUMENT_PRECHECK、结果结构版本受支持且未过任务结果保留期的任务。PENDING/RUNNING/FAILED 不可确认。 |
| 显式确认 | UI 必须逐字段展示识别值、来源材料、页码/位置、置信度和告警。用户须确认必填识别字段或修改后确认；低置信度/字段冲突必须逐项 acknowledged，不允许默认勾选。 |
| 身份、户和区划 | resolutionToken 绑定当前用户和 taskVersion；Java 事务内重新解析身份证号/姓名、目标 ACTIVE 户、DataScope、现有户籍、区划匹配及跨区批次。 |
| 幂等 | Idempotency-Key 在 userId + taskId 范围唯一；同键同请求返回首次创建的 applicationId；同键不同请求返回 409 IDEMPOTENCY_KEY_REUSED。一个 task 最多成功绑定一个迁入草稿；已被消费的任务使用另一键重试也返回 409 TASK_ALREADY_CONSUMED。 |
| 并发 | 确认时带 taskVersion 和业务实体 version；任务、暂存材料及目标实体发生变化时返回 409 BUSINESS_STATE_CHANGED，不创建半成品。 |
| 创建业务 | 只调用现有迁入专业业务语义，状态为 DRAFT；不调用 submit、approval、executeIn。申请创建、migration_in 详情、材料绑定及确认审计应处于同一数据库事务。 |
| 材料绑定 | 每份正式 application_material 使用 Java 算出的材料标准码、requiredFlag 和文件元数据；verifyStatus 一律初始化 PENDING。AI 的“识别成功”不能转成 VERIFIED。 |
| 事务/文件补偿 | DB 事务回滚时暂存文件保持 READY/IN_USE 并可重试；不能先删除文件。推荐正式记录引用同一私有对象，提交后状态变 BOUND；如必须复制，增加可重放的文件绑定/清理事件并在后续实施前确认补偿规则。 |
| 审计 | 记录调用人、taskId、traceId、确认时间、字段版本、用户修正字段清单、候选解析版本、applicationId、幂等键摘要和结果；不记录完整身份证号、文件内容、服务 Token。 |
| 返回 | 首次创建返回 HTTP 201 和 applicationId、applicationNo、migrationDetailId、status=DRAFT、version、绑定材料状态列表（PENDING）及 warnings。重复同键请求返回同一资源，不再创建第二份草稿。 |

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
