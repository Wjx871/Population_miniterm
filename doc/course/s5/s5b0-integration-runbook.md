# S5-B0 分布式联调 Runbook（冻结候选）

日期：2026-11-06。事实基线：`9f9c13727c552f031b97862dea1595db7e643453`。

本文件是 S5-B1 的执行准备，状态为 **CODE_CANDIDATE / 冻结候选**，不是组员确认记录，也不是部署授权。本轮没有启动服务、连接团队数据库、运行真实推理、发布/消费消息或执行故障注入。所有真实环境验收仍为 **NOT_VERIFIED**；没有实现或未提供的必要依赖标为 **BLOCKED**；未取得实际约定的字段、地址、接口及操作责任确认为 **PENDING_CONFIRMATION**。确认信息缺失时，不得由脚本自动选择默认环境并继续部署。

## 1. 事实源、责任与执行约束

联调顺序采用主工作区 `专业工程实训/邑循项目详细分工.md` 第 10.2 节的六步顺序：样例/状态 → Java/Python → MQ/持久化任务/前端进度 → 人工确认和草稿 → 缓存/主从/双实例重跑 → 压测/故障/恢复。路线图 S5 和 [S1 架构决策](../s1/architecture-decisions-and-confirmations.md)用于理解责任；S1 仍为 REVIEW DRAFT，历史“当前事实”不代替本基线源码。

| 人员 | 能力主责 | 本 Runbook 中的责任 |
|---|---|---|
| 张锦源 | Java、业务编排、权限、事务、消息业务语义 | 核对 API/RBAC/DataScope/幂等/事务；执行经确认的合成业务；整理代码及业务证据 |
| 赵书亚 | 数据、缓存、路由、双实例、部署运维、压测 | 确认隔离环境；提供真实拓扑与节点；负责基础设施启停、注入、恢复、备份及拟清理清单 |
| 王家兴 | Python RAG、OCR/文档分析与评测 | 确认实际内部接口、schema、认证、文件读取、推理版本与故障语义；恢复 Python 服务 |
| 张蕊 | Web、JavaFX、图表与交互 | 确认前端环境和页面；验证引用、进度、修正、显式确认与失败表现；留存脱敏截图 |

以上为既有分工，不表示已取得实际环境确认。共同配置、安全、数据源、依赖及迁移的变更由张锦源与对应主责复核。S5-B0 不实施这些改动。

### 1.1 进入 S5-B1 前的门禁

1. 按 [14 项环境契约](s5b0-environment-contract.md)和[跨组交接清单](s5b0-team-handoff.md)逐项填写实际信息、确认人、时间与证据；未确认项保留 `PENDING_CONFIRMATION`。实际环境主责赵书亚必须明确确认隔离库、合成账号、允许操作、故障范围及恢复方案。
2. 真实 Python Document Analysis **BLOCKED**：当前只有 `DocumentAnalysisClient` 与 `MockDocumentAnalysisClient`，没有 HTTP 实现及相应实际 URL/服务认证配置。`AI_PRECHECK_ANALYSIS_CLIENT` 改成 `HTTP` 不会自动获得实现；需另行确认/实现并复核，S5-B0 不补做。
3. 真实主从路由 **BLOCKED**：当前单 DataSource，无主从路由器和专属配置。赵书亚提供实际实现及节点观测方式后，才执行主从验收；两条 URL 或事务注解不算路由。
4. 跨实例文件验证 **BLOCKED**：当前没有已确认共享卷拓扑。普通上传、AI 私有暂存、正式材料来源和导出均需指向同一共享资源；相同相对路径字符串不证明相同存储。
5. Nginx 示例仅代理单个 `127.0.0.1:8080`；仓库无完整双实例 Compose/upstream。赵书亚需提供真实配置、A/B JVM 身份、非粘滞分流及故障切换方式；未提供为 `BLOCKED`。
6. 当前 `/api/ops/overview` 仅为 S1 PROPOSED，未实现；运维 API/权限/指标窗口、JavaFX 对接为 `PENDING_CONFIRMATION`，需要该能力的验收子项为 `BLOCKED`。不得新增匿名状态接口或把未采集指标填成 0。
7. 数据库已包含 V4_001—V4_018。两份部署指南仍写至 V4_010，数据库指南仍称最新 V4_016，存在部署阻断风险。本轮只记录差异；不得运行 Fresh、初始化、迁移或自行修改升级流程。共享库变更必须另获数据主责授权。
8. 所有实例均使用基线或后续共同批准的同一版本；混跑旧字典缓存命名空间版本不算权限隔离验收。双实例配置比较只保留安全的一致/不一致结论，不输出密钥、密码、JWT、服务令牌或其可复用摘要。

### 1.2 操作与证据规则

