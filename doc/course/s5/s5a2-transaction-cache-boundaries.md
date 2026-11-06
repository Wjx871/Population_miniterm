# S5-A2 业务事务、Mapper 路由与参考数据缓存边界

## 1. 范围与证据口径

本轮施工基线为 `3e6c87ed4f2441b974b3600ef7f13af06932a89d`。本文件是交给赵书亚实施 S5 读写路由和联调的业务边界清单；本轮不创建路由器，不修改 DataSource、复制配置、部署或数据库结构。生产改动限定于 `ReferenceCacheAspect` 与 `HouseholdService.member`，其余源码仅用于静态核对。

依据：`doc/course/s1/architecture-decisions-and-confirmations.md` 的 D3/D4/D6/D7，以及主工作区 `专业工程实训/邑循_个人后端课程改造总体开发路线图_v1.1_2026-11-03.md`、`专业工程实训/邑循项目详细分工.md`。S1 是评审草案，不能把其中历史“当前事实”覆盖为现行实现；下列事实以本基线 Service、Mapper Java/XML 与 JDBC SQL 为准。分工保持赵书亚主责复制、路由、Redis、双实例和故障演练，张锦源核对业务一致性。

必须分开记录三层证据：

1. **Java 事务事实**：源码声明、传播行为、直接调用与代理边界；没有动态验证的地方不称“实测事务”。
2. **建议路由**：`PRIMARY` 是主库强制边界；`REPLICA_ELIGIBLE` 只是满足条件后可走从库的候选，不代表当前已走从库。
3. **物理验证**：需要真实主从连接身份、SQL 所到节点、复制位点/延迟与故障记录。H2、Mock、注解反射和单 DataSource 测试不能证明 MySQL 主从正确。

矩阵中的 `RW` 指默认 `@Transactional`（`REQUIRED`、`readOnly=false`）；`RO` 指 `@Transactional(readOnly=true)`；`NONE` 指该入口没有事务声明，并不保证调用者没有事务；`RN` 指 `TransactionTemplate` 或注解的 `REQUIRES_NEW`，默认非只读；`MANDATORY` 只加入已有事务。隔离级别未显式指定时为框架/数据库默认值，不能据此宣称已验证某个 MySQL 隔离级别。自调用不会单独应用被调用方法的注解，实际继承外层事务；外层 RW 内任何 SELECT 都保持主库。

失败策略统一记号：**P**＝只能使用可用主库，主库不可用时返回明确失败，不改投从库、不伪造成功；**R**＝候选从库异常、延迟未知/超阈值或复制不健康时，整次读取保守回主（若已有事务连接失效，应结束失败事务后重新执行整个只读操作，不能在同一事务中任意换连接），主库也不可用则失败。所有候选从库查询仍须实时授权和 DataScope 约束。

## 2. 业务事务与 Mapper 路由矩阵

“自动化证据”列为测试源码与边界的映射，编号及已执行结果详见第 3 节；测试通过仅证明其覆盖的环境和断言，不能代替真实 MySQL 主从验证。其他生产源码的事务边界来自静态核对。当前核心状态查询先保守走主，允许从库的范围采用明确白名单。

