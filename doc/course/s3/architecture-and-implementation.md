# S3 架构与实现

## 范围和验收状态

S3 在现有 Spring Boot / MyBatis 后端增加 AI 预审专属暂存、异步任务、Outbox/Inbox、受限 RabbitMQ 消费和审计。新功能由 `AI_PRECHECK_ENABLED` 默认关闭，不接管传统人口、材料、迁入或审批流程。任务消费者只读取暂存材料并写预审结果，不创建业务申请或修改人口、户籍、迁入及正式材料状态。

Python 文档分析真实接口尚未联调，当前使用显式标注 `mode=MOCK` 的确定性客户端。`/internal/**` 服务端点未添加。真实 RabbitMQ 不可用或未运行时，H2/Mock 结果不代表 Broker 可靠性实测；本分支真实 Broker 状态为 `NOT_VERIFIED`。MySQL 迁移仅做静态审查，没有连接或升级共享课程数据库。

## 暂存材料

材料所有人来自 `CurrentUserContext`；组织、区划和数据范围作为创建时快照保存。服务端以高熵 `stg_` ID 命名材料，文件私有存放，API 和消息不会披露路径或对象键。文件验收覆盖大小、允许扩展名、MIME 与 PDF/JPEG/PNG 签名；服务端计算 SHA-256，不跨用户按哈希去重。

生命周期为 `UPLOADING → READY → IN_USE → READY`，或转入 `UPLOAD_FAILED`、`CANCELLED`、`EXPIRED`。`BOUND` 仅保留给 S4；S3 不会设置该状态。上传先持久化 `UPLOADING` 记录与临时对象键，再完成私有文件落盘，最后以数据库更新提交 `READY`。异常转成不可用状态并补偿删除；孤儿和未完成记录由定时 reaper 分批处理。任务结束后材料回到 `READY` 并获得有限保留期；活跃任务材料不会被生命周期清理。

上传幂等约束在数据库中唯一限定 `(owner_user_id, idempotency_endpoint, idempotency_key)`。请求指纹由服务端文件 SHA-256、规范业务类型、文档角色提示和检测到的内容类型组成。并发同键请求依靠唯一约束竞争并回读原记录；同指纹返回同一 ID 和当前状态，不重新激活已取消、过期、上传失败或缺失文件。不同指纹返回 409。

## 任务受理与容量