- 本文所有 HTTP 操作仅供真实执行获得确认后使用，**不属于 S5-B0 只读预检**。GET 可能更新业务状态、写审计、访问/回填缓存或消费文件引用，不能按 HTTP 方法推断无副作用；预检仅允许显式指定的 `/api/health`。
- 只用隔离数据库、合成材料和合成人员。测试账号用“申请人R1/申请人R2/核验岗L3/审批岗/执行岗/字典管理岗/普通查看岗”等类别；实际账号映射、权限和部门区划由主责确认。凭据在受控工具内输入，不写入本文、命令历史、证据或 Git。
- A/B 表示两个独立 JVM，不是两个服务对象。先通过直达端点定位，再通过 Nginx 验证实际分流；每次保存 `instanceId/traceId` 与实际数据库节点，不依赖粘滞会话。地址、观察点、注入命令均为 `PENDING_CONFIRMATION`，没有默认部署地址。
- 操作片段是流程记法，不是可直接执行的批处理。花括号变量均须取自本次响应、受控配置或合成夹具映射；不得把示意 `taskId`、候选 token、版本或幂等键作为有效常量。
- 同一次幂等回放复用原规范化请求与原 key；不同场景生成新 key。材料上传的 key 位于 `Idempotency-Key` 头；任务/确认的 key 位于 JSON。key 必须符合接口字符/长度规则，不能把含中文或尖括号的占位符直接发送。
- 统一响应当前为 `code/message/data/timestamp`，不能沿用旧样例 `code=0`。HTTP状态和业务code分别记录：任务创建实际为HTTP 202、业务code=200；上传/确认首次为201/201、回放为200/200。文件响应没有JSON code时记“不适用”。
- 使用[证据模板](s5b0-evidence-template.md)逐项留存原始证据索引。STATIC/H2/MOCK/HTTP_STUB/SYNTHETIC_DEMO 与 REAL_ENV 分开；历史730项不能代替本次执行记录，本轮实际重跑也不能复制到真实环境PASS栏。所有缺陷进入 `BUG-xxx / INT-xxx`。
- 发现越权、数据泄漏、重复主档、错误放行已撤销 JWT、状态部分提交或文件归属不明，立即停止相应阶段；张锦源保全脱敏业务证据，赵书亚隔离受影响流量并按已批准方案恢复。
- 本文不授权删除、清库、覆盖、批量移动或密钥轮换。任何清理先列确切对象清单、原因、影响与恢复点，取得用户明确许可再由负责人执行；不明文件保留。不得把删除运行数据当作“重试准备”。

## 2. Phase A：环境与契约确认

| 项目 | 内容 |
|---|---|
| 前置条件 | 精确基线与施工变更已复核；环境矩阵、配置比较、交接表和本 Runbook 均为候选，等待逐项确认 |
| 主责/参加 | 赵书亚主责环境；张锦源核对 Java；王家兴、张蕊确认各自服务；确认记录目前均 `PENDING_CONFIRMATION` |
| 准备数据 | 独立隔离库；合成人员R1/R2、不相交区划/部门、有效目标户、缺件与版本冲突夹具；独立L3/审批/执行身份；合成PDF/PNG及其SHA-256 |
| 请求/操作 | 执行下方 A-01—A-07，所有环境操作均须先确认范围 |
| 预期结果 | 14项输入可追溯，A/B一致性结论不泄密；阻断项有主责和计划；健康仅证明有限连通性 |
| 停止条件 | 隔离库归属不清、迁移授权缺失、配置不一致、存储不共享、必要实现缺失、没有恢复负责人 |
| 必存证据 | 确认表、版本与SHA、A/B身份、脱敏拓扑/配置比较、迁移盘点、健康时间及状态；不得保存连接密码/原始环境文件 |
| 回滚/清理责任 | 赵书亚维护隔离环境与备份；各服务负责人恢复本模块；本阶段不清理已有数据，清理仍需对象清单和许可 |
| 真实服务 | Offline可核对静态事实；有限健康探测需要显式目标与授权；完整Phase A通过仍须真实环境主责确认 |
| 当前验收 | `NOT_VERIFIED`；Document Analysis、路由器、共享卷和双实例部署依赖 `BLOCKED`；实际输入 `PENDING_CONFIRMATION` |

```text
A-01 记录本次 runId、UTC/Asia/Shanghai时间、Git完整SHA和所有服务版本。
A-02 环境主责逐项确认14项契约、隔离库名、合成数据、允许操作和恢复点。
A-03 安全核对A/B：共同JWT_SECRET；strict模式；相同Redis一致端点/database/prefix；
     真实DB拓扑；共享文件资源；不同instanceId；端口是否需要不同由部署拓扑决定。
A-04 核验Redis>=6.2、SET/PXAT/EXISTS ACL、noeviction、持久化/备份和时钟，
     仅记录主责提供的脱敏确认与证据，不由预检执行写命令。
A-05 盘点V4_001—V4_018及数据库版本证据；不得执行迁移或修改共享数据库。
A-06 仅对已明确指定的A、B及Nginx入口 GET /api/health，有限超时且不记录敏感响应体；
     分别核对data.database、redisStatus、cacheMode；200/PING不作strict/复制验收。
A-07 对缺失依赖记录BLOCKED和负责人；确认结果入模板后，再由主责决定可执行子阶段。
```

## 3. Phase B：Java ↔ Python 接口联通

| 项目 | 内容 |
|---|---|
| 前置条件 | Phase A对应输入已确认；政策服务真实HTTPS/服务认证/来源注册版本可用；文档分析实际客户端实现和文件读取契约就绪后才能执行其子项 |
| 主责/参加 | 王家兴主责Python；张锦源主责Java适配与安全；赵书亚提供内部网络/证书；张蕊核对引用展示 |
| 准备数据 | 无个人信息的合成政策问题、受控来源及章节；合成PDF/PNG；合法/无依据/错误schema/慢响应夹具；服务认证由受控配置注入 |
| 请求/操作 | B-01—B-05；文档分析使用最终双方确认的内部契约，不伪造当前存在的HTTP客户端 |
| 预期结果 | 政策响应模式与实际来源一致；一次主查询至多一次Python远调；错误安全降级；文档结果有来源/版本/空值并可验证 |
| 停止条件 | 用户JWT或个人信息外发、引用伪造、客户端决定URL/主键、无认证访问、超时无界、缺少真实分析客户端 |
| 必存证据 | 脱敏schema/版本、traceId/requestId、耗时、模式/引用核验、实际调用次数、认证边界；不保存服务令牌或原始敏感输入 |
| 回滚/清理责任 | 王家兴恢复Python测试夹具；赵书亚恢复网络/证书；张锦源记录适配差异；关闭远程开关须另由主责确认且不得掩盖失败 |
| 真实服务 | 真实联通必须真实Python；Mock/HTTP Stub只能单列为局部证据 |
| 当前验收 | 政策真实服务 `NOT_VERIFIED`；Document Analysis真实联通 `BLOCKED`；实际URL、认证、文件流/只读卷 `PENDING_CONFIRMATION` |