| 业务 / Service 入口 | Mapper / JDBC 范围 | 读写 | Java 真实声明与调用边界（静态） | 行锁 / 并发条件 | 写后读取依赖 | 建议路由 | 延迟容忍 | 从库失败策略 | 自动化证据（仅静态映射） |
|---|---|---|---|---|---|---|---|---|---|
| 传统迁入 DRAFT：`MigrationService.createIn/updateIn` | `MigrationMapper.findResidence/lockHousehold/insertIn/updateInDraft`；`PersonMapper.selectById`；`BusinessApplicationMapper`、审批日志 | R/W | RW；调用 `ApplicationService.createSpecialized/updateSpecialized` 加入同一事务 | 目标户 `FOR UPDATE`；草稿 `business_status + version`；不是所有人员读取都有行锁 | 创建/修改后 `applications.require` 生成返回视图；检查当前户籍/目标户 | PRIMARY | 0，不能使用旧户籍/区划 | P | T1、T5 |
| 传统迁出 DRAFT：`MigrationService.createOut/updateOut` | `findResidence/findHousehold/activeMembers/insertOut/updateOutDraft`；申请与日志 Mapper | R/W | RW | 此创建/修改路径的 residence/household 是普通读取；更新草稿有版本条件，不能声称已加 `FOR UPDATE` | 当前户籍、户主和成员决定草稿内容；申请更新后读取 | PRIMARY | 0 | P | T1 |
| AI 解析预览：`AiResolutionService.preview` | JDBC 任务、解析 session/candidate/receipt；`PersonMapper.selectScopedByIdCard`、`HouseholdMapper.findScopedByNo/search` 等 | R/W | RW，预览会撤销旧 session 并写新 session/candidate，不能当普通 GET 从库查询 | 任务 `FOR UPDATE`；预览候选快照不等于确认锁定 | 当前权限、候选版本/状态摘要用于后续确认 | PRIMARY | 0 | P | T2 |
| AI 人工确认并绑定：`AiConfirmService.confirm` → `AiResolutionService.validateForConfirmation/markConsumed` | JDBC task/receipt/session/candidate/stage/binding/audit；人员/户查询；迁入、申请、正式材料 Mapper | R/W | confirm RW；两个 resolution 原语 `MANDATORY`；迁入建 DRAFT 加入调用方事务 | task、receipt、session/candidate、stage、person、household、residence/member、相关批次按代码锁定；版本/摘要复核 | 新申请/迁入 DRAFT、`PENDING` 正式材料、BOUND、receipt、session consumed 同事务；读回申请和材料；重复确认查 receipt | PRIMARY | 0，AI 结果不替代当前主档 | P | T2、T3 |
| 正式材料上传：`MaterialService.upload` | `BusinessApplicationMapper.selectByIdForUpdate`；`ApplicationMaterialMapper.insert/selectById` | R/W + 文件 | RW；文件系统不属于数据库原子事务 | 申请行锁；只允许 DRAFT/RETURNED | 插入后按 ID 读回；与 submit 共用申请行锁 | PRIMARY | 0 | P | T3、T4 |
| 正式材料替换/删除：`MaterialService.delete`，随后新 upload | 材料 `selectById/deleteById`；申请行锁；AI binding/stage JDBC | R/W + 文件 | delete RW；`AiBoundMaterialCleanupService.releaseBeforeDelete` 为 MANDATORY；AI 文件提交后清理另开 RN | 上传与删除共用申请行锁；AI 绑定释放还有自身锁/条件 | 替换是删除/新上传组合，源码无单一原子 replace 入口；新材料重置 PENDING | PRIMARY | 0 | P | T3、T4 |
| 材料人工核验：`MaterialService.verify` | 材料 `selectById/verify`、申请读取、审批请求/日志 | R/W | RW | 没有宣称申请行锁；`verify_status='PENDING'` 条件更新，非 version 字段锁 | 审核中状态、独立 L3 身份、本人回避和核验后读回 | PRIMARY | 0 | P | T3、T5 |
| 材料列表/下载：`MaterialService.list/download` | 申请 `selectById`；材料 `selectByApplicationId/selectById`；存储 resolver | R + 文件 | RO | 无显式行锁 | 上传/删除后立即查看；访问权和材料存储来源必须当前有效 | PRIMARY | 0；不以旧引用授权下载 | P | T3、T4 |
| 提交/补正再提交：`ApprovalService.submit` | 申请 `selectByIdForUpdate/updateStatus`；材料 count；专业 validator；审批单 insert/reset/read；日志 | R/W | RW | 申请行 `FOR UPDATE`；申请 version + 源状态；重提交复用审批单唯一约束 | 材料清单、申请状态、专业业务状态 listener 同一事务；reset 后读取审批单 | PRIMARY | 0 | P | T3、T4、T5 |
| 审批通过/驳回：`ApprovalService.approve/reject`；恢复登记 `approveAndCreatePerson` | 审批单 `selectById/decide`、申请 `updateStatus/completeWithTargetPerson`、材料 count、专业 validator/listener；人员建档 | R/W | RW；调用人员服务加入事务 | 审批 `PENDING + version`、申请状态 + version；非每个前置 SELECT 都 FOR UPDATE | 当前材料核验、审批权、状态、版本决定结果；通过不自动执行迁入 | PRIMARY | 0 | P | T3、T5 |
| 撤回/退回/取消：`ApprovalService.withdraw`；`ApplicationService.returnApplication/cancelDraft` | 申请状态更新、审批 `decide`、专业 listener、日志 | R/W | RW | 源状态 + version，源码未统一使用申请行锁 | 状态变更及后续再次提交/执行资格 | PRIMARY | 0 | P | T5 |
| 迁入执行：`MigrationService.executeIn` | `lockHousehold/lockIn/lockResidence`；`findResidence/countActiveMembership/findCompletedBatch/countCompletedInBatch`；residence/member/person/申请/迁入写入与日志 | R/W | RW | 先目标户后迁入行，再当前 residence；人员 UPDATE version；迁入/申请状态 version；批次校验不能读从库 | 插 residence、恢复/插成员、人员 REGISTERED、两个 COMPLETED 原子完成；插入 hook 失败要回滚 | PRIMARY | 0 | P | T1、T3 |
| 迁出执行：`MigrationService.executeOut` | `lockOut/lockResidence/lockHousehold`、成员/户主计数、archive 插入、residence 删除、member/person/household/申请状态写 | R/W | RW | 迁出、residence、household `FOR UPDATE`；删除/状态/person/head version 条件 | 归档、移除当前户籍、离户、变更户主/待销户、MIGRATED_OUT、COMPLETED 保持一致 | PRIMARY | 0 | P | T1 |
| 当前迁移/户籍办理详情：`MigrationService.detail`；当前人员 `PersonService.get/getByIdCard` | 申请、迁移、`findResidence/findHousehold/activeMembers`、材料/审批/日志；人员 scoped 查询 | R | RO | 普通读，无显式行锁 | 写后页面刷新和 executable 提示；实际执行仍重新校验 | PRIMARY | 0（本轮保守口径） | P | T1、T6 |
| 人员新增/审批建档：`PersonService.create/createForApproval` | `PersonMapper.countByIdCard/insertPerson`；`PersonIdCardImageMapper.selectById/bindPerson` | R/W | RW | 证件号唯一约束；图片绑定条件以 Mapper 为准，不声称所有预查加锁 | 影印本归属、未绑定状态、新人员与图片绑定 | PRIMARY | 0 | P | T6 |
| 人员修改：`PersonService.update` | scoped 人员查询、证件校验、`updatePerson` | R/W | RW | UPDATE `version` 条件；普通编辑保留生命周期状态 | 服务器读取版本和写入必须同主库，旧版本不能覆盖并发更新 | PRIMARY | 0 | P | T6 |
| 家庭户新增/修改：`HouseholdService.create/update` | `HouseholdMapper.countNo/lockPerson/activeMemberByPerson/insertHousehold/insertMember/lock/updateHousehold` | R/W | RW；内部 `detail` 自调用继承 RW，不另开 RO | 修改户、候选人员、有效成员查询 FOR UPDATE；户 version | create/update 返回 `detail` 必须读到本事务新户和成员 | PRIMARY | 0 | P | T7 |
| 成员新增/关系/离户/户主变更：`HouseholdService.add/updateMember/leave/changeHead` | `lock/lockMember/lockPerson/activeMemberByPerson/activeHead`；计数、成员/户更新 | R/W | RW；内部 `member/detail` 自调用继承 RW | 对应查询 FOR UPDATE；成员/户 version；待销户还检查当前户籍与在途迁入 | 写后 `member/detail`、主档/户主/有效成员/当前户籍联动 | PRIMARY | 0 | P | T7 |
| 单成员读取：`HouseholdService.member` | `findScoped` + `lockMember` | R（锁定读） | 基线 RO 与 FOR UPDATE 冲突；本轮已改成默认 RW，保留 FOR UPDATE | `lockMember` SQL 包含 FOR UPDATE，不能因方法名为查询就路由从库 | 写后返回和独立入口均须当前成员 | PRIMARY | 0 | P | T7、T14 |
| 当前户/成员详情：`HouseholdService.detail/members` | `findScoped/members` | R | RO | 不含显式 FOR UPDATE（与单 member 区分） | 写后刷新、当前有效成员与家庭户状态 | PRIMARY | 0（本轮保守口径） | P | T7 |
| S3 任务受理/重复键恢复：`AiPrecheckTaskService.create` | JDBC `findByIdempotencyKey`、task/capacity/stage/task_material/outbox/audit | R/W | 入口 NONE；首次幂等查和 DuplicateKey 后恢复查在 template 外；内部 RN | stage 按排序普通读取，再用状态/有效期/version 条件 UPDATE 抢占，不是 SELECT FOR UPDATE；capacity 条件 UPDATE；owner/taskType/endpoint/key 唯一约束 | 新 task + 材料占用 + capacity + outbox 原子提交；回放必须看到已提交记录 | PRIMARY（含事务外读） | 0 | P | T2 |
| S3 暂存上传/幂等回放：`AiStageMaterialService.upload` | JDBC `findByKey/findByKeyForUpdate/findByStageIdForUpdate`、claim/READY/FAILED/audit | R/W + 文件 | 入口 NONE；首次 `findByKey` 在事务外；claim、回放核验、终态更新分段 RN；文件处理在事务间 | owner/endpoint/key 唯一约束；回放/READY 核验 FOR UPDATE；version 条件 | READY、对象存在/大小摘要必须一致；UPLOADING/失败不能伪装成功 | PRIMARY（含事务外读） | 0 | P | T2 |
| S3 受理状态与材料状态：`AiPrecheckTaskService.get`；`AiStageMaterialService.get/delete` | JDBC owned task/stage 查询与 stage 状态变更 | R/W | get 无方法级事务；stage 状态检查可能进入 RN；delete 在 RN 变状态 | 需要变更时锁/条件更新；不是所有 get 都有事务 | 202 后跨实例轮询、上传后创建任务、取消后的重放 | PRIMARY | 0，不给旧受理/READY 结论 | P | T2 |
| S3 worker 领取：`AiPrecheckWorkerService.process` → `claim` | JDBC outbox event、inbox、task、task_material/stage、audit | R/W | process 无事务注解；仅 `transaction(() -> claim(event))` 为 RN；文件存在检查、请求构造、Python/Mock 分析及序列化均在该事务结束后执行 | 普通 task/inbox 读；`PENDING + version + next_attempt_at` 条件 UPDATE 领取租约，不是 FOR UPDATE；重复投递分支可能写 inbox 或新 outbox | 领取后读回 task/material，上下文随租约版本交给分析；重复、终态、活动租约不得重复分析 | PRIMARY | 0 | P | T2；其余 worker 分支静态核对，最终全量见验证文档 |
| S3 worker 完成/重试/失败：`completeSuccess/handleFailure/persistFailure/failMalformedKnownEvent/recordPoison` | JDBC task、stage、capacity、inbox、outbox、audit | R/W | completeSuccess、handleFailure、failMalformedKnownEvent、recordPoison 各自通过 template RN；private persistFailure 加入其调用方 RN，本身无注解/新事务 | 成功/可重试 UPDATE 校验 RUNNING、version、lease_owner、未过期租约；最终失败校验 PENDING/RUNNING + version；旧结果/旧失败被抑制 | 成功同事务恢复材料与释放容量；重试同事务写 PENDING + 新 MAIN outbox；最终失败同事务 FAILED、材料/容量、DLQ intent/inbox/audit | PRIMARY | 0；不能依从库误判租约或终态 | P | T2 的 retryable/permanent failure 与容量/材料断言；其他分支静态映射 |
| S3 worker 超时/投递恢复：`recoverExpiredLeases/recoverOne/recoverStalePendingDeliveries` | JDBC task、inbox、MAIN/DLQ outbox、stage、capacity、audit | R/W | 调度/恢复入口无事务注解；过期 RUNNING 和陈旧 PENDING 的候选扫描在 template 外；每 task 单独 RN；recoverOne/persistFailure 继承本次 RN | 条件 UPDATE 校验状态、version、租约过期或投递时间/次数；无 SELECT FOR UPDATE | RUNNING 超时重新 PENDING + outbox，超过处理上限则 FAILED；陈旧投递最多恢复 3 次，耗尽后失败；状态与重投意图必须原子 | PRIMARY（含事务外候选扫描） | 0 | P | T2；具体恢复 SQL/传播边界静态核对 |
| S3 outbox 领取/发布/确认：`AiPrecheckOutboxPublisher.publishDue/claim/markPublished/markPublishFailed` | JDBC outbox、task.last_enqueued_at、audit；RabbitTemplate | R/W + Broker | publishDue 无事务注解；候选 SELECT 在 template 外；claim、markPublished、markPublishFailed 分别 RN；Rabbit publish/confirm 等待位于这些事务之间，不与 DB 同事务 | 领取按 PENDING available_at 或过期 PUBLISHING lease 条件 UPDATE；后续状态按 PUBLISHING + lease_owner 条件更新；无 SELECT FOR UPDATE | claim 写 PUBLISHING 后读回；confirm 后才写 PUBLISHED 与任务入队时间；失败恢复 PENDING，状态持久化失败由租约回收允许重复发布 | PRIMARY（含事务外候选扫描） | 0 | P | T2 的 brokerUnavailableLeavesAcceptedTaskAndOutboxRetryable；真实 Broker NOT_VERIFIED |
| S3 暂存过期/上传恢复：`AiPrecheckLifecycleReaper.markExpiredReady/recoverStaleUploads` | JDBC stage + audit；私有对象/临时文件 | R/W + 文件 | reapMaterials 无事务注解；READY/UPLOADING 候选扫描在 template 外；每 candidate 过期或恢复更新为 RN；对象 promote 在 RN 之前，恢复时 objectExists 检查在回调内 | status + version，过期还校验 expires_at；无 SELECT FOR UPDATE；UPLOADING 超过 5 分钟才列候选 | 过期 EXPIRED；已提升对象恢复 READY，否则 UPLOAD_FAILED；数据库与文件不是一个原子事务 | PRIMARY（含事务外候选扫描） | 0 | P | T2 的 lifecycleExpiresReadyFilesAndNeverDeletesBoundFiles、staleUploadingRowRecoveredFromPromotedFileKeepsIdempotencySemantics |
| S3 不可用对象/孤儿临时文件回收：`AiPrecheckLifecycleReaper.deleteUnavailableObjects/deleteUnreferencedTempFiles` | JDBC stage 候选、file_deleted_at 标记、temp_object_key 引用计数；私有文件 | R/W + 文件 | 没有事务注解，也未包 TransactionTemplate；候选读、物理删除、DB 标记是分步执行；临时文件至少 1 小时后再检查引用 | 文件标记 UPDATE 限定 UPLOAD_FAILED/CANCELLED/EXPIRED + file_deleted_at NULL；引用计数普通读，不能误标为事务锁保护 | 文件删除前的终态/引用检查不能用从库旧数据；BOUND 不属于不可用状态；失败保留后续重试机会 | PRIMARY（含全部事务外读写） | 0 | P | T2 生命周期/缺对象断言；其他文件回收边界静态核对 |
| 实时授权：`AuthService.loadUser/loadUserById`、`regionCodesFor`、登录用户组装 | `AuthMapper.selectByUsername/selectByUserId/selectPermissionCodesByRoleId/selectAllEnabledPermissionCodes/selectEnabledRegionCodes` | R（login 还写） | loadUser/loadUserById RO；login 无声明，内调用不独立开 RO；region helper NONE/继承调用方 | 无显式行锁 | 禁用账号/角色、权限撤销、DataScope 区划必须立刻用于本次鉴权；登录最后时间另写 | PRIMARY | 0；不能用复制延迟延长授权 | P | T8 |
| 字典查询：`DataDictionaryService.search/byType/get/enabled` | `DataDictionaryMapper.search/count/findByTypeCode` | R | NONE，继承调用方；byType 有缓存切面 | 普通读 | `enabled` 用于新业务合法性检查，写事务内不使用参考缓存；get/search 可作管理写后查看 | PRIMARY；独立只读 byType 展示才 REPLICA_ELIGIBLE | 展示容忍受控 TTL；管理与业务决策 0 | R；强一致子场景 P | T9、T11/T12 |
| 字典写入：`DataDictionaryService.create/update/enable/disable` | 字典 insert/update/updateStatus/findById、事务审计 | R/W | RW；create/update 读回在当前事务；提交后缓存失效 | version + 源状态；无显式 FOR UPDATE | 返回新字典、后续 enabled 校验；回滚不失效 | PRIMARY | 0 | P | T9、T12 |
| 区划查询：`AdminRegionService.search/get/tree/children` | `AdminRegionMapper.search/findByCode` | R | NONE，继承调用方；tree/children 有缓存切面 | 普通读 | 树/子节点展示可延迟；父级/状态/环校验及授权区划不同于展示缓存 | PRIMARY；独立只读 tree/children 展示才 REPLICA_ELIGIBLE | 展示受控 TTL；校验/写后 0 | R；强一致子场景 P | T9、T11/T12 |
| 区划写入：`AdminRegionService.create/update/enable/disable` | 区划 find/search/countEnabledChildren/insert/update/updateStatus；审计 | R/W | RW；内部 `get` 自调用保留 RW | version + 源状态；父子/环校验是普通读，不能写成已加锁 | 写后 get、启停父子约束；提交后 region 缓存失效 | PRIMARY | 0 | P | T9、T12 |
| 常规统计/仪表盘：`StatisticsService.getSummary/getChartData/getRecentLogs`、`DashboardService.overview/charts` | `StatisticsMapper` 与 `DashboardMapper` 各聚合 SELECT | R | RO | 无显式行锁 | 展示用途，无办理状态决策；授权仍实时主库，结果带 DataScope | REPLICA_ELIGIBLE | 允许已约定的复制延迟，非实时统计 | R | T10 |
| 统一统计缓存：`UnifiedStatisticsService.overview/charts/migrationTrend/regionDistribution/floatingPopulation/certificateExpiry/householdDistribution/keyPopulation` | 缓存 miss 调用 DashboardService；部分直接 StatisticsMapper | R | 入口 NONE；代理调用 dashboard 时 RO；直接 Mapper 查询无方法级事务 | 无显式行锁 | cache key 含 DataScope、用户/部门/区划/日期/参数；不得作业务决策 | REPLICA_ELIGIBLE（独立展示） | overview/trend 默认 TTL 5 分钟，叠加复制延迟；不是实时读 | R | T10 |
| 只读检索/历史归档/日志：人员和户 search、`Phase11QueryService`、`ComprehensiveQueryService`、`ResidenceArchiveService`、`LogQueryService` | 各 scoped search/count/profile/archive/log SELECT | R | 以上声明 RO；不覆盖前述当前详情 PRIMARY 规则 | 普通读 | 非即时写后、非办理/鉴权决策的独立检索 | REPLICA_ELIGIBLE（明确选择的展示入口） | 需赵书亚和业务方约定；未约定前 PRIMARY | R | T6/T7；query/archive/log 对应既有测试源码 |
| 其余业务写入：注销、流动登记/居住证、证件、重点人口、导出与下载计数 | `CancellationMapper`、`FloatingResidenceMapper`、`CertificateMapper`、`KeyPopulationMapper`、`ExportMapper`，及申请/材料/日志 | R/W | 各 create/update/execute/issue/endorse/cancel/normal/executeSensitive/download 为 RW；到期/清理 worker RN；证件普通读取无声明 | 按对应 Mapper 锁/状态/version；不能把整组宣称每条 SELECT 均行锁 | 当前生命周期/审批/导出权限/次数和读回依赖主库 | PRIMARY；本轮未开放其详情从库白名单 | 0（办理） | P | 对应 Phase04/05/06/09/10 测试基线已通过；最终全量见验证文档 |
| 业务审计：`OperationLogService.recordTransactional/record` | `OperationLogMapper.insert` | W | 前者 MANDATORY，后者 RN | INSERT；不要求伪造 FOR UPDATE | 业务审计与外层回滚边界不同于独立登录/失败审计 | PRIMARY | 0 | P | T5、T8；仅静态映射 |

