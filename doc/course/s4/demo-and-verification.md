# S4-A 演示与验证记录

## 当前交付状态

- 施工基线：`758014cbcccdfe820efb2a6fae73bc3168030484`。
- 施工分支：`feat/20261104-s4a-entity-resolution`。
- 阶段：S4-A Java 本地实现；S4-B 未施工。
- Python 真实联调：`NOT_VERIFIED`。
- RabbitMQ 真实联调：`NOT_VERIFIED`。
- 前端联调：`NOT_VERIFIED`。
- 不访问团队共享 MySQL；无真实 MySQL 验收声明。

## 可复现演示

1. 以具备 `ai:precheck:view` 与 `migration:in:create` 的用户上传合成材料并创建 `MIGRATION_IN` 预审任务。
2. 由确定性 Mock 完成任务，读取任务当前 version。
3. 调用 `/api/ai/tasks/{taskId}/resolution-preview`，用虚构的姓名、合法合成证件号和户号填充 corrections。
4. 检查 `RESOLVED_SINGLE` 或 `SELECTION_REQUIRED`、脱敏候选、token 不透明度与显式选择要求。
5. 再次预览并更改字段，检查旧 session 状态变为 `REVOKED`、旧 candidateToken 不能与新 resolutionToken 配对。
6. 改动 person.version、成员关系、户籍、目标户状态/区划、权限范围或迁出批次后，检查 S4-B 内部校验拒绝旧快照。
7. 对 `NEEDS_CORRECTION` 会话调用内部校验，确认 `RESOLUTION_NOT_CONFIRMABLE`；本阶段不存在创建正式 DRAFT 的 HTTP 接口。

测试 fixtures 均为虚构数据；Mock 不代表真实 OCR/Python 服务验证。

## 回归证据

- S4-A API / H2 / Mock 定向测试：`AiPrecheckIntegrationTest`，29 项通过，0 失败。
- 本轮 S4-A/S3 H2 / Mock 定向回归：`AiPrecheckIntegrationTest` 35 项通过，0 失败；覆盖 MANDATORY 事务边界、事务回滚与锁持有、令牌 404/409 契约、清理保留窗口和并发幂等、候选级联删除及数据边界、带完整敏感数据权限用户的 AI 地址脱敏，并覆盖原有材料/Outbox/任务生命周期。
- 脱敏单元测试：`SensitiveDataMaskingServiceTest` 2 项通过，0 失败；覆盖 `address()` 原行为、`addressAlways()` 强制脱敏、身份号/手机号/证件许可、Bearer/password 正则替换。
- 合并定向回归：S3/S4-A 与脱敏 37 项、S2 RMI 并发/HTTP/异常分类 8 项，合计 45 项通过，0 失败。
- 人员版本与主链路专项：`PersonMapperTest`、`Phase03MigrationIntegrationTest`、`Phase04CancellationIntegrationTest`，合计 76 项通过，0 失败；包含人员 CAS/递增、迁入迁出递增、注销递增与 #21 create/update/execute 拒绝测试。
- S2 RMI 回归：`S2RmiClientConcurrencyIntegrationTest` 6 项、`S2RmiHttpEndToEndIntegrationTest` 1 项、`RmiInvocationHandlerClassificationTest` 1 项，8 项通过，0 失败。
- 上述人员/迁入/注销/S2 合并定向回归命令共 84 项通过，0 失败。
- 完整 `mvnw.cmd test`：545 项通过，0 失败，0 错误，0 跳过；BUILD SUCCESS，耗时约 6 分 19 秒。
- `git diff --check`：最终提交前复核。

一次中间全量运行发现测试夹具将 `next_attempt_at` 设为任务创建的当前时刻，worker 在极短间隔内读取时偶尔仍判为未到期。夹具现在将其固定为过去一秒；S4-A 定向测试与最终全量运行均通过。生产任务受理与 worker 领取规则未放宽。

测试运行于 H2，不访问团队共享 MySQL。`MySql84*IT` 类未作为 Surefire `test` 运行，不据此声明真实 MySQL 验收。

令牌清理仅删除超过 30 天保留期的过期/撤销 `ai_resolution_session`，每小时最多处理 100 条；candidate 由外键级联删除。H2 验证覆盖清理开关、有限批次、并发两个清理调用、审计及人员/户籍/材料表不变。新增索引放在顺序迁移 `V4_017_s4a_resolution_retention_indexes.sql`，V4_016 未改写；没有查询或执行任何实际 MySQL 迁移。

## 保留项

- 审计 #21：已在创建、更新、执行三处增加服务端精确校验；最终回归结果及测试名见审计台账。
- 审计 #33：保持 `OPEN`，等待权威区划层级规则，不推导前缀关系。
- 审计 #3 长事务：保持 `OPEN`。
- S3 `demo-and-verification.md` 已注明 527 项是 S3 阶段最终报告，和本轮 S4-A 545 项完整回归分开记录。
- 本工作不声称 RabbitMQ、Python 或前端联调通过，不开展 S4-B。