```text
B-01 以合成会话向Java POST /api/assistant/policy/query，JSON使用实际Controller字段：
     question为本次合成问题，history按约定提供；记录sourceMode/evidenceStatus/引用/版本。
B-02 政策HTTP客户端由可信配置调用 POST /internal/policy-query；这是Python端契约，
     不是Java新增内部入口。核对HTTPS、独立服务认证、schema=1.0、requestId/traceId。
B-03 王家兴在已授权测试服务分别提供正常、无依据、错schema/伪造引用、超时响应；
     每次只发一个用户请求，观察远调次数和最终模式；失败时Java本地fallback，
     不保留失效Python回答/版本，不回落到付费Legacy模型冒充本地结果。
B-04 文档分析：先确认实际HTTP客户端、认证及读取方案；缺一项记录BLOCKED并停止该子项。
     就绪后按双方版本化契约以合成材料验证正常、缺件、低置信度、冲突和超时；
     业务任务通过Phase C创建，Python不得接收浏览器JWT/任意宿主路径/业务写权限。
B-05 张蕊在真实前端核对引用、sourceMode及失败提示；页面未就绪记NOT_VERIFIED。
```

政策适配细节复用 [S4-C契约](../s4/s4c-policy-python-adapter.md)。S1提出的 `POST /internal/document-analysis` 目前是候选服务契约；实际接口路径、认证、材料读取与Java适配仍 `PENDING_CONFIRMATION / BLOCKED`，不能直接据此执行推理。

## 4. Phase C：真实 RabbitMQ + 持久化任务

| 项目 | 内容 |
|---|---|
| 前置条件 | 隔离真实Broker及VHOST/交换机/队列/DLQ由赵书亚确认；隔离库任务表已核对；真实分析依赖就绪才验Python结果 |
| 主责/参加 | 张锦源主责任务/Outbox/幂等；赵书亚主责Broker与观测恢复；王家兴负责分析；张蕊负责进度 |
| 准备数据 | 每场景独立合成材料与合法随机幂等键；申请人R1/R2；容量、超时、有限重试、重复投递夹具；不含真实证件 |
| 请求/操作 | C-01—C-06；使用真实Broker但Mock分析时明确标注组合证据，不称真实Python通过 |
| 预期结果 | 受理事务一致，202不等于Broker确认或预审通过；终态可查；重投无重复业务结果；重试/DLQ有实际证据 |
| 停止条件 | 丢失持久化任务、错误提前202、同key重复记录、无限重试、越权可读、消息含原始材料/凭据/绝对路径 |
| 必存证据 | taskId/eventId/traceId、A/B身份、task/stage/receipt/outbox/capacity数量与状态、confirm/return、消费次数、重试/DLQ、实际SQL节点 |
| 回滚/清理责任 | 赵书亚恢复Broker/队列及流量；张锦源核对任务/Outbox一致性；只在批准清单内处理测试消息/文件，禁止清空共享队列或库 |
| 真实服务 | 需要真实RabbitMQ和MySQL；完整文档分析还需要真实Python及已实现客户端；前端进度需要真实页面 |
| 当前验收 | 真实Broker `NOT_VERIFIED`；完整真实分析链 `BLOCKED`；Broker部署和观测输入 `PENDING_CONFIRMATION` |

```text
C-01 R1经A POST /api/ai/materials：multipart file、businessType=MIGRATION_IN、
     documentRoleHint取接口白名单；Idempotency-Key由本次工具生成并受控保存。
     首次READY且私有对象存在后201；B用同owner/endpoint/key/同文件回放为200同stageMaterialId。
C-02 R1经A POST /api/ai/tasks，JSON：requestSchemaVersion=1.0、
     taskType=DOCUMENT_PRECHECK、businessType=MIGRATION_IN、migrationType=OUTSIDE_CITY、
     stageMaterialIds取C-01响应、idempotencyKey为本次新键。
     HTTP 202/业务code=200须在任务、占用、capacity、Outbox及受理审计事务提交后返回。
C-03 R1经B GET /api/ai/tasks/{本次taskId}；记录PENDING/RUNNING/SUCCEEDED/FAILED，
     终态、poll间隔和结果mode。R2及无查看权限身份查询同ID须被拒绝且无他人内容。
C-04 A/B并发发送同一规范化任务请求和同key；随后换内容重用key。
     前者同任务且无多份Outbox/占用；后者409，不能创建第二份任务。
C-05 经赵书亚确认后在隔离Broker重投同一事件、延迟确认/断连后恢复；
     留存publisher confirm/return、消费者幂等和Outbox重发证据；不由预检发布/消费。
C-06 以受控分析失败触发有限重试及DLQ，核验配置的次数、退避、租约和终态；
     张蕊核对页面只写“已受理/分析完成”，不把202或SUCCEEDED显示为材料核验通过。
```

请求细节、规范化及状态规则复用 [AI预审API](../../api/ai-precheck-api.md)。消息只传契约允许的元数据/任务引用；不手工向共享Broker灌入未确认schema。

## 5. Phase D：人工修正到显式迁入执行

