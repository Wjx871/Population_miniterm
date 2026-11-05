# S4-B 人工确认与迁入草稿绑定架构

## 状态与边界

- S4-A：已结项。S4-B 施工分支从已验收 integration SHA `214c10734e67819c87e0c8446c13807a9646609f` 派生；S4-B 等待独立复核，尚未整合。
- 新入口只创建 `business_application.DRAFT` 和 `migration_in.DRAFT`。不 submit、不核验、不审批、不执行迁入、不写人口/户籍/成员关系。
- 传统 `POST /api/migrations/in/applications` 保持独立。AI 关闭、RabbitMQ/Python 不可用不阻断传统人工迁入。
- RabbitMQ、Python、前端和真实 MySQL 迁移：`NOT_VERIFIED`。审计 #3、#33 保持 `OPEN`；#21 保持 `FIXED_VERIFIED`。

## 确认请求与摘要

`POST /api/ai/tasks/{taskId}/confirm` 需要 Bearer JWT、`ai:precheck:confirm`、`migration:in:create`。任务必须属于当前用户、类型 `DOCUMENT_PRECHECK/MIGRATION_IN`、状态 `SUCCEEDED`，且 taskVersion 匹配。新增权限由 V4_018 仅授给当前 S3 AI 创建/查看角色 `HOUSEHOLD_MANAGER` 和 `SYSTEM_ADMIN`。

请求保留 S1 G.1 的 `resolutionToken`、显式 person/targetHousehold candidateToken、`idempotencyKey`、`confirmedFields`、`acknowledgedAlerts` 和 `materialBindings`。增加 `householdEvidenceType`：两种户口簿/地址证明类别同时提供时要求用户选其中一种；只有一种则自动采用，均无则允许草稿并返回缺件 warning。确认端对未知 JSON 属性和重复 JSON 键采取拒绝策略，不改变全局 Jackson 行为。

预览和确认共用 `AiResolutionInputCanonicalizer`。摘要纳入 schema、taskId/taskVersion、迁入类型、目标区划、迁出批次、完整持久化 task result 的 SHA-256、规范化 OCR 解析字段、用户修正后的解析字段和冲突集合。确认从数据库重新读取 task 结果并重算；原始 OCR、冲突、修正值或业务上下文不一致返回 `409 RESOLUTION_INPUT_CHANGED`。只有 digest 入库，原始身份证号和完整地址不写入日志/审计。

字段白名单：

- 预览绑定：`person.name`、`person.idCard`、`household.householdNo`、`household.address`、`migration.migrationType`、`migration.toRegionCode`、`migration.transferBatchNo`。
- 新业务输入：`migration.fromRegionCode`、`migration.fromAddress`、`migration.inDate`、`migration.reason`、`migration.title`、`migration.remark`。其中 fromRegionCode 与 remark 按现有 `MigrationInRequest` 允许为空，其余按现有校验；服务器不推断缺失值。
- `acknowledgedAlerts` 必须为空：当前 S4-A 任一 warning 都会使会话不可确认，不能以确认告警绕过 NEEDS_CORRECTION。

## 事务、锁与幂等

确认事务锁顺序：

1. 按当前用户锁定预审 task；在授权后查任务回执，以允许过期令牌的同键回放。
2. 首次确认调用 S4-A `validateForConfirmation()`（`Propagation.MANDATORY`），锁会话、候选、person、household、当前户籍/成员和迁出批次，并重验 DataScope、权限、实体版本/状态及批次。
3. 按 stageMaterialId 字典序锁定用户拥有且属于该 task 的暂存材料，确认 READY、未过期、MIGRATION_IN、文件存在并匹配大小/SHA。
4. 显式 Bean Validation 服务端构造的 `MigrationInRequest`，调用既有 `MigrationService.createIn()`；同事务写材料、绑定、审计、回执，并把解析会话置为 CONSUMED。

申请提交与草稿材料修改也共享申请行锁。`ApprovalService.submit()`、`MaterialService.upload()`、`MaterialService.delete()` 均在各自外层事务中调用 `ApplicationService.requireForUpdate()`，由 `BusinessApplicationMapper.selectByIdForUpdate` 锁定 `business_application` 行；该方法使用 `Propagation.MANDATORY`，不会另开或提前结束事务。三条路径先取得申请行锁，再读取当前状态和执行材料检查/修改；submit 在锁内验证必需材料并使用原有 version CAS 改为 `UNDER_REVIEW`。AI_STAGE 删除在申请锁之后锁定 binding/stage，事务提交后才触发文件清理。申请行锁保持至外层事务提交或回滚，因此竞争操作醒来后会读取新状态与材料集合。其他写入路径已逐一核查：`MaterialService.verify()` 只在 `UNDER_REVIEW` 修改核验字段，不增删必需材料；`AiConfirmService` 只在同一事务内创建全新的 DRAFT 并绑定材料，未提交前申请不可见。

`ai_confirmation_receipt.task_id` 唯一，`(owner_user_id,task_id,idempotency_key)` 唯一；task 行锁串行化并发，数据库唯一约束作为最终防线。同用户/任务/key/请求摘要相同返回原 applicationId；同 key 不同请求 `409 IDEMPOTENCY_KEY_REUSED`；同 task 新 key `409 TASK_ALREADY_CONSUMED`。首次响应 `201`；重放 `200`，返回 `creationStatus=DRAFT` 和实时 `currentStatus`。回放先验证当前认证、权限和任务所有权，不再次校验过期 session、不复制/绑定文件。

安全错误：缺少/未知/跨用户任务、候选或材料 `404`；权限不足 `403`；缺候选/不完整 schema 或字段 `422`；过期/撤销/不可确认令牌、状态与版本变化、幂等冲突、材料不可用 `409`。错误和日志不返回 token、路径、对象键或敏感字段值。

## 正式材料来源与生命周期

采用 S1 A.3 建议的同一私有文件对象引用方案：V4_018 给 `application_material` 增加 `storage_kind`/`storage_reference`，并新增 `ai_stage_material_binding` 持久化 stage↔material、task、application、owner 与释放状态。binding 对 stage 和 material 均有唯一约束。正式材料的真实 object key 只由 binding 与 `ai_stage_material` 数据库记录取得；客户端只能提交 stageMaterialId。`ApplicationMaterialStorageResolver` 按 `LOCAL`/`AI_STAGE` 分流，AI_STAGE 读取器不依赖 `AiPrivateFileStorageService` Bean，也不向 API 返回 key/路径。

S3 lifecycle reaper 只清 UPLOAD_FAILED/CANCELLED/EXPIRED，不清 BOUND。正式材料删除只允许 DRAFT/RETURNED：先在事务内把 binding 置为 RELEASE_PENDING、stage BOUND→CANCELLED、移除正式材料并写审计；事务回滚时文件不动。提交后尝试删除文件，失败由始终运行的 `AiBoundMaterialCleanupService` 有界重试；进程在删除前/后崩溃均可从持久化 RELEASE_PENDING 继续。唯一 binding 防止删除仍被其他业务引用的对象。LOCAL 人工材料保留原本 upload/download/delete 流程。

文档与迁移：增量 SQL 为 `V4_018_s4b_confirm_draft_binding.sql`，不得改写 V4_015～V4_017；H2 schema 同步维护。升级与回滚核对见 [V4_018 升级说明](../../database/s4b-V4_018-upgrade.md)。