## 3. 自动化证据索引及验证结果

| 编号 | 源码位置（均在 `src/test/java/com/wjx871/population/` 下） | 对应断言主题（静态核对） | 本轮执行状态 |
|---|---|---|---|
| T1 | `migration/Phase03MigrationIntegrationTest.java` | DRAFT 原子创建、当前户籍、区划不匹配、执行回滚、版本/成员/户主状态 | 基线全量已通过；最终全量见验证文档 |
| T2 | `ai/precheck/AiPrecheckIntegrationTest.java` | 用户/endpoint 幂等、并发单任务/单材料、受理与 outbox 原子提交、容量回滚、事务原语与锁持续、候选版本/权限变化 | 基线全量已通过；最终全量见验证文档 |
| T3 | `ai/precheck/AiConfirmIntegrationTest.java` | 一次草稿与回放、并发确认、材料插入失败回滚、BOUND 生存与删除补偿、沿用提交和独立 L3 核验 | 基线全量已通过；最终全量见验证文档 |
| T4 | `material/ApplicationMaterialSubmitConcurrencyIntegrationTest.java`、`ApplicationMaterialFeatureOffIntegrationTest.java` | 申请行锁协调材料变更与提交、功能关闭兼容 | 基线全量已通过；最终全量见验证文档 |
| T5 | `approval/Phase02ApprovalIntegrationTest.java`、`application/Phase12ApplicationReturnIntegrationTest.java` | 材料门槛、独立审批、退回补正/撤回、乐观锁 | 基线全量已通过；最终全量见验证文档 |
| T6 | `person/PersonMapperTest.java`、`Phase08PersonIdentityIntegrationTest.java`、`PersonRegionOwnershipAuditIntegrationTest.java` | 旧服务器版本不能覆盖并发修改、保留生命周期、区域归属 | 基线全量已通过；最终全量见验证文档 |
| T7 | `household/Phase08HouseholdMasterDataIntegrationTest.java` | 家庭户/成员/户主/离户与版本规则；本轮事务边界另见 T14 | 基线全量已通过；最终全量见验证文档 |
| T8 | `auth/AuthRbacIntegrationTest.java` | 禁用用户/角色、有效权限、授权区划树与登录审计；没有真实主从路由断言 | 基线全量已通过；最终全量见验证文档 |
| T9 | `reference/Phase09DataDictionaryIntegrationTest.java`、`Phase09AdminRegionIntegrationTest.java` | enabled/disabled 可见性、权限、版本、父子/环约束；新增缓存断言另见 T11/T12 | 基线全量已通过；最终全量见验证文档 |
| T10 | `stats/StatisticsScopeIntegrationTest.java`、`UnifiedStatisticsScopeCacheTest.java`、`dashboard/DashboardMapperTest.java`、`DashboardServiceTest.java` | DataScope 缓存隔离、有效业务行口径、空统计；不证明复制或真实 Redis 一致性 | 基线全量已通过；最终全量见验证文档 |
| T11 | `reference/ReferenceCacheIsolationIntegrationTest.java` | 字典可见性、权限切换、归一化与缓存隔离 | 本轮定向测试 15 项通过 |
| T12 | `reference/ReferenceCacheTransactionIntegrationTest.java` | 真实 Spring 代理事务顺序、提交/回滚失效、活动 RW 绕过缓存 | 本轮定向测试 11 项通过 |
| T13 | `stats/StatisticsScopeCacheIntegrationTest.java` | 统计缓存 DataScope 隔离、JSON 命中、实时授权范围变化、启停/故障回源 | 本轮定向测试 13 项通过 |
| T14 | `transaction/CoreTransactionBoundaryIntegrationTest.java` | 12 项真实 HTTP/H2 业务链事务/连接/回滚观测，1 项静态 32 入口合同，1 项 S3 私有 helper 的传播实验；后两项不等于真实业务链执行 | 本轮定向测试 14 项通过 |
| 真实环境 | MySQL 主从、延迟/断连恢复、Nginx 双实例、共享 Redis 与文件 | 必须提供物理连接身份、SQL 节点、故障注入与恢复证据 | NOT_VERIFIED |