| 项目 | 内容 |
|---|---|
| 前置条件 | Phase C有本次成功任务；合成人员与ACTIVE目标户符合DataScope且无当前户籍/冲突成员；AI开关、确认权限及V4_018事实已核对；至少三类独立业务身份 |
| 主责/参加 | 张锦源主责；张蕊负责页面；L3核验岗/审批岗/执行岗由环境主责提供；王家兴解释分析来源；赵书亚观测数据库/文件 |
| 准备数据 | 任务OCR结果与合成纠正值；必需材料IDENTITY_PROOF和HOUSEHOLD_BOOK/ADDRESS_PROOF；跨区场景另备合法已完成未用迁出批次与MIGRATION_PROOF；每场景独立人/户 |
| 请求/操作 | D-01—D-10；传统入口另用一组合成人员独立执行并汇入相同下游步骤 |
| 预期结果 | 人工显式选候选；确认只建DRAFT并绑定PENDING材料；提交→人工核验→审批→execute分别授权；最终主档与状态原子一致 |
| 停止条件 | AI自动提交/核验/审批/执行、候选越权、实体快照改变仍通过、重复草稿、非本人回执泄漏、审批即生成户籍、任一失败留下部分主档 |
| 必存证据 | task/session的安全关联、申请/审批/材料ID映射、版本、脱敏状态快照、权限身份、实际PRIMARY节点、文件哈希、重复请求及负面请求结果；不记录候选token/JWT原文 |
| 回滚/清理责任 | 张锦源判定业务不变量；赵书亚保全并恢复隔离库/卷；已执行迁入不能靠删行伪造回滚，恢复隔离快照须单独批准；张蕊恢复页面夹具 |
| 真实服务 | 完整端到端需真实前端、MySQL、共享文件、RabbitMQ和Python；使用Mock分析时只证明对应组合链，真实分析仍NOT_VERIFIED |
| 当前验收 | `NOT_VERIFIED`；完整真实链受分析客户端/共享卷依赖 `BLOCKED`；实际测试身份与恢复点 `PENDING_CONFIRMATION` |

```text
D-01 R1经B GET /api/ai/tasks/{本次taskId}，取data.version及SUCCEEDED结果。
     人工核对原材料与合成纠正值；不把模型文本或前端猜测当数据库主键。
D-02 R1经B POST /api/ai/tasks/{taskId}/resolution-preview：requestSchemaVersion=1.0，
     请求taskVersion取D-01的data.version；corrections只用白名单fieldCode/value；迁入类型、目标区划和批次
     与本次夹具一致。取新resolutionToken及person/targetHousehold候选token到受控内存。
     唯一候选也显式选中；NEEDS_CORRECTION或confirmable=false不得继续。
D-03 R1经A POST /api/ai/tasks/{taskId}/confirm：requestSchemaVersion=1.0、
     taskVersion取最新预览响应的data.taskVersion；resolutionToken取该预览，
     selectedCandidates.person/targetHousehold分别取该预览两类候选的candidateToken；
     idempotencyKey为新合法随机键；confirmedFields与预览规范化输入及实际MigrationInRequest
     必填迁入字段一致；acknowledgedAlerts=[]；materialBindings取本任务可绑定stage引用及
     允许标准码，householdEvidenceType按实际HOUSEHOLD_BOOK/ADDRESS_PROOF选择。
     不发送personId/toHouseholdId/applicationId/requiredFlag/核验或执行状态。
D-04 首次201；取applicationId、migrationDetailId、正式materialId映射。
     B查 GET /api/applications/{applicationId}、GET /api/migrations/applications/{applicationId}、
     GET /api/applications/{applicationId}/materials：仅DRAFT，材料均PENDING。
     A/B同key同请求回放200同回执；异内容同key409；同任务换key409；无新增草稿/绑定。
D-05 若缺件，只在DRAFT/RETURNED且有上传权限时POST /api/applications/{applicationId}/materials
     补足本类型必需材料。multipart必须含file、materialType、materialName；
     对按本次规则确定的必需代表材料显式requiredFlag=true（该接口默认false）；
     非必需材料不冒标必需。AI绑定的requiredFlag仍仅由Java计算，不接受confirm客户端指定。
D-06 申请人 POST /api/applications/{applicationId}/submit，无需伪造version请求体。
     确认UNDER_REVIEW；独立审批身份GET /api/approvals/pending并按applicationId关联，
     再GET /api/approvals/{本次approvalId}取data.approval.version。approvalId不是applicationId。
D-07 独立L3核验岗 POST /api/materials/{materialId}/verify，
     JSON result=VERIFIED、comment为合成核验说明；逐项核验必需材料。
     申请人自验/无L3权限核验须拒绝；不能用AI结果替代人工核验。
D-08 独立L3审批岗重新读取审批详情取最新data.approval.version，
     POST /api/approvals/{approvalId}/approve，JSON comment、version取本次详情。
     必需材料未VERIFIED或自审必须拒绝；成功仅APPROVED，不应生成有效residence。
D-09 执行岗重新GET /api/migrations/applications/{applicationId}，取migrationIn.version，
     POST /api/migrations/in/applications/{applicationId}/execute，JSON version取本次响应。
     通过授权查询及主责脱敏DB快照核对当前residence、member、person及两类COMPLETED状态；
     相同旧version再执行409且无第二份户籍/成员/批次使用。
D-10 另取独立人/户夹具POST /api/migrations/in/applications创建传统DRAFT，
     JSON必填personId、migrationType、fromAddress、toRegionCode、toHouseholdId、inDate、reason、title；
     人/户ID取授权合成夹具，IN_CITY_CROSS_DISTRICT另填合法transferBatchNo。
     按D-05—D-09执行相同下游；在Python/MQ受控故障时验证传统入口仍独立可用。
```

确认 JSON 完整字段与规范化细节复用 [AI预审API的S4-B章节](../../api/ai-precheck-api.md)，不得把示例令牌/日期/证件值当本次有效夹具。S4-A会话默认5分钟；过期、再预览撤销、task/实体版本、权限/DataScope变化后必须重新预览。正式材料初始PENDING；APPLICATION_FORM不能改标为MIGRATION_PROOF。

