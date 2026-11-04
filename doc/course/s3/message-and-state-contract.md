# S3 消息和状态契约

## 任务和材料状态

```text
材料：UPLOADING → READY → IN_USE → READY
          └────→ UPLOAD_FAILED
                 READY → CANCELLED | EXPIRED
BOUND 为后续保留状态，S3 不设置。

任务：PENDING → RUNNING → SUCCEEDED
                    ├──→ PENDING（可恢复错误，记录 nextAttemptAt 并新增 Outbox）
                    └──→ FAILED（重试耗尽、永久错误或有界投递恢复耗尽）
```

任务只在 `SUCCEEDED` 有结果，只在 `FAILED` 有安全错误码。状态更新与结果、Inbox、材料恢复、容量释放和审计按各自一致性事务提交；条件更新校验当前版本和 lease，过期 worker 的迟到结果被丢弃。

## 事件 JSON

事件类型：`DOCUMENT_PRECHECK_REQUESTED`

```json
{
  "eventId": "evt_0123456789abcdef0123456789abcdef",
  "taskId": "task_0123456789abcdef0123456789abcdef",
  "taskType": "DOCUMENT_PRECHECK",
  "schemaVersion": "1.0",
  "traceId": "trace-example-20261104",
  "occurredAt": "2026-11-04T12:00:00+08:00",
  "producer": "population-java"
}
```

属性集合必须精确匹配七个字段。消费端拒绝缺少、额外或类型不符字段；校验事件 ID、task ID、trace 格式、producer 与 schema。消息不包含 stageMaterialId、姓名、身份证号、户号、OCR 文本、文件字节、文件路径、原始文件名、JWT 或服务凭据。消费者通过持久化任务和任务材料关联读取对象引用。

## Outbox 发布和调度

1. 任务事务写 `PENDING` 主消息 Outbox；同一个 task 的其他业务数据不可在消息发布之后才补写。
2. 发布器用数据库租约/CAS 领取 Outbox，写 PUBLISHING 和 attemptCount。
3. RabbitTemplate 发送持久消息，使用 correlated publisher confirm 和 mandatory return。
4. 只有 Broker confirm ACK 且无 returned message，才在数据库将记录标记 `PUBLISHED`。
5. NACK、return、超时或 Broker 故障时，发布意图回到 PENDING 并写 `available_at`/安全错误码；租约到期允许其他实例恢复。已受理任务仍为 PENDING。
6. 消费重试由数据库保存 `PENDING + attemptCount + nextAttemptAt + MAIN Outbox` 后 ACK 当前投递。终态 FAILED 与 DLQ Outbox 同事务提交后 ACK。

Confirm 丢失可能导致同一事件重复发布；消费端的 eventId Inbox 唯一键、任务状态 CAS、终态检查和 lease 保护是重复处理安全边界。RabbitMQ 本身不参与数据库事务。

## 重试、租约和死信

- 最大分析尝试默认 3；默认重试退避 2 秒、10 秒。没有无限 `NACK/requeue`。
- 消息 JSON 语法错误、格式错误或未知任务走不 requeue 的 Broker DLQ；能关联已知任务的格式/schema 错误先提交 FAILED、审计及 DLQ Outbox，再 ACK。
- 可恢复分析失败在事务中更新任务、审计、Inbox 和下一条主 Outbox，然后 ACK 原消息。
- 不可恢复错误或尝试耗尽在事务中写 FAILED、安全错误码、容量释放、材料恢复、Inbox、审计和 DLQ Outbox，然后 ACK。
- RUNNING 租约过期时，reaper 通过 version/owner/expiry 条件更新恢复或终结任务。旧 worker 不可提交迟到结果。
- 已发布但在保守队列排空时间内仍未被领取的 PENDING 任务最多进行三次有界投递恢复；超过后以 `DELIVERY_RECOVERY_EXHAUSTED` 终结并审计。
- DB 故障导致 listener 无法提交时，不 requeue 当前消息；Broker DLQ 保存投递，任务租约恢复逻辑保存数据库重试意图。终态状态优先由数据库事实决定，DLQ 发布本身可通过其 Outbox 重试。

## 有界容量统计

数据库准入上限记录于 `ai_precheck_capacity`。`outstanding_count` 精确定义为从任务事务提交直到 SUCCEEDED/FAILED 终态事务提交之间的所有已受理任务（PENDING + RUNNING）；包括 Broker 暂停和退避等待中的任务。上限默认 1000。任务受理事务中的条件更新 `outstanding_count < max_outstanding` 是并发下唯一计数入口；任务终态与 outbox/审计一致性事务扣减。若事务回滚则计数也回滚。H2 并发容量回归验证限制为 1 时最多只有一项受理成功。

主队列和 DLQ 另有默认 1000 条的 broker 侧 `x-max-length`，overflow 为 `reject-publish`。这是存储/积压上界，不作为 API 是否受理的判断依据；Broker 不可用时 API 仅依赖数据库容量准入并写 Outbox。主队列过满或 confirm 不成功时，Outbox 保持待发布。
