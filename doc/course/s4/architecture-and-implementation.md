# S4-A 实体解析与迁入主链路加固

## 状态边界

- S4-A：Java 本地实现，使用隔离 H2 与确定性 Python Mock。
- S4-B：未施工；本轮没有创建正式迁入 DRAFT 的 HTTP 入口。
- 真实 Python 联调、真实 RabbitMQ 联调、前端联调：`NOT_VERIFIED`。
- S3 的 RabbitMQ 验收仍为 `NOT_VERIFIED`，不能视为整合验收完成。
- 审计 #3 长事务：`OPEN`。
- 审计 #33 区划层级冲突：`OPEN`，等待权威区划规则。

## 解析入口与输入

`POST /api/ai/tasks/{taskId}/resolution-preview` 只允许任务所有者调用，并要求 `ai:precheck:view` 与 `migration:in:create`。任务必须为成功的 `DOCUMENT_PRECHECK` / `MIGRATION_IN` 任务；客户端提交的 `taskVersion`、`migrationType` 必须与服务端任务记录吻合。

客户端只可修正 `person.name`、`person.idCard`、`household.householdNo`、`household.address`。证件号经 GB 11643 校验后精确匹配，人员姓名需一致；户号使用授权范围内的精确查询。地址只用于当前授权范围内的候选提示。任何模型输出或客户端输入都不能指定 personId、toHouseholdId、实体版本或权限范围。

所有实体读取都使用 `DataScopeCriteria`。无权与不存在实体对调用方返回同一通用 warning。响应只包含掩码姓名/证件号/户号/地址、区域代码、服务端 entityVersion、候选令牌和安全 warning，不含数据库主键。AI 预览地址使用始终脱敏方法，即使调用方另有完整敏感资料权限也不返回完整地址。

## 状态与候选

- `RESOLVED_SINGLE`：人员与目标户各有一个可信候选，仍须用户显式选择。
- `SELECTION_REQUIRED`：存在多个授权候选，用户必须逐项显式选择。
- `NEEDS_CORRECTION`：输入缺失/冲突或业务检查未通过。可能返回部分候选，但会话 `confirmable=false`，不能进入后续确认。

候选拒绝检查包括人员状态、当前有效 residence、ACTIVE household_member、目标户 ACTIVE 状态、目标户区划与 `toRegionCode` 一致性，以及同市跨区迁出批次的人员、类型、目标区划、完成状态和未使用状态。不自动建人、建户或改人口主档。

## 令牌和数据快照

`resolutionToken`、person candidateToken、household candidateToken 分别使用 256-bit SecureRandom 值与不同前缀。数据库只保存 SHA-256 摘要。候选记录绑定父 session、实体类型、服务端主键、实体版本、实体状态与状态摘要。candidateToken 只有与本次 resolutionToken 同时使用才可解析。

令牌数据默认保留 30 天。每小时最多清理 100 条满足以下条件的 session：`ACTIVE` 且 `expires_at` 已超过 30 天，或 `REVOKED` 且撤销更新时间已超过 30 天。删除 session 后由外键级联删除其 candidate；不清理 `ai_precheck_audit` 或任何人员、家庭户、迁入及材料数据。可用 `AI_RESOLUTION_CLEANUP_ENABLED`、`AI_RESOLUTION_SESSION_RETENTION`、`AI_RESOLUTION_CLEANUP_BATCH_SIZE` 和 `AI_RESOLUTION_CLEANUP_POLL_MS` 调整；默认启用、`P30D`、100 条、3600000 毫秒。失败日志只记录异常类型，调度任务捕获清理异常。

同一用户/任务重跑预览时，服务在持有 `ai_precheck_task` 行锁的事务内撤销旧 ACTIVE session、创建新 session 并写入候选。该数据库锁串行化跨 JVM 的重新预览；旧候选不会被复用。默认令牌 TTL 为 5 分钟，可由 `AI_RESOLUTION_TOKEN_TTL` 配置。请求摘要绑定规范化修正/识别字段、任务版本、迁入类型、目标区划和批次；权限摘要绑定 userId、当前权限、DataScope、部门、当前区划与授权区划集合。

person 候选快照包含 person.version/status/current_status_code 与所有 residence/member 行；household 候选快照包含 household.version/status/regionCode、所有成员关系、户籍与在途迁入记录。因此，即使 household.version 未变而成员关系发生变化，S4-B 内部校验也会拒绝旧快照。

`AiResolutionService.validateForConfirmation(...)` 是未映射到 HTTP 的 S4-B 事务校验原语，采用 `Propagation.MANDATORY`，无外层事务时拒绝调用。调用方必须在同一事务中继续完成正式 DRAFT 创建和材料绑定；校验锁与后续写入直到该外层事务提交/回滚才释放。该原语锁定任务、解析会话、候选、person、household、相关 residence/member 和迁出批次，并重新检查：任务状态/版本、token 归属/过期/confirmable、当前权限/DataScope、person.version/status/current residence/member、household.version/status/regionCode/成员/户籍，以及迁出批次当前状态和使用情况。只有服务端从候选令牌解析出的主键会进入返回值。当前不存在草稿确认 HTTP 接口。

内部校验的令牌错误契约：不存在 token、跨用户/跨任务 token 及不存在/错类型 candidateToken 返回统一 404，避免暴露实体是否存在；仍保留的过期/撤销会话和 `NEEDS_CORRECTION` 返回 409。权限丢失或实体越权按统一 404，权限范围快照、任务或实体状态变化安全返回 409。安全保留期清理后已经删除的 token 记录按“不存在”处理。

## 版本完整性与审计项

V4_016 为 `person` 添加 `BIGINT NOT NULL DEFAULT 0` 版本。PersonMapper 普通更新使用服务端读取的原始版本 CAS 并递增；删除状态更新、迁入执行和人员注销执行都递增版本。迁入/迁出与注销的版本冲突会导致事务回滚，不能返回成功。

审计 #21 在 `MigrationService.createIn`、`updateIn`、`executeIn` 锁定目标户后做服务端精确区划一致性检查；缺失区划或不相等均拒绝。迁入执行再次检查，AI 预览不能替代传统主链路校验。

审计 #33 暂不修改。当前 `countActiveResidenceInRegion` 仍使用数据库区划代码精确相等；仓库没有可证明“跨区同市”边界的权威数据规则，不能自行改成字符串前缀或固定长度推导。需要业务提供规范层级数据及归属规则后再单独实施。

## 数据库迁移

`doc/database/migrations/V4_016_s4a_entity_resolution.sql` 新增 person.version、`ai_resolution_session` 与 `ai_resolution_candidate`。本次不改写 V4_016，新增顺序迁移 `V4_017_s4a_resolution_retention_indexes.sql`，为过期与撤销清理条件各加一条复合索引。迁移不改 `business_application`、`migration_in` 或 `application_material`。本轮仅通过 H2 集成测试和 SQL 源码静态审查；不连接团队共享 MySQL，也不声称迁移已在真实数据库执行。