额外权限负面在独立夹具执行：R2读R1任务/暂存/候选/回执；缺确认权限；错类候选；旧会话；越权目标户；非本人确认；无材料权限下载；无执行权限请求。预期依实际接口为401/403、统一404或安全409/422，均不得返回无权内容或产生业务写入。具体错误码记录实际响应，不以一个通用403掩盖接口合同差异。

## 6. Phase E：Redis、主从、Nginx双实例重跑

| 项目 | 内容 |
|---|---|
| 前置条件 | A/B真实JVM、Nginx真实分流、共享Redis完整数据、路由器及共享卷均就绪；主责已确认受控注入窗口、会话隔离和恢复策略 |
| 主责/参加 | 赵书亚主责拓扑/路由/缓存/注入恢复；张锦源主责安全与业务不变量；王家兴/张蕊配合原流程复测 |
| 准备数据 | 独立登录会话及不同JTI（仅留安全别名）；字典管理/普通身份、SELF/DEPARTMENT/REGION/ALL夹具；每个竞争/延迟场景独立业务；物理节点证据采集 |
| 请求/操作 | 6.1九场景、6.2十三类用例和6.3文件/消息；在每次恢复后重新执行Phase C/D，不能用单JVM成功替代 |
| 预期结果 | 跨实例退出安全；所有PRIMARY入口物理落主；仅白名单可落健康且追平从库；权限缓存隔离；文件/任务跨实例一致 |
| 停止条件 | #64错误放行、故障仍虚假登出成功、主写落从、旧状态放行业务、数据丢失未隔离、文件越权、粘滞导致无法证明分流 |
| 必存证据 | 下述每个Test ID独立模板、A/B/Nginx关联日志、SQL/连接节点/复制位点、Redis故障及完整性恢复、文件哈希、幂等计数 |
| 回滚/清理责任 | 赵书亚主责恢复流量、数据库、Redis、Nginx和卷；张锦源复核安全/不变量后共同批准恢复；具体动作与清理对象仍需确认和许可 |
| 真实服务 | 本阶段真实验收必须真实Redis/MySQL主从/Nginx/A+B/共享存储；Mock不得填REAL_ENV PASS |
| 当前验收 | 全部 `NOT_VERIFIED`；路由器/共享卷/双实例拓扑未就绪子项 `BLOCKED`；实际注入/恢复命令 `PENDING_CONFIRMATION` |

### 6.1 S5-A1：九个真实场景

共同前置：两实例都为 `SHARED_REDIS_REQUIRED`，显式生产门禁、共享一致读写端点/database/prefix和JWT_SECRET，不读落后Redis副本；Redis>=6.2、SET/PXAT/EXISTS、校时、noeviction与持久化策略已确认。操作身份是合成账号；登录JSON字段为username/password，凭据现场输入；成功后仅在受控客户端内存读取data.token和data.tokenType（Bearer），用于后续Authorization头，不保存其原文。所有故障由赵书亚按**先确认的测试窗口与命令**执行，本轮不执行。安全事件观察 `REVOCATION_READ_UNAVAILABLE/WRITE_UNAVAILABLE/REJECTED/CONFIRMED`，不保留原始JTI/键/JWT。

#### A1-01：A登录 → B验证会话

```text
1. 受控客户端直达A POST /api/auth/login，凭据由合成账号保管人现场输入。
2. 仅在内存保存响应会话，直达B GET /api/auth/me；不转存Authorization头。
3. 再通过Nginx重复授权请求，用instanceId/traceId证明实际到达两个JVM。
判定：200/200，同一合成身份及当前权限；没有粘滞前提；真实共享Redis读写可确认。
证据：A/B与Nginx关联、安全会话别名、状态码、strict预检及共享端点安全比较结论。
```

#### A1-02：A退出 → B立即拒绝旧JWT

```text
1. 新建独立会话，经B GET /api/auth/me先证200。
2. 同会话直达A POST /api/auth/logout；必须收到共享写确认后200/200。
3. 不等待TTL，立即直达B GET /api/auth/me，并通过Nginx重复该旧会话请求。
判定：旧会话401；B未执行后续业务；另一独立会话仍正常，不发生不同JTI污染。
证据：登出确认时间、B首次拒绝时间、实例/trace、事件与无业务写入的受控检查。
```

#### A1-03：B退出 → A立即拒绝旧JWT

```text
1. 创建另一新会话，经A验证200；直达B POST /api/auth/logout。
2. 退出确认后立即直达A GET /api/auth/me，随后经Nginx验证。
判定：旧会话401；方向反转也成立；不复用已撤销会话来代替新测试。
证据：同A1-02，明确B写/A读及实际JVM，不只标逻辑A/B。
```

#### A1-04：Redis停止 → 两实例受保护请求503

```text
1. 准备未过期独立会话和故障前已确认撤销会话，保存安全别名及预期状态。
2. 赵书亚确认持久化/备份与恢复点后，在隔离环境停止/隔离Redis。
3. 分别向A、B GET /api/auth/me；另以授权的合成写请求验证Filter前置拒绝。
判定：有效JWT受保护请求503，业务code=503且含REVOCATION_STORE_UNAVAILABLE；
     无业务写入、不伪造登录/退出成功。/api/health的200不影响此判定。
停止/恢复：任何放行立即隔离；不把REDIS_ENABLED=false或本地模式当修复。
```

#### A1-05：恢复完整撤销数据 → 正常校验

```text
1. 赵书亚保持认证流量隔离，恢复原完整撤销数据并核验持久化/复制与恢复点。
2. 张锦源分别经A/B测试故障前已撤销会话仍401、未撤销有效会话恢复200、
   新登录/登出正常；核验PXAT真实到期及5秒probe淘汰，记录时钟误差。
3. 只有双方核对数据完整性和安全会话结果后，才按确认的方案恢复测试流量。
判定：恢复不能仅凭PING或KEY_ABSENT；故障前后的撤销均保持有效。
若完整性未知：记录FAIL/BLOCKED，按6.4旧JWT作废流程处理，不宣布strict自动恢复。
```

