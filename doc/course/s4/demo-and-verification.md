# S4-A / S4-B 演示与验证记录

## 当前交付状态

- S4-A 施工基线：`758014cbcccdfe820efb2a6fae73bc3168030484`。
- S4-B 施工基线：`214c10734e67819c87e0c8446c13807a9646609f`。
- 当前施工分支：`feat/20261104-s4b-confirm-draft-material-binding`。
- 阶段：S4-A 已结项；S4-B Java 本地实现位于独立施工分支，等待独立复核。
- Python 真实联调：`NOT_VERIFIED`。
- RabbitMQ 真实联调：`NOT_VERIFIED`。
- 前端联调：`NOT_VERIFIED`。
- 不访问团队共享 MySQL；V4_018 真实 MySQL 执行未验证。

## 可复现演示

1. 以具备 `ai:precheck:view` 与 `migration:in:create` 的用户上传合成材料并创建 `MIGRATION_IN` 预审任务。
2. 由确定性 Mock 完成任务，读取任务当前 version。
3. 调用 `/api/ai/tasks/{taskId}/resolution-preview`，用虚构的姓名、合法合成证件号和户号填充 corrections。
4. 检查 `RESOLVED_SINGLE` 或 `SELECTION_REQUIRED`、脱敏候选、token 不透明度与显式选择要求。
5. 再次预览并更改字段，检查旧 session 状态变为 `REVOKED`、旧 candidateToken 不能与新 resolutionToken 配对。
6. 改动 person.version、成员关系、户籍、目标户状态/区划、权限范围或迁出批次后，检查 S4-B 内部校验拒绝旧快照。
7. S4-A 单独演示时，对 `NEEDS_CORRECTION` 会话调用事务内校验原语，确认 `RESOLUTION_NOT_CONFIRMABLE`。S4-B 的正式确认入口与 DRAFT 创建演示见下文。

测试 fixtures 均为虚构数据；Mock 不代表真实 OCR/Python 服务验证。

## 回归证据

- S4-A API / H2 / Mock 定向测试：`AiPrecheckIntegrationTest`，29 项通过，0 失败。
- 本轮 S4-A/S3 H2 / Mock 定向回归：`AiPrecheckIntegrationTest` 35 项通过，0 失败；覆盖 MANDATORY 事务边界、事务回滚与锁持有、令牌 404/409 契约、清理保留窗口和并发幂等、候选级联删除及数据边界、带完整敏感数据权限用户的 AI 地址脱敏，并覆盖原有材料/Outbox/任务生命周期。
- 脱敏单元测试：`SensitiveDataMaskingServiceTest` 2 项通过，0 失败；覆盖 `address()` 原行为、`addressAlways()` 强制脱敏、身份号/手机号/证件许可、Bearer/password 正则替换。
- 合并定向回归：S3/S4-A 与脱敏 37 项、S2 RMI 并发/HTTP/异常分类 8 项，合计 45 项通过，0 失败。
- 人员版本与主链路专项：`PersonMapperTest`、`Phase03MigrationIntegrationTest`、`Phase04CancellationIntegrationTest`，合计 76 项通过，0 失败；包含人员 CAS/递增、迁入迁出递增、注销递增与 #21 create/update/execute 拒绝测试。
- S2 RMI 回归：`S2RmiClientConcurrencyIntegrationTest` 6 项、`S2RmiHttpEndToEndIntegrationTest` 1 项、`RmiInvocationHandlerClassificationTest` 1 项，8 项通过，0 失败。
- 上述人员/迁入/注销/S2 合并定向回归命令共 84 项通过，0 失败。
- S4-B 定向回归：`AiConfirmIntegrationTest`、`ApplicationMaterialSubmitConcurrencyIntegrationTest`、`ApplicationMaterialFeatureOffIntegrationTest`、`AiPrecheckIntegrationTest`、S2 RMI、迁入/审批/撤销和脱敏测试合计 165 项通过，0 失败、0 错误、0 跳过。
- S4-B 完整 `mvnw.cmd test`：567 项通过，0 失败、0 错误、0 跳过；BUILD SUCCESS，耗时 5 分 42 秒。
- S4-A 的完整回归记录为 545 项；该数字属于 S4-A 阶段。S3 最终报告的 527 项属于 S3 阶段，分别记录，不与本轮 S4-B 的 567 项混用。
- `git diff --check`：本轮提交前通过。