已完成基线全量：**677 项通过，耗时 6 分 11 秒**；本轮新增四个 suite 定向运行共 **53 项通过，耗时 1 分 54 秒**（15 + 11 + 13 + 14）；最终完整 Maven 回归为 **730 项通过、0 failures/errors/skipped，耗时 6 分 52 秒**。这些结果不证明真实 MySQL 主从、Redis 双实例或 Broker 行为。完整命令、环境和日志以同目录 `s5a2-verification.md` 为准。

## 4. 给路由实现方的主库清单与从库白名单

路由优先级建议为：显式强制 PRIMARY / 正在执行的 RW 事务 / 锁定查询 / 一致性敏感入口 > 独立展示白名单 > 默认 PRIMARY。`readOnly=true` 不是自动从库许可，SQL 是 SELECT 也不是许可。尤其必须覆盖：

- `AuthService.loadUser/loadUserById` 及其角色权限、账号状态、有效区划读取；`TokenRevocationService` 的 strict Redis 行为不应被普通缓存的可选回源语义取代。
- S3 template 外的上传/任务幂等首次查、唯一冲突后重查、受理后状态轮询；路由只能看 Spring 事务状态会漏掉这些读。
- 全部 `FOR UPDATE`，包括 `HouseholdMapper.lock/lockScoped/lockPerson/lockMember/activeMemberByPerson/activeHead`、`PersonMapper.selectScopedByIdForUpdate`、`BusinessApplicationMapper.selectByIdForUpdate`、迁入迁出锁、AI 解析与确认的 JDBC 锁。不能只扫描方法名 `lock*`。
- 材料清单/核验、提交/审批/执行、当前 residence/成员/户主、唯一性/状态/version 校验；乐观锁必须与前置读取、更新结果和回读共同走主库。
- 写方法内部 `detail/member/get/require` 自调用、返回视图、提交成功后的即时详情刷新。HTTP 切换到另一个实例也必须保持写后读语义；不能依赖单实例 ThreadLocal 或本机会话粘滞作为跨请求保证。