#### A1-06：Redis写入失败 → 不能报告退出成功

```text
1. 赵书亚仅在隔离命名空间/ACL中安排“允许EXISTS读取但拒绝撤销SET”的受控故障；
   先准备有效会话，确保故障不被读取失败提前遮蔽。
2. 分别向A/B POST /api/auth/logout；观察共享写确认和审计。
判定：503/REVOCATION_STORE_UNAVAILABLE，不能有LOGOUT SUCCESS；
     不以客户端清除会话或本地Map为退出成功。写结果不明也不能返回成功。
恢复：赵书亚恢复ACL/容量并核验命令权限；重新检查原会话实际状态，不能猜测是否写成。
```

#### A1-07：Redis认证失败/连接超时 → fail-closed

```text
1. 赵书亚按已确认的独立测试配置分别安排错误认证、连接拒绝和命令超时；
   每次只改变一个因素，记录实际超时设置，不输出错误凭据。
2. A/B分别GET /api/auth/me及进行受控退出；另测正确凭据登录。
判定：无法确认共享状态时503；退出无成功审计；业务无写入；在配置超时范围内结束。
恢复：主责恢复正确配置/网络，按A1-05验证，不能仅以连接恢复宣告安全。
```

#### A1-08：登录预检失败 → 不签发成功会话

```text
1. 使用正确、启用的合成账号，主责分别使probe写失败、读不到或查询超时。
2. A/B分别POST /api/auth/login；由受控DB/审计观察last_login及LOGIN_SUCCESS。
3. 独立对照错误密码、停用账号/角色，确保不会触发probe或掩盖原401/403语义。
判定：正确凭据但probe故障时503，无成功会话、无last_login更新/LOGIN_SUCCESS；
     probe不含JWT、独立命名空间且绝对TTL约5秒。不要把预检成功当历史完整性证明。
恢复：赵书亚恢复共享Redis，张锦源核对新登录与probe到期。
```

#### A1-09：单实例下线 → 另一实例接管

```text
1. 先记录Nginx分流到A/B及一项已授权合成业务正常基线。
2. 赵书亚仅下线事先确认的A实例；经Nginx发送有限请求，记录失败窗口及B处理证据。
3. 验证未撤销会话、已撤销会话、任务查询和有权文件访问；恢复A后再观察分流。
4. 使用新夹具反向验证B下线/A接管（不同时停两实例）。
判定：按确认的故障窗口恢复正常，撤销/权限/文件语义不漂移；不要求零丢包而编造结果。
证据：准确进程/实例、时间窗、请求错误数、Nginx日志、恢复后状态。
```

### 6.2 S5-A2：39行矩阵复用与十三类物理验证