当前任务状态严格为 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`。任务幂等唯一约束为 owner、task type、endpoint、key。请求摘要包括 schema、任务/业务类型、迁入类型和排序后的材料 ID。事务中先写任务，再通过条件更新保留容量计数，然后 CAS 占用材料、插入任务材料快照、Outbox 和审计。任何失败全部回滚。相同幂等请求在容量检查前命中现有记录，容量紧张时仍可安全重放。

容量口径：`ai_precheck_capacity.outstanding_count` 统计已提交且尚未到 `SUCCEEDED` 或 `FAILED` 的 `DOCUMENT_PRECHECK` 任务，即 `PENDING + RUNNING`，包含等待 Broker 恢复和等待重试的任务。迁移初始上限为 1000；原子条件更新 `outstanding_count < max_outstanding` 负责并发准入，终态事务负责减计数。达到上限时返回 429 并在任务事务提交前回滚。H2 并发回归把上限临时设置为 1，两个并发不同任务恰好一个返回 202、一个返回 429，最终计数为 1。

因此 Broker 不可用不会阻止上限内任务和 Outbox 的数据库受理。发布器未收到正向 confirm 时保留 Outbox、更新有限间隔后的 `available_at`，而不会把已提交任务变成接口错误。达到数据库可靠容量时拒绝新受理。主队列与 DLQ 各自使用 `x-max-length` 和 `reject-publish`，其 broker 队列限制不会替代数据库受理容量。

## Outbox、RabbitMQ 和消费

Outbox 发布器通过数据库条件更新领取带租约的记录，发送持久 JSON 消息，等待 correlated publisher confirm，并检查 mandatory return。只有确认 ACK 且没有 return 才标记 `PUBLISHED`。Confirm 丢失可能重复发布；唯一事件 ID 和 Inbox 记录、任务状态 CAS 共同实现消费幂等。

RabbitMQ 使用 durable direct exchange、durable 主队列和 DLX/DLQ。消息只包含 `eventId`、`taskId`、`taskType`、`schemaVersion`、`traceId`、`occurredAt`、`producer` 七个字段。消费者根据 taskId 查询数据库关联材料；消息不携带材料 ID、文件内容/路径或个人信息。

监听器并发固定为配置值（默认 2），prefetch 默认 1；队列上限默认 1000；没有“每条消息一个新线程”的执行方式。listener 使用手动 ACK 且关闭自动 requeue：成功/失败终态及其审计、材料状态和容量变更都提交后 ACK；可重试失败先在一个数据库事务内写 `PENDING`、attemptCount、nextAttemptAt、新 Outbox 和审计，再 ACK。若持久化异常，当前投递以不 requeue 的方式交给 DLQ；RUNNING 租约恢复任务并生成新的可靠 Outbox，因此不会无限 NACK/requeue。

默认处理最多 3 次尝试，退避为 2 秒、10 秒。可恢复错误耗尽后，事务写 `FAILED`、安全错误码、容量释放、材料恢复、Inbox、审计和指向 DLQ 的 Outbox；随后消息 ACK。DLQ Outbox 仍由 publisher confirm 发布，发布失败保留其数据库意图。不可解析或无法关联任务的 poison message 走 Broker DLQ，并在数据库可用时写 Inbox/审计；已知任务的格式错误则先持久化 FAILED 和 DLQ Outbox 再 ACK。

worker 使用 `status + version + lease_owner + lease_expires_at` 条件更新领取任务。分析超时配置默认 125 秒，租约 180 秒，HTTP 读取上限 120 秒。过期 lease 恢复为 PENDING 并事务性写下一条 Outbox；超过重试上限写 FAILED。对长时间无消费者确认的已发布 PENDING 任务有界地重新发出至多三次；仍不可交付后写入 `DELIVERY_RECOVERY_EXHAUSTED` 失败，避免无限重发。终态和 Inbox 去重使重复投递不会覆盖结果或重复释放容量。

## 分析适配

`DocumentAnalysisClient` 是可替换边界。当前 Mock 可注入延迟、可恢复失败、永久失败、超时和无效响应。确定性成功响应只返回空识别字段和“需人工复核”告警，不伪造姓名、身份证或识别值，也不生成正式审批或草稿。响应校验器检查 schema、request/task/trace 对应关系、材料引用、置信度和来源框范围、规则 ID 与材料类型语义后才允许 SUCCEEDED。

真实 Python HTTP 调用尚未配置服务身份与联调环境，不能声称 Python OCR/RAG 已接入。S4 接入前仍需为服务身份、材料读取授权、超时、错误清洗及真实响应契约独立验收。

## 存储、权限和迁移

`V4_015_ai_precheck_async.sql` 新增 `ai_stage_material`、`ai_precheck_task`、`ai_precheck_task_material`、`ai_precheck_capacity`、`ai_precheck_outbox`、`ai_precheck_inbox`、`ai_precheck_audit`，仅加两个 AI 权限并向户籍管理员和系统管理员授权。H2 测试 schema 保持对应表、唯一约束及种子权限。未运行 MySQL 迁移，也未修改共享数据库。

不新增未服务身份保护的 `/internal/**`；现有 `/api/**` 认证与方法权限继续生效。结果仅由创建者读取。普通日志写安全错误码、任务/事件 ID、traceId、实例 ID、尝试次数和耗时，不写个人识别字段、原文件、绝对路径或凭据。

## 边界外的审计项

人口身份证影印本 `PersonIdCardImageService.uploadAndRecognize` 的同步 OCR 长事务仍保持未修复。见 [#3 专项审查](person-idcard-long-transaction-review.md)；审计账本状态继续为 OPEN。S3 新预审表和消费者没有改变旧服务。