允许从库的初始白名单仅为矩阵所列统计/仪表盘、独立字典 byType 和区划 tree/children 展示、已明确选定的只读列表/历史检索。未完成延迟容忍约定的候选仍走主库。参考数据缓存仅覆盖展示，不承担授权与业务合法性检查；从库节点健康不等于复制追平。当前单数据源实现不提供真实路由证据。

## 5. Redis 使用清单、参考缓存机制及限制

### 5.1 全部生产 Redis 调用点

以 `rg --no-ignore` 扫描 `src/main/java` 中 `OptionalRedisService`、Redis template 与调用点，业务消费者为下列四类；底层连接和 JSON/marker 操作统一由 `OptionalRedisService` 执行。清单只描述现有调用，不把未实现的失效策略计作已有能力。

| 使用方 | 键与操作 | TTL / 主动失效 | 故障语义与限制 |
|---|---|---|---|
| `ReferenceCacheAspect` | GET/JSON SET：`<prefix>:region:tree`、`region:children:<code>`、`dictionary:v2:MANAGE:<TYPE>`、`dictionary:v2:ENABLED_ONLY:<TYPE>` | region/dictionary 默认 30 分钟；对应 create/update/enable/disable 成功提交后 `evictPattern(region:*)` / `evictPattern(dictionary:*)`，底层 KEYS + DEL；回滚不失效 | Redis 关闭、GET/SET/DEL 失败走数据库原方法或保留数据库成功；活动 RW 绕过 GET/SET；权限空间不能相互复用 |
| `UnifiedStatisticsService` | overview/charts GET/JSON SET；migrationTrend/regionDistribution/floatingPopulation/certificateExpiry 复用这些缓存；householdDistribution/keyPopulation 直接查 Mapper，不写统计缓存 | overview/trend 默认 5 分钟；**未发现业务写入后的 statistics 主动失效调用，仅 TTL** | 失败按当前 DataScope 查询数据库；已成功写入的统计缓存可在业务写入后继续保留旧值直至 TTL，不能保证写后实时统计 |
| `TokenRevocationService`：`SHARED_REDIS_REQUIRED` | `security:jwt:blacklist:<jti>`；strictPutMarker 使用原子 SET + 绝对到期时间，strictContains 查存在；登录还写读 `security:jwt:login-probe:<UUID>` | blacklist 到 token expiresAt；probe 5 秒；严格写确认才允许登出成功 | 禁用/超时/断连/认证失败或无法确认均抛存储不可用，不能解释为“未撤销”；认证、登录预检、登出按 strict 契约失败，不回退本机 Map 或普通 MySQL 缓存逻辑 |
| `TokenRevocationService`：`SINGLE_INSTANCE_LOCAL` | 本地同步 Map 保存 jti 到期时间；可选 `putMarker/contains` 使用同一 blacklist key | marker 相对 TTL 到 token 过期；本地定时有界清理；本地已有撤销不依赖 Redis | 可选 Redis 失败被 wrapper 吞为未命中；单实例本地模式不提供跨实例撤销保证，不能用此模式证明双实例安全 |
| `system.HealthController.health` | 配置启用时调用 `OptionalRedisService.ping()`，底层连接 PING；无业务 key | 无缓存、无 TTL、无失效；另独立执行数据库 SELECT 1 | 状态报告 UP/DOWN/DISABLED，PING 失败返回 false；`cacheMode=MYSQL_FALLBACK` 只是健康响应中的普通缓存模式标签，不覆盖 strict 撤销策略，也不证明复制或所有 Redis 操作权限正常 |