**完整沿用** [S5-A2第2节的39行路由矩阵](s5a2-transaction-cache-boundaries.md#2-业务事务与-mapper-路由矩阵)，不在此另写一套矩阵。执行时按原顺序登记 `ROUTE-01`—`ROUTE-39`，逐行引用原业务标题、Mapper/JDBC范围、路由建议和延迟/失败规则；每行至少一条真实物理节点证据。没有实现或观测条件的行记BLOCKED，不得用十三类抽样替代其余行。

每次物理记录至少含：runId/Test ID、原矩阵标题、instanceId/traceId、事务active/readOnly/传播、Mapper或SQL类别、连接标识、实际 `server_id`/`read_only`/节点别名、主从位点及延迟、路由决定、提交/回滚时刻。须关联到**业务实际使用的同一连接**，不能另开主库连接执行身份查询后冒充业务路由。由赵书亚提供安全观测实现，当前为 `PENDING_CONFIRMATION`；没有观测即BLOCKED。

路由优先级和主库清单复用 [S5-A2第4节](s5a2-transaction-cache-boundaries.md#4-给路由实现方的主库清单与从库白名单)：RW、锁、鉴权/状态机、提交/审批/执行、事务外幂等与写后即时读必须PRIMARY；`readOnly=true`、SELECT或“连接可用”不自动授权走从。副本断连时须整次只读操作回主或明确失败，不能在失败事务中任意换连接。

```text
A2-01 PRIMARY写事务：分别经A/B创建/修改独立传统迁入草稿，记录每个实际Mapper/连接节点；
      预期读写及写后返回均PRIMARY，同一事务无从库SQL；主库失败明确失败且无部分写入。
A2-02 REPLICA_ELIGIBLE统计：以有statistics:view身份GET /api/statistics/overview，
      先安排可观测缓存miss（不能用FLUSH），记录授权读取走主、允许统计SQL可走已追平副本；
      命中缓存没有SQL时如实记录，不能把无SQL推断成副本命中。
A2-03 写后即时读取：A创建/更新申请或材料成功后立即经B读取详情/材料清单，
      即使副本未追平也看到主库当前状态；记录提交时间与B实际PRIMARY连接。
A2-04 SELECT FOR UPDATE：通过本次迁入执行/成员锁查询触发真实锁SQL，
      包含不以lock命名的SQL；并发观察等待/冲突，物理节点必须PRIMARY。
A2-05 提交/审批/执行：完整执行D-06—D-09并记录每一步实际SQL节点；
      前置状态/材料/版本读取和写均PRIMARY，审批后未execute不产生有效户籍。
A2-06 事务外幂等：A上传/受理，B立即同owner/endpoint/key回放及A/B并发；
      记录template外首次查与唯一冲突后重查物理节点为PRIMARY，数量与回执唯一。
A2-07 副本延迟：赵书亚受控暂停/延迟复制，A写后B立即查/提交/审批/执行；
      强一致路径仍见主库新状态，候选展示按确认预算陈旧；保存位点/延迟/恢复追平证据。
A2-08 副本断连：切断副本后发独立统计/展示，再恢复连接但暂不追平；
      安全整次回主或明确失败；追平/健康确认前不重新路由副本；记录全过程连接身份。
A2-09 字典权限隔离：管理岗A预热含禁用项的GET /api/dictionaries/{本次类型}，
      普通岗B查询，再反向预热，随后用同用户权限降级/恢复夹具重复；
      MANAGE/ENABLED_ONLY分区、每次实时权限正确，不读旧共享key，不泄露禁用项。
A2-10 统计DataScope：SELF/DEPARTMENT/REGION/ALL及不同区划分别跨A/B预热overview；
      同用户权限/部门/有效子区划变化后重复，结果符合实时范围；保存脱敏key结构，
      不删除必要user/department/regionCodes/参数维度，不把有缓存当授权。
A2-11 普通缓存故障回源：在共享撤销仍健康、会话仍可授权的前提下，
      受控使普通缓存GET/SET/失效失败，调用字典/区划/统计，观察按当前DataScope回源；
      全Redis停机时strict Filter先503，不能据此声称已动态覆盖普通缓存回源。
A2-12 字典旧值回填：在经批准的观测夹具中闩锁A旧读取；B提交字典更新并失效；
      再释放A回填，观察B后续展示和服务端TTL；分别测提交/回滚及删除失败。
      没有安全可控闩锁则BLOCKED。记录残留上限/复制延迟，不能声称已解决此竞争。
A2-13 KEYS(pattern)规模：在隔离Redis由赵书亚准备确认规模的合成键，
      通过真实参考数据写入触发既有KEYS+DEL，观察实际慢操作/P50/P95/吞吐/错误率，
      与同数据/并发/预热条件缓存开关基线比较；不能运行共享FLUSH或编造提升百分比。
```

缓存边界复用 [S5-A2第5节](s5a2-transaction-cache-boundaries.md#5-redis-使用清单参考缓存机制及限制)：活动RW参考查询不读写缓存；提交后失效、回滚不失效。字典/区划默认TTL30分钟、统计5分钟；旧值回填/删除失败及复制延迟需动态观测，统计目前仅TTL而无业务写入主动失效。TTL残留是已知限制，不是测到旧值就伪称“强一致通过”。

### 6.3 文件共享、消息跨实例与权限负面

以下均需真实A/B及共享卷。记录上传/下载SHA-256、对象安全别名、持久化行数及实例，不记录宿主路径、对象键、文件内容或JWT。读取身份既包含“同一账号在B访问”，也包含“另一个无权账号在B访问”，不能把实例B误当成用户B。

```text
FS-01 普通正式材料：R1在A对DRAFT POST /api/applications/{id}/materials；
      multipart使用file、materialType、materialName，requiredFlag按D-05的本次规则确定；
      同身份在B GET清单及 GET /api/materials/{materialId}/download，比对哈希与元数据；
      无权限/范围外身份请求必须被拒绝且无内容，无Token为401。
FS-02 预审暂存：R1经A POST /api/ai/materials后，经B GET /api/ai/materials/{stageId}；
      再在B创建任务/预览并在A或B确认。当前GET只是元数据，不宣称存在暂存下载入口；
      Python读取方式须双方确认；R2查同stage/task不应得到他人材料。
FS-03 AI绑定：A完成confirm后，B查正式材料清单并经正式材料download读取；
      文件哈希一致、verifyStatus=PENDING；在经确认的独立运行配置中关闭AI预审开关后，
      已绑定正式材料仍应可按权限下载；BOUND不被暂存清理误删。
FS-04 导出：有data:export:normal身份在A POST /api/exports/normal，
      JSON module=PERSON，filters.regionCode取当前授权合成区划，
      fields=[name,maskedIdentityNo,gender,regionCode]；取返回exportLogId，
      在B GET /api/exports/{exportLogId}/download。
      核对权限、范围、文件哈希/行数；另一个无权身份拒绝，不使用敏感导出绕过审批。
FS-05 身份证影印本：有权限R1在A POST /api/persons/idcard-image，multipart合成图片，
      skipOcr=true仅验证存储/元数据；同身份在B GET /api/persons/idcard-image/{imageId}。
      非SYSTEM_ADMIN的另一普通账号读取未绑定草稿须拒绝；已绑定时另按人员DataScope验证。
      此GET是元数据，不是文件下载API；二进制跨实例可读性由主责受控文件/hash观测证明，
      若无安全读取/观测契约则BLOCKED。真实OCR另验，不能因skipOcr核销#3。
FS-06 任务状态：A创建任务，B立即GET /api/ai/tasks/{taskId}并轮询；
      跨实例状态/终态一致；R2/缺查看权限请求拒绝，无分析内容泄漏。
FS-07 A/B并发幂等：分别对材料上传、任务创建、confirm同key同请求并发，
      观察材料/任务/receipt/outbox/binding/capacity数量及唯一结果；异请求同key409；
      不用从库漏读导致的冲突/重复成功冒充幂等通过。
FS-08 Outbox/DLQ：经批准在隔离Broker重复投递/断连恢复/耗尽有限重试，
      A/B不同消费者处理时仍无重复结果；DLQ、确认/重发和任务终态相互可关联。
FS-09 文件故障与负面：主责受控撤去测试对象可用性或只读权限，不删除共享业务文件；
      A/B读写应安全失败且无他人内容/绝对路径泄漏，任务/绑定不能虚假成功。
      恢复卷/权限后按同一授权重新验证；不共享或仅同JVM成功一律不能填PASS。
```

正常导出请求字段复用 [数据导出与审计API](../../api/export-audit-api.md)。身份证影印本读取的所有权、已绑定人员范围及返回字段以 [PersonIdCardImageService](../../../src/main/java/com/wjx871/population/person/idcard/PersonIdCardImageService.java)为准；不得自行添加静态文件路由来“完成”验证。

### 6.4 Redis数据丢失与#64结项边界

strict只保证共享数据完整、读写可确认时的撤销传播。重启/切换/回滚、旧备份恢复、驱逐、FLUSH、持久化缺失可能丢失撤销历史；`KEY_ABSENT`或PING成功不会检测、补回这些历史。当前没有全局epoch或数据库撤销历史，不能自动恢复安全。

```text
数据完整性未知时：
1. 赵书亚隔离认证流量，保全故障前后恢复点、持久化/复制证据；禁止直接清空撤销空间。
2. 张锦源与赵书亚确认所有旧JWT的最长剩余生命周期及受影响实例。
3. 由负责人提出旧JWT作废方案并取得必要许可：例如协调全部实例统一轮换JWT_SECRET，
   或隔离流量直至全部旧JWT最长生命周期结束；方案需含范围、维护窗口、恢复/回退条件。
4. 逐实例验证旧会话不可用、新登录/撤销及RBAC正常后，共同确认重新开放。
5. 记录实际过程；任何方案未确定或未验证均BLOCKED，不把网络恢复记为安全恢复。
```

只有九场景及所需真实双JVM、共享Redis、Nginx分流、故障恢复/数据完整性与旧JWT策略证据齐备，独立复核后才可**提议**关闭#64。本轮#64保持OPEN；#3、#33保持OPEN，#21保持FIXED_VERIFIED。本文件不自动修改审计状态。

## 7. Phase F：故障注入、恢复、压测与最终演示

| 项目 | 内容 |
|---|---|
| 前置条件 | Phase A—E适用项真实完成且缺陷已复核；环境主责确认窗口、允许故障、恢复点、负载、终止阈值与清理范围；PENDING项不得默认为同意 |
| 主责/参加 | 赵书亚主责注入、资源、压测和恢复；张锦源判定业务一致性；王家兴恢复推理服务；张蕊观察UI/JavaFX |
| 准备数据 | 新独立合成业务/材料；固定数据量、并发、预热、持续时长；服务版本；故障前状态快照；经确认采集方案 |
| 请求/操作 | F-01—F-06，按“一次一个变量→证据→恢复验证→下一个场景”执行，注入具体命令仍PENDING_CONFIRMATION |
| 预期结果 | 故障被发现、可定位、可恢复；恢复后传统与AI入口同回归；P50/P95、吞吐、错误率及资源来自真实测量 |
| 停止条件 | 超出预定资源/错误阈值、恢复点不可用、真实数据混入、任一安全/业务不变量失效、服务归属不明、无负责人可恢复 |
| 必存证据 | 注入/检测/隔离/恢复完整时间线、原始测量、配置、trace/task/event关联、恢复后业务/授权/文件回归、演示截图、缺陷台账 |
| 回滚/清理责任 | 赵书亚负责基础设施恢复；王家兴负责Python；张蕊负责页面；张锦源共同复核数据安全；清理先清单/影响/恢复点/用户许可，不能自动删除中间证据 |
| 真实服务 | 需要实际拓扑；仅本地Compose须如实标本地，不能称已上云；缺少真实采集或ops API的看板子项BLOCKED |
| 当前验收 | `NOT_VERIFIED`；具体窗口、负载阈值、观测接口、注入与恢复步骤 `PENDING_CONFIRMATION`；本轮未实施任何故障/压测 |

```text
F-01 固定环境版本/数据量/并发/预热/时长，记录空载与正常基线；阈值由主责先确认。
F-02 按Phase E场景逐项验证单实例故障、Redis故障；再受控验证从库延迟/断连、
     RabbitMQ积压/重连、Python超时/中断及文件不可用。每场景单独恢复，不能叠加掩盖原因。
F-03 关联异常指标→实例日志/trace/task/event→可能原因→检查→恢复；
     真实ops聚合接口未实现时标BLOCKED，不能用健康200或静态AI诊断文字补证据。
F-04 相同条件比较缓存开关/路由/双实例，保存原始P50/P95、吞吐、错误率、CPU/内存、
     连接池、复制延迟、队列积压、KEYS操作耗时；不写无测量的提升百分比。
F-05 每次恢复后重复传统DRAFT到execute和AI显式确认到execute，跨实例权限/文件/撤销重验；
     演示清晰区分AI分析、PENDING材料、DRAFT、人工审批和显式执行。
F-06 汇总证据模板、39行矩阵、缺陷修复回归与未验证项；列拟清理对象和恢复影响，
     仅在用户明确许可后由对应负责人清理。保留必要原始证据与本施工worktree。
```

## 8. 交接与退出条件

```text
给环境与服务负责人的执行说明（候选，未发送）：
请按环境契约逐项补充确认人/时间/安全证据，并确认隔离库与合成数据、A/B JVM身份、
Redis完整性/旧JWT作废策略、主从物理节点观测、共享卷、Python真实接口、故障窗口与恢复责任。
Document Analysis HTTP客户端、主从路由器、共享卷/双实例拓扑等BLOCKED项就绪并复核前，
请勿将本文作为自动部署脚本。实际字段/接口未确认仍为PENDING_CONFIRMATION。
真实执行按Phase A→F，逐项填证据，未执行不填PASS；停止/恢复/清理必须保留责任人。
```

S5-B0交付状态为CODE_CANDIDATE；真实Redis/Nginx双实例、MySQL主从、RabbitMQ、Python、前端均NOT_VERIFIED。本文完成后停止，不自动进入S5-B1、部署、故障注入或S6。