一次中间全量运行发现测试夹具将 `next_attempt_at` 设为任务创建的当前时刻，worker 在极短间隔内读取时偶尔仍判为未到期。夹具现在将其固定为过去一秒；S4-A 定向测试与最终全量运行均通过。生产任务受理与 worker 领取规则未放宽。

测试运行于 H2，不访问团队共享 MySQL。`MySql84*IT` 类未作为 Surefire `test` 运行，不据此声明真实 MySQL 验收。

令牌清理仅删除超过 30 天保留期的过期 ACTIVE、REVOKED 或 CONSUMED `ai_resolution_session`，每小时最多处理 100 条；candidate 由外键级联删除。H2 验证覆盖清理开关、有限批次、并发两个清理调用、审计及人员/户籍/材料表不变。V4_017 为原 S4-A retention index；V4_016 未改写。V4_018 S4-B 新增回执/绑定/来源字段；本轮未查询或执行任何实际 MySQL 迁移。

## 保留项

- 审计 #21：已在创建、更新、执行三处增加服务端精确校验；最终回归结果及测试名见审计台账。
- 审计 #33：保持 `OPEN`，等待权威区划层级规则，不推导前缀关系。
- 审计 #3 长事务：保持 `OPEN`。
- S3 `demo-and-verification.md` 已注明 527 项是 S3 阶段最终报告；S4-A 完整回归为 545 项；本轮 S4-B 完整回归为 567 项，按阶段分别记录。
- S4-B 本轮只声明 Java/H2/Mock 本地实施与测试；RabbitMQ、Python、前端和真实 MySQL 均未验收。

## S4-B 演示步骤

以下只使用 H2 和合成文件。Mock 任务代表 Java 接收了确定性结构化响应，不代表真实 OCR/Python 联调。

1. 以 `HOUSEHOLD_MANAGER` 账户创建合成 MIGRATION_IN 预审任务，运行 Mock worker 直到 `SUCCEEDED`。
2. 调用 `resolution-preview`，明确确认显示的 person 和 targetHousehold 候选；修改解析字段必须重新预览。
3. 上传 IDENTITY_CARD_FRONT 与 ADDRESS_PROOF 合成文件，在同一 task 完成预审后调用 `/confirm`，选择两个候选 token，填写现有 MigrationInRequest 所需业务字段。
4. 确认首请求为 201，检查 application 与 migration detail 均为 DRAFT，材料通过正式列表/下载接口可读、verifyStatus=PENDING，requiredFlag 由 Java 计算。
5. 对同一请求重放，检查 200 和相同 applicationId；改变同 key 内容检查 409；用新 key 再确认检查 TASK_ALREADY_CONSUMED。
6. 通过既有 application submit 入口提交草稿，再以合规 L3 的独立材料核验接口核验；确认接口本身没有执行 submit、核验、审批或 executeIn。
7. 在 AI 关闭配置下重新启动服务，读取既有 AI_STAGE 正式材料；测试普通 LOCAL 上传材料下载/删除保持原行为。

## S4-B 自动化验证

- S4-B 专项 H2/Mock：`AiConfirmIntegrationTest`、`ApplicationMaterialSubmitConcurrencyIntegrationTest` 和 `ApplicationMaterialFeatureOffIntegrationTest`；覆盖确认同/异 key 并发、摘要/原始 OCR 变化、非法 JSON/嵌套绑定、跨用户/跨任务/过期/错误状态材料、文件缺失/大小/SHA 不匹配、材料角色防冒充、缺件 warning 后由现有 submit 拒绝、DRAFT/提交/L3 核验、BOUND 与 S3 reaper 并发、删除回滚、清理失败后由新清理服务实例恢复、AI 关闭后的持久化绑定读取与新存储解析器恢复、普通材料回归，以及 submit 与普通/AI_STAGE 删除和普通上传的确定性锁竞争。
- S4-A/S3：`AiPrecheckIntegrationTest` 覆盖预览摘要、令牌状态及 S3 生命周期；S4-B 清理新增 CONSUMED session 保留期用例。
- S4-B 定向回归 165 项、完整 Maven 回归 567 项均通过；`git diff --check` 通过。commit SHA 与 refs 核验见最终提交记录。H2/Mock 结果不等于真实 MySQL、RabbitMQ、Python 或前端联调验收。

当前外部验收状态：RabbitMQ `NOT_VERIFIED`、Python `NOT_VERIFIED`、前端 `NOT_VERIFIED`、V4_018 真实 MySQL `NOT_VERIFIED`；审计 #3/#33 `OPEN`，#21 `FIXED_VERIFIED`。