`OptionalRedisService` 的普通 GET/SET、contains/putMarker、evict/evictPattern 是可选语义，strictContains/strictPutMarker 是严格安全语义；共享同一个 Redis 客户端不表示故障策略可以互换。`ping` 也不能作为撤销存储已完成严格读写确认的证据。

统计完整 key 由 `UnifiedStatisticsService.key` 拼接：

```text
<keyPrefix>:statistics:<type>:<dataScope>:<userId或->:<regionCode或->:<regionCodes列表>:<departmentId或->:<LocalDate.now()>:<Arrays.toString(args)>
overview 的 args = [periodDays, expiryDays]
charts 的 args = [days, regionLimit]
```

`regionCodes` 是当前展开后的有效区划列表，不能只保留根区划；用户/部门/范围/有效子区划变化都会改变键。日期与全部参数也是隔离维度，不能退化成仅按接口名缓存。列表按当前 Java `toString` 表达保留原顺序，本轮没有添加排序或摘要协议；同一集合顺序不同可能多占缓存，不应据此删除必要维度。实时权限/账号/区划加载应强制主库，本轮单数据源尚无物理路由验证；统计 key 不缓存授权决定。T10/T13 只证明所覆盖的范围/结果与缓存交互，不能证明 Redis 集群或复制一致性。

### 5.2 本轮参考缓存机制

`ReferenceCacheAspect` 的本轮实现与代理集成测试覆盖以下行为：

- 字典 key 使用 `dictionary:v2:MANAGE:<TYPE>` 与 `dictionary:v2:ENABLED_ONLY:<TYPE>` 两个命名空间，外层仍有配置的 keyPrefix。每次调用读取当前 `permissions` 中的 `dictionary:manage` 决定命名空间，不依赖用户名、userId 或角色；这是当前结果语义分区，不缓存权限本身。不同用户具有相同可见性时可共享，失去管理权限后不能继续读管理空间。
- type `trim` 后按 `Locale.ROOT` 大写，与服务查询归一化一致；null/空白类型绕过缓存并继续原业务调用，避免把无类型查询错误拼成共享键。区划保持 region:tree / region:children 语义。
- 检测到**活动写事务**时，字典/区划参考查询直接执行原方法，既不取缓存也不回填。写后返回、事务内校验不能得到旧缓存，也不能把未提交结果放进共享缓存。活动 RO 仅在独立展示条件下允许缓存，不能越过本矩阵的主库边界。
- 缓存切面显式声明顺序，位于事务 advice 外层；写方法正常返回之后应已完成提交，或由 `CacheInvalidation.afterCommit` 挂到更外层已有事务。没有提交就不做失效；外层事务后续回滚时不失效。动态测试必须覆盖代理顺序，不能只用裸 aspect 单测推断事务提交。
- 写入提交后失效同类命名空间；字典通配符需同时覆盖 v2 两类空间（以及原键），区域更新覆盖 tree/children。Redis 关闭、读取/写入/删除失败时沿用可选缓存回源，数据库成功不因展示缓存失败而改写为回滚。此语义不能用于 Token 撤销存储。

现存 `OptionalRedisService.evictPattern` 使用 Redis **KEYS** 再 delete，扫描成本随数据库键数量增长，可能阻塞 Redis；本轮没有改成 SCAN、精确键索引或版本化失效，也没有提供其性能结论。赵书亚需在实际键规模下测量，并另行评审后续策略。

提交后删除缓存并不能消除跨实例旧值回填：实例 A 的旧查询先开始，实例 B 提交更新并删除键，A 后完成查询又把旧值写回，旧值可存活一个新 TTL。删除失败也可能保留原值直至过期。默认 dictionary/region TTL 为 30 分钟，overview/trend 为 5 分钟，均以最终环境配置为准；前述上限是**旧值缓存成功写入后的一段 TTL**，还要加数据库复制延迟。Redis 恢复不会自动清除所有旧值，重连成功不等于一致性恢复。

v2 命名空间保证升级后的代码不读取历史字典共享 key，但旧版本实例仍可能读写旧共享 key；混合版本运行不算权限隔离验收，须所有实例升级后再进行 S5-B 验证。本轮不进行部署或清理 Redis。

因此本轮只解决可见性空间隔离、活动 RW 绕缓存和提交边界，不宣称跨实例线性一致或缓存永不陈旧。强一致读取走主库并绕过展示缓存；如业务要求更短陈旧窗口，由赵书亚共同评审 TTL、精确失效/代际键/防旧回填协议及观测，不在本轮添加新基础设施。

## 6. 赵书亚协同事项与真实环境用例

赵书亚起草 DataSource/路由、复制、连接池与缓存配置；张锦源复核本矩阵、SQL 锁、写后读取与授权边界。公共 `pom.xml`、SecurityConfig、DataSource、application 配置、迁移脚本采用单一改动集并双人核对，避免共享文件并行覆盖。本轮不代做这些变更，不将“已有两条数据库连接”称为读写分离完成。

| 用例 | 注入与操作 | 应验证的行为 | 必须留存的证据 | 状态 |
|---|---|---|---|---|
| 主从路由基准 | 两个实例分别调用强一致/白名单读/写，并记录数据库节点身份 | PRIMARY 清单从不落从库；候选展示才可从库 | instanceId/traceId、事务 readOnly/active、Mapper/SQL、实际连接 server_id/read_only/节点标识 | NOT_VERIFIED |
| 复制延迟与写后读 | 人为暂停/延迟复制；A 创建/更新申请、材料、字典；立即通过 B 查详情/提交/审批/执行 | 当前业务状态从主库看到新数据；字典展示按说明可能陈旧；从库展示明确受延迟预算约束 | 提交时间、主从位点/延迟、A/B 实际 SQL 节点及返回值 | NOT_VERIFIED |
| 主库失败 | 在提交/审批/执行或锁查询时断开主库 | 明确失败，不能转从库继续写或用旧状态放行；重试守幂等 | 错误分类、回滚/持久化检查、恢复后重复请求结果 | NOT_VERIFIED |
| 从库断连与恢复 | 切断从库，调用统计/独立展示，恢复连接但暂不恢复复制，再追平 | 保守回主/明确失败；恢复连接不立即代表可读，确认健康与追平后恢复白名单 | 故障窗口、整次只读操作的连接身份、健康/位点与恢复时间 | NOT_VERIFIED |
| 双实例幂等与状态 | A 上传/受理，B 立即同 key 回放/查询，A/B 并发同 key；包含事务外查 | 同 owner/endpoint 一个材料/任务/草稿；不得因从库漏读返回错误成功或生成第二份 | task/stage/receipt/outbox/capacity 行数、状态、实例与路由日志 | NOT_VERIFIED |
| 双实例锁与版本 | A/B 同时迁入/迁出、改成员/户主或提交与删材料；旧 version 重试 | 锁/状态/version 共同维护不变量；一方失败也不留下部分业务状态 | 锁等待与错误、提交前后 residence/member/person/application 快照 | NOT_VERIFIED |
| 共享缓存权限 | A 管理权限预热禁用字典，B 普通权限读取；反向预热；同用户权限变更 | MANAGE 与 ENABLED_ONLY 隔离；实时 permissions 决定本次 key | 脱敏 key、请求权限、DB/Redis 交互；不输出敏感完整记录 | NOT_VERIFIED |
| 提交/回滚失效 | A 外层事务修改参考数据，B 查询；分别提交、强制回滚；RW 内先读再写再读 | 未提交结果不入缓存；提交后失效；回滚不失效；RW 查不碰缓存 | 事务生命周期、Redis 调用顺序和返回值 | NOT_VERIFIED |
| 旧值回填与 Redis 故障 | 用闩锁延迟 A 旧读取；B 更新提交失效；再释放 A；另注入失效失败/Redis 重连 | 如实展示残留 TTL 限制；核心办理走主库；不得把展示缓存称强一致 | 故障时间线、TTL、旧键回填、回源节点、恢复验证 | NOT_VERIFIED |
| 双实例退出与共享文件 | A 退出后 B 请求；A 上传/绑定，B 下载；单实例停机后重试 | 另实例撤销一致性按 S5-A1 strict 契约；文件可用且仍鉴权；不依赖本机路径 | strict Redis 故障/恢复记录、A/B 日志、私有文件访问和校验 | NOT_VERIFIED |
| KEYS 与性能 | 相同数据量/键规模/并发/预热条件比较缓存开关与失效 | 记录 KEYS 最坏延迟、吞吐/错误率/P50/P95；不编造提升百分比 | 环境配置、原始结果、SQL/Redis 慢操作、实例资源指标 | NOT_VERIFIED |

以上是后续 S5-B/团队联调用例，不是本轮已运行脚本，也不要求本轮搭建主从或双实例。

## 7. 尚未核销事项

本轮审计状态保持：`#64 OPEN`、`#3 OPEN`、`#33 OPEN`；`#21 FIXED_VERIFIED`。S5-A1/本轮局部代码与自动化不能替代 #64 的真实双实例联调。#3 同步身份证 OCR 长事务仍独立存在，不能因新异步预审可用而称已修复；#33 与真实链路证据按原账本继续跟踪。

真实 MySQL 主从、S5-B、Python 实际服务、RabbitMQ 实际 Broker、前端联调均为 **NOT_VERIFIED**。上述自动化结果不核销这些环境验收事项。
