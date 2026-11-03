> S1 REVIEW DRAFT<br>
> 尚未完成全组及教师确认<br>
> 不得视为最终冻结接口

# 架构决策、对接事项与 S1 冻结检查

## 使用方式

本文件记录 S1 七项决策的后端建议。四位成员在《邑循项目详细分工.md》中已有明确主责，本稿直接采用该分工；对具体共享 Java 文件、消息字段、状态及真实服务传输方式，路线图仍留有待决空间，本稿给出后端可执行基线。外部服务未就绪时，后端可以使用 Mock 和兼容适配继续 S2/S3，不以全员逐项签字作为开工条件。

本文所有新增接口、字段和实现选择均标为 PROPOSED。课程文件提到的教师验收形式仍未知，记录为 EXTERNAL_PENDING_CONFIRMATION 并采用更保守的实现建议。成员实际服务接入时再协调版本或映射差异。

## 七项架构决策

### D1. 个人长期集成分支策略

**状态：PROPOSED**

**背景：** v1.1 路线图要求永久 snapshot/20261103-pre-course-upgrade-backend 保持固定，并决定后续继续使用 integration/20261101-backend-p0-security，还是另建长期课程集成分支。

**当前事实：** fetch 后 origin/integration/20261101-backend-p0-security 与 origin/snapshot/20261103-pre-course-upgrade-backend 均为 d6b0b87b74af7a413214d7da9dd30b075cec882b；个人集成 worktree 干净。主工作区停在 main 且有本地 .gitignore 修改，本轮没有切换或修改它。

| 方案 | 优点 | 代价 |
|---|---|---|
| A. 继续 integration/20261101-backend-p0-security 作为个人长期集成分支 | S0 后端验收基线正是此分支；无需增加同一基线的第二条长期分支；snapshot 继续只读。 | 需要严格区分个人集成与共享 main，持续检查集成分支来源和状态。 |
| B. 从 d6b0b87 新建课程长期集成分支 | 课程改造历史与 S0 集成工作分开。 | 增加长期分支和后续整合路径；在当前基线完全相同的情况下暂未带来代码隔离收益。 |

**推荐：** 采用 A。保留当前个人集成分支作为后续个人长期集成基线；每项阶段施工另建 codex/ 或 docs/ 分支，从当时核实的最新个人集成 SHA 派生，审查后再整合。永久 snapshot 不移动，main 不作课程施工分支。

**本轮边界：** 本建议不创建或切换长期集成分支。本轮只使用 docs/20261103-s1-backend-contract-design 施工分支；它不是长期集成分支。

**决策责任人：** 用户/后端负责人。无需其他成员确认。<br>
**影响：** S2/S3 的阶段分支创建与合并路径；本轮的 S1 文档施工已按独立分支完成。<br>
**未决项：** 用户是否采纳“继续使用现有 integration 分支”为长期策略。

### D2. 教师对 Web Service、RMI、JavaFX、Redis/MemCached 的验收口径

**状态：EXTERNAL_PENDING_CONFIRMATION**

**背景：** 《专业工程实训 4-3(1).pptx》第 4 页列 Java RMI、反射/代理、消息队列、Web Service、Docker；第 5—6 页列 MemCached、MySQL 读写分离和 Nginx；第 7 页列 JavaFX、JFreeChart/ECharts 与性能优化；第 8 页列日志分析。课件没有说明 SOAP/WSDL、独立进程粒度、JavaFX 最小交付或 Redis 是否可替代 MemCached。

**当前事实：** 源码有 REST API、Redis 可选缓存及 Vue/ECharts 页面；未发现 RMI、SOAP/WSDL、JavaFX、RabbitMQ 或 Redis 主从/读写路由实现。路线图和分工文件均保留教师口径确认事项。

| 主题 | 保守后端建议 | 未确认时的课程风险 |
|---|---|---|
| Web Service | 保留 REST 前端 API；另提供最小 SOAP/WSDL 服务，复用非敏感统计逻辑并给出独立客户端。 | 如果教师接受 REST，SOAP 可作为额外兼容演示；如果不做 SOAP 而教师强制 SOAP/WSDL，会导致该项未验收。 |
| RMI | 两个独立 JVM 进程间远程调用；包含 DTO、服务描述和远端不可用场景。 | 在同一进程中模拟调用可能不符合课件中的远程对象访问目标。 |
| JavaFX | 按分工保留轻量运维看板：读取 Java 聚合接口，显示服务健康、延迟或任务概览，能处理服务失败。 | 若取消 JavaFX 而教师要求可视化框架展示，会有缺项风险。 |
| 缓存 | 复用现有 Redis；实现命中、失效、故障回源和性能对比。若教师点名 MemCached，再做小范围适配。 | 当前课件写“推荐工具：MemCached”，不代表教师已经允许 Redis 替代。 |

**推荐：** 在教师答复前，S2 以 SOAP/WSDL 的最小实现和独立 JVM RMI 作为保守施工方案；JavaFX 保留最小看板落点；Redis 作为已存在缓存基线，同时不宣称课件口径已确认。

**决策责任人：** 指导教师/课程要求负责人答复；张锦源、张蕊分别承接 Java 服务和 JavaFX。<br>
**影响：** S2 Web Service/RMI 设计；S5/S6 JavaFX、缓存和演示安排。保守施工可以先开工，答复后再裁剪或补齐。<br>
**未决项：** SOAP/WSDL、RMI 展示、JavaFX 最低要求、Redis/MemCached 与其他强制技术点。

### D3. 预审材料暂存、材料绑定及标准编码映射

**状态：PROPOSED（后端基线）**

**背景：** 新交互需要正式申请创建前上传材料，但当前 application_material.application_id 为非空外键，MaterialService.upload 必须先找到申请并确认处于 DRAFT/RETURNED。

**当前事实：** V4_002 建立正式材料表和外键。LocalFileStorageService 允许 PDF/JPEG/PNG、生成随机文件名并计算 SHA；上传写库失败时尝试删除文件。已有 person_idcard_image 草稿表只面向身份证影印本，并按上传用户隔离，不能承载户口簿、申请表等通用预审材料。

| 方案 | 优点 | 代价 |
|---|---|---|
| A. 独立预审材料元数据和私有文件引用 | 符合先上传后建申请；所有权、过期、任务占用可单独表达；确认时能在同一 DB 事务建立正式附件并标记 BOUND。 | 后续需要增量迁移和过期清理；文件系统补偿须单独设计。 |
| B. AI 流程开始时先创建正式 DRAFT，再复用当前材料服务 | 复用现有表、权限和文件服务，不加暂存表。 | 申请在用户确认前就存在，废弃草稿增多，改变“先传材料、后建正式申请”语义。 |
| C. 复用 person_idcard_image | 已有草稿 UUID 和创建者字段。 | 业务边界、字段和 OCR 状态仅适合身份证影印本，无法表达多种材料和申请生命周期。 |

**推荐后端基线：** 方案 A。后端 Mock 的暂存对象使用随机不透明 stageMaterialId、ownerUserId、文件元数据、内部对象引用、expiresAt 和 READY/IN_USE/BOUND/CANCELLED/EXPIRED 等暂存状态。RabbitMQ 事件只含 taskId 与 eventId、taskType、schemaVersion、traceId、occurredAt 等元数据，不含 stageMaterialId、内容、绝对路径或凭据；消费者由 taskId 查询持久化任务并取得其关联材料引用。Python 通过 Java 受控文件流读取，或经后续部署对接只读卷兼容适配。

首选在共享私有存储中保持文件对象引用不变；创建 DRAFT 时同一 MySQL 事务写 business_application、migration_in、application_material 和绑定状态。若后续环境要求复制文件，改为绑定事件加可重放补偿，不能将文件系统和数据库说成同一原子事务。

**标准编码建议：**

- 身份证正反面 → IDENTITY_PROOF。
- 户口簿页 → HOUSEHOLD_BOOK。
- 地址证明 → ADDRESS_PROOF。
- 同市跨区迁移证明 → MIGRATION_PROOF。
- 迁出同意材料 → HOUSEHOLD_CONSENT（现有码值）。
- 申请表 → APPLICATION_FORM 候选；当前迁入规则不要求。绑定是否采用此新增码，后续与 AI/前端材料清单对接；不得误映射成 MIGRATION_PROOF。
- 客户端 materialType 仅是用户的分类/修正建议；Java 校验材料归属和标准码白名单，并按 businessType、migrationType、授权分类及现行迁入规则计算 requiredFlag。不能信任客户端或 AI 的 requiredFlag；正式材料初始核验状态固定 PENDING。

**责任：** 按分工，张锦源负责业务暂存/绑定和 Java 授权；赵书亚负责共享文件卷与保留策略；王家兴提供 AI 文件引用读取适配；张蕊提供上传/进度/预览交互。

**影响：** S3 可用随机 stageMaterialId 和 mock document-analysis worker 验证消息；S4 接入正式材料预上传、人工确认及绑定事务。<br>
**未决项：** 文件卷路径/服务流、部署保留规则和 APPLICATION_FORM 是否作为新正式码。后端默认建议 READY 材料保留 24 小时、任务结束后最多 24 小时供确认、结果保留 7 日；在实际部署时按数据保留要求核实。这些环境/跨组对接项不阻止后端先按方案 A 设计和开发可替换适配层。

### D4. OCR 实体解析与异常交互规则

**状态：PROPOSED（后端基线）**

**背景：** AI 抽取的是姓名、证件号、户号、地址等文本；现有 MigrationInRequest 需要 personId、toHouseholdId、toRegionCode、migrationType 和跨区 transferBatchNo 等可信数据库/业务值。模型文本不能成为主键。

**当前事实：** MigrationService.createIn 检查人员存在、人员没有当前户籍、目标家庭户 ACTIVE 和迁入类型；跨区批次要求在执行时核对已完成的迁出批次。审计账本 #21 标记迁入区划与目标家庭户区划未比较。当前 createIn 的人员读取使用 selectById，目标户使用锁定查询，但该方法本身没有显式 DataScope 查询参数。

**候选方案：**

- 只用模型值填充 personId/toHouseholdId：不可接受，存在主键猜测和权限越界风险。
- Java 按当前权限范围精确查询，再由用户确认授权候选：能够保持 Java 为主数据唯一裁定方，并支持无匹配、多候选、冲突和数据变化处理。

**推荐后端基线：** 对证件号精确查人员并检查姓名一致；按授权范围精确查 ACTIVE 目标户；地址仅提供提示，不作为唯一键。resolution-preview 发会话级 resolutionToken 和按实体类型区分的 candidateToken；前者绑定 userId、taskId、taskVersion、DataScope 指纹、修正字段摘要、候选集和有效期，后者绑定父 resolutionToken、实体类型、实体版本和服务端主键（主键不在 token 明文）。confirm 必须显式提交 person 与 targetHousehold 的各一个 candidateToken，不接受 personId/toHouseholdId。唯一候选也必须由用户显式选中；缺选、多选、错类、过期或不属于当前用户/task/scope 的令牌均拒绝。Java 在事务写入时重查并锁定人员、目标户、批次与当前户籍；数据变化返回 409 并要求重新预览。人户区划冲突返回 409；越权或范围外资源返回统一 404；人员不存在时引导先走现有建档业务，AI 不自动建人/户。

若目标户不存在、非 ACTIVE、人员已有有效户籍或有效成员关系、跨区批次不匹配/已用、DataScope 不允许或预览后数据变化，则拒绝创建草稿并返回字段级安全提示。只有经授权人员才能看到候选；错误响应不得暴露无权资料。

**责任：** 张锦源负责可信解析、DataScope、状态和幂等；王家兴提供字段、来源和置信度；张蕊提供用户修正和授权候选 UI。<br>
**影响：** 后端可用 mock OCR 输出和测试库记录开始设计 S4 确认请求；S2/S3 不依赖真实 AI OCR。<br>
**未决项：** 实际前端候选展示、特殊模拟数据的证件号规则和字段更正体验在对接时适配；不会改变“Java 查主键、用户显式确认”边界。

### D5. 两个 Python 内部接口与降级 Schema

**状态：PROPOSED（后端基线）**

**背景：** 目标增加文档理解和混合检索，但当前 Java 的本地检索与可选外部大模型不等同于 Python RAG；任务消费者需要可测量、可替换的内部调用协议。

**当前事实：** PolicyAssistantService 从本地 Markdown 知识片段检索，之后可选调用外部 Chat Completions；PolicyWorkflowService 生成只读清单，PolicyTaskAgentService 用权限白名单构造 nextActions。当前无 Python 内部 URL、服务凭据、引用版本 schema 或文档分析任务 API。

**候选方案：** 直接把 Python 主机地址、模型文本和文件路径暴露给浏览器；或由 Java 作为单一业务网关，使用内部认证和 versioned JSON 与 Python 通信。推荐后者。

**推荐后端基线：**

- POST /internal/document-analysis：使用 taskId/requestId/traceId、schemaVersion=1.0、businessType/migrationType、Java 推导的结构化 materialRequirements（allOf/anyOf）和任务持久化记录中的不透明材料引用；返回材料分类、字段值/空值状态、来源材料/page/boundingBox、置信度、缺件/冲突告警、模型/规则版本及耗时。逻辑规则名不是 materialType。
- POST /internal/policy-query：含问题、有限历史、业务类型/区划；返回 answer、citations、来源版本、生效期、knowledgeBaseVersion、模型/检索版本、evidenceLevel、elapsedMs。
- 内部认证采用 mTLS 或短时服务身份，Python 仅有分析权限；浏览器不直连 Python，不传用户 JWT/DB 主键。
- 当前 PolicyAssistantController.query → PolicyTaskAgentService.execute 会调用 PolicyAssistantService.query、融合 citations、计算 evidenceStatus、过滤 nextActions，并由 buildSummary 构造 answerSummary；它不会自动将 QueryResponse.answer 复制到 AgentResponse。Python answer 必须由适配后的 QueryResponse.answer 明确进入 PolicyTaskAgentService，并经 citation 校验后成为 answerSummary 主体。
- 新增 sourceMode 明确区分 PYTHON_HYBRID_RAG、JAVA_LOCAL_FALLBACK、JAVA_LEGACY_AI_ASSISTED、JAVA_SENSITIVE_GUARD 和 SERVICE_ERROR。mode/evidenceStatus 保留现有语义；SAFE_REFUSAL 只用于敏感输入拒答，普通无依据保持 RETRIEVAL_ONLY + INSUFFICIENT。
- Python citation 的 sourceId 映射到 Java Citation.logicalPath，category 保留供 matchesIntent 使用，sourceVersion 映射 version、excerpt 映射 summary；Java 融合后重新编号并验证 citationIdsUsed。有效 Python answer 写入现有 QueryResponse.answer，再由 PolicyTaskAgentService 明确设置 answerSummary；Java 的 conditions、steps、warnings、状态说明和权限过滤 nextActions 保持原有职责。
- Java 校验 Python evidenceLevel 与最终融合 citation 集，再映射到 AgentResponse.evidenceStatus；引用不支持或不足时降级为 PARTIAL/INSUFFICIENT，不把模型置信度当作答案正确率。
- Python 或 Java 检索无证据时，mode=RETRIEVAL_ONLY、evidenceStatus=INSUFFICIENT 并使用 Java 无依据文案；SAFE_REFUSAL 仅用于现有敏感输入规则。Python 异常先回退纯 Java 本地检索；本地检索服务也异常时用新增 SERVICE_ERROR mode/sourceMode，不伪装成拒答。
- 建议 policy connect/total timeout 2s/8s；document-analysis connect/read timeout 2s/120s，任务 worker 有限重试。timeout 值是后端默认起点，可在真实 Python 性能联调时调整，不阻止用 Mock 开发。

**责任：** 王家兴负责 Python 服务及检索质量；张锦源负责 Java API 编排、服务身份边界、结果持久化和 fallback；张蕊消费稳定的 Java API；赵书亚提供容器网络和部署参数。

**影响：** S2/S3 可实现 Java interface/adapter 和确定性 Mock；S4 再接 Python 实际地址、模型和知识库。<br>
**未决项：** AI 服务真实 URL、认证部署、吞吐、版本命名和超时实测结果在实际联调时对齐，不要求本轮获得签字。

### D6. 跨组共改 Java 代码的责任划分

**状态：PROPOSED（实现规则；能力主责已依据分工文件确定）**

**背景：** 分工文件已确定张锦源（Java/业务）、赵书亚（数据和运维）、张蕊（前端/JavaFX）、王家兴（Python AI）。路线图另把读写路由、Redis 代码扩展、Actuator/Micrometer 和受保护运维 Controller 的 Java 文件主笔/复核列为待决。

**当前事实：** pom.xml 包含 Spring Web/Security、MyBatis、MySQL、Redis、Log4j2；不含 RabbitMQ starter、Actuator/Micrometer。当前 SecurityConfig 为 /api/** 设置认证，其他路由默认 permitAll。application.properties 使用单一 DataSource。Redis 默认关闭；TokenRevocationService 同时使用 Redis 和单实例本地 Map。

**候选方案：** 所有 Java 文件都由张锦源独自修改，形成瓶颈；或根据能力主责安排主笔，涉及共享配置、安全和数据结构时由张锦源与对应主责人双重核对。推荐后者。

| 修改面 | 起草/代码主责建议 | 双人复核和接口协作 |
|---|---|---|
| RabbitMQ 任务服务、状态、Outbox/consumer | 张锦源 | 赵书亚复核 Broker/配置；王家兴提供 Python 调用适配；张蕊对接 API。 |
| RabbitMQ 部署、持久化、DLQ、监控 | 赵书亚 | 张锦源复核消息业务语义；不改业务状态枚举。 |
| MySQL 复制和读写路由基础设施 | 赵书亚起草 | 张锦源共同核对事务、Mapper 和写后读取；必须标明强一致业务走主库。 |
| Redis 缓存和运维配置 | 赵书亚起草 | 张锦源核对 DataScope 缓存键、令牌撤销跨实例行为。 |
| Python 客户端和内部认证 | 张锦源维护 Java 侧 | 王家兴核对 Python 字段、服务认证和错误兼容；部署参数由赵书亚提供。 |
| Actuator/Micrometer 指标采集 | 赵书亚起草 | 张锦源核对 Java 请求/任务关联和安全暴露；张蕊消费字段。 |
| 受保护聚合 Controller | 张锦源维护权限入口；赵书亚维护聚合指标服务 | 二人双审；张蕊核对看板展示。 |
| pom.xml、SecurityConfig、DataSource 配置、application.properties、迁移 SQL | 对应能力主责提交单一改动集 | 张锦源 + 对应能力主责双审；迁移/数据库环境由赵书亚共同核验。 |

建议避免共享文件并行编辑；先提交小范围分支，reviewer 对照接口契约检查后再合入个人集成。与主责一致的独立文件可先做 Mock 实现。该分工允许 S2/S3 并行准备，不代表本轮改动了任何配置或代码。

**影响：** S2/S3 共改依赖审阅规则；S5 数据路由/指标依赖该责任表；不阻止成员按既定能力主责起草各自隔离模块。<br>
**未决项：** 具体分支/PR 流程在 S2/S3 进入共享集成时决定；本轮不创建 PR。

### D7. 现有同步身份证 OCR 长事务问题的后续定位

**状态：DEFERRED**

**背景：** 现有身份影印本上传方法在一个 Spring 事务内保存文件、写草稿记录、远程 OCR、写识别结果并记录审计。课程路线图要求独立评估，不得把新异步预审当成旧问题已解决。

**当前事实：** PersonIdCardImageService.uploadAndRecognize 标注 @Transactional，内部调用 PaddleOcrHttpClient.recognize；HTTP timeout 在 application.properties 默认 15000ms（环境可覆盖），IdCardOcrProperties 字段初始值为 8000ms。审计账本 #3 标记 OPEN，并说明未改、未做外部 OCR 时序压测。实际 Controller 路径是 POST /api/persons/idcard-image；路线图中 /api/persons/idcard-images 为复数，与代码不一致。V4_013 影印本表的草稿 UUID 机制不能替代通用预审暂存契约。

| 方案 | 优点 | 代价 |
|---|---|---|
| 保持现有同步行为，另做 S3/S4 通用预审异步任务 | 不扩大本轮范围，保留现有上传路径行为。 | 身份影印本长事务问题继续存在，必须在风险账本保持 OPEN/DEFERRED。 |
| 后续单独把旧 OCR 调用移出长事务或异步化 | 可以针对原入口改善事务持有时间和外部服务超时隔离。 | 需单独兼容旧 API 的同步响应/草稿状态，需独立时序与回归证据。 |

**推荐：** S1 不改。S2/S3/S4 新流程不能引用 D7 作为完成证据；后续单独测量 OCR 调用耗时、事务边界和失败回滚，再决定同步拆分还是异步化。旧入口的兼容改变要单列决策和验收，不与迁入预审任务混为一项。

**责任：** 张锦源负责旧 Java 事务定位；若共用 OCR 服务，王家兴提供推理契约，赵书亚提供超时/部署观测；张蕊核验旧表单交互。<br>
**影响：** 不阻止新的文档分析任务用独立 Mock 及 Python 契约开工；旧身份证录入性能/故障演示仍未验收。<br>
**未决项：** 外部 OCR 实测时延、演示是否仍使用旧入口、是否复用底层 OCR 适配，待后续专项确定。

## 跨组对接清单（供实际联调时使用，不作为 S1/S2/S3 开工门槛）

以下问题用于后续真实服务对接。本轮不向任何组员发送消息，也不要求逐项签字。后端可先用 Mock 按本文 schema 编写和评估自己的部分。

### 王家兴：Python AI 服务对接

1. **政策问答 Schema**<br>
   背景：现有 Java PolicyTaskAgentService 已有引用、证据等级、建议动作和模式字段。<br>
   推荐：Python 返回 answer、citations、sourceVersion/effective date、knowledgeBaseVersion、evidenceLevel、model/retrieval/reranker version、elapsedMs。<br>
   需要对接时明确：Python 输出可否按本稿 1.0 字段提供，字段增删如何版本化？<br>
   影响：S4 内部 HTTP 适配、前端引用组件、无证据表现。

2. **文档分析 Schema**<br>
   背景：Java 需要把识别字段展示在原图上，并将模型输出与后端实体映射分开。<br>
   推荐：fieldCode/value/fieldStatus/confidence/source.materialRef/page/boundingBox；alert 显示缺件、低置信度、冲突；附模型与规则版本。<br>
   需要对接时明确：坐标单位是否归一化到 0—1；字段路径和告警代码表采用什么主版本？<br>
   影响：S3 消费者适配和 S4 原图定位、字段修正 UI。

3. **材料引用和读取方式**<br>
   背景：材料由 Java 持有，MQ 只传引用。<br>
   推荐：Python worker 经服务身份调用 Java 内部短时授权文件流；只读共享卷作为备选。<br>
   需要对接时明确：选内网流还是只读卷；服务到 Java 的网络方向、MIME 和最大文件大小。<br>
   影响：赵书亚卷/网络配置、王家兴容器权限、双实例验收。

4. **置信度、版本和空值**<br>
   背景：低置信度不是错误事实，检索分数也不是答案正确概率。<br>
   推荐：置信度范围 0—1 或 null；字段无识别时 value=null 并给 fieldStatus；来源位置为空时 source=null；模型版本逐请求返回。<br>
   需要对接时明确：实际模型会提供哪些字段/置信度，有哪些情况下不提供？<br>
   影响：字段展示与评测指标解释。

5. **超时和错误**<br>
   背景：消息任务有限重试，政策查询要快速 fallback。<br>
   推荐：政策 2s connect/8s total；文档分析 2s connect/120s read，临时故障由 Java 有限重试。<br>
   需要对接时明确：Python 推理的实际 P95、过载码、模型不可用码、可重试码。<br>
   影响：S3 backoff 配置、S4 政策响应体验与监控阈值。

### 张蕊：前端与 JavaFX 对接

1. **预审上传**<br>
   背景：正式申请材料不能在申请创建前写入现有 application_material。<br>
   推荐：先传到独立暂存并显示每份材料状态，再启动 AI 任务。<br>
   需要对接时明确：身份证、户口簿、申请表是否同屏上传，替换/取消动作如何呈现？<br>
   影响：S4 上传组件、暂存删除和防重复点击。

2. **任务进度语义**<br>
   背景：HTTP 202 只表示 Java 已持久化受理。<br>
   推荐：按 PENDING/RUNNING/SUCCEEDED/FAILED 显示；受理时文案写“已提交分析”，不写“预审通过”。<br>
   需要对接时明确：轮询间隔、页面刷新后恢复方式及终态展示。<br>
   影响：S3 GET task 轮询，S4 任务页面。

3. **冲突、低置信度、无匹配**<br>
   背景：模型字段是未验证建议，候选数据受用户 DataScope 约束。<br>
   推荐：显示原图位置与字段差异；授权候选使用掩码和短时 token；无匹配时给出现有建档入口。<br>
   需要对接时明确：哪些字段逐项确认、哪些候选信息可展示。<br>
   影响：S4 entity-resolution UI 和权限联调。

4. **草稿预览和显式确认**<br>
   背景：AI 不得直接写正式迁入业务。<br>
   推荐：用户先核对字段、目标户、材料标准码和提醒，点击确认后只创建 DRAFT。<br>
   需要对接时明确：确认页字段顺序、确认后导航与重复点击表现。<br>
   影响：S4 confirm API 和幂等体验。

5. **双轨流程**<br>
   背景：传统 POST /api/migrations/in/applications 必须保持独立可用。<br>
   推荐：保留“人工填写”入口，并将 AI 预审作为独立入口；AI/Python/MQ 故障不阻断人工路径。<br>
   需要对接时明确：两个入口的菜单、默认页和失败后回退方式。<br>
   影响：前端路由和演示脚本。

6. **运维数据展示**<br>
   背景：JavaFX 为待教师确认项，聚合 API 尚未实现。<br>
   推荐：只消费 Java overview 返回的服务健康、请求延迟/错误率、任务计数、实例 ID 和时间窗。<br>
   需要对接时明确：哪些指标需要在 Web 和 JavaFX 同时展示？<br>
   影响：S5/S6 看板字段和刷新策略。

### 赵书亚：数据与部署对接

1. **RabbitMQ 环境和投递行为**<br>
   背景：后端建议 Outbox、publisher confirm、至少一次投递和幂等消费者。<br>
   推荐：Broker 提供持久化队列、有限重试、DLQ、消费者并发上限和任务积压观测。<br>
   需要对接时明确：可用环境、重启持久化、确认模式、DLQ 查看方式和连接参数名称。<br>
   影响：S3 可复现验收和 S5 部署。

2. **MySQL 读写路由责任**<br>
   背景：课件要求 MySQL 读写分离，现有后端只有单 DataSource。<br>
   推荐：赵书亚主责复制和路由组件，张锦源标记事务/Mapper 与必须读主库路径并复核配置。<br>
   需要对接时明确：路由实现由谁起草、运行环境提供哪些主从 URL/账号变量、延迟时如何 fallback。<br>
   影响：S5 共享 Java 配置和核心业务回归。

3. **共享材料文件卷**<br>
   背景：两 Java 实例和 Python worker 必须按同一授权规则读取材料。<br>
   推荐：用户不可见随机对象引用、私有卷；Python 优先走 Java 内容流，卷挂载只读且不暴露宿主绝对路径。<br>
   需要对接时明确：卷是否可共享、挂载点/权限、空间和清理责任。<br>
   影响：S4 存储适配、S5 双实例/容器。

4. **Redis 与双实例鉴权一致性**<br>
   背景：当前 TokenRevocationService 的本地 Map 只在当前 JVM 可见；Redis 默认关闭。<br>
   推荐：双实例验收时使用共享 Redis 撤销标记，或另行提供共享撤销机制；不把单机 fallback 当跨实例一致性。<br>
   需要对接时明确：双实例是否保证 Redis 可用、Redis 故障时 logout/revocation 的接受行为。<br>
   影响：S5 SecurityConfig、TokenRevocationService 和故障演练。

5. **日志追踪字段**<br>
   背景：异步链路需要跨请求、消息、Python 和实例定位。<br>
   推荐：统一 traceId、taskId、instanceId、eventId、errorCode、elapsedMs；不记录证件原文、令牌和内部路径。<br>
   需要对接时明确：Log4j2 输出字段和采集侧索引名称。<br>
   影响：S3 故障定位、S5 监控和答辩证据。

6. **Actuator/Micrometer 与聚合 API**<br>
   背景：当前只有公开 GET /api/health 的 DB/Redis 状态摘要。<br>
   推荐：赵书亚负责指标采集及聚合服务；张锦源负责受保护 Controller/SecurityConfig；前端只访问 Java 业务 API。<br>
   需要对接时明确：指标后端、窗口、权限码、管理端口网络边界及实例标签。<br>
   影响：S5/S6 运维看板与安全审查。

7. **Docker/部署接口**<br>
   背景：仓库当前没有 Compose；各模块将逐步拆分/新增 Python、MQ。<br>
   推荐：赵书亚维护总体 Compose 和卷；各服务负责人提供构建/启动/健康检查参数；仅内部网络开放推理与 Broker。<br>
   需要对接时明确：服务名、端口、healthcheck、环境变量和本地/云端目标。<br>
   影响：S5 双实例和 S6 清洁环境交付。

### 指导教师/课程负责人：教学验收口径

1. **Web Service**<br>
   背景：课件写 Web Service，但没有明示 SOAP/WSDL。<br>
   推荐：按保守方案实现复用统计逻辑的最小 SOAP/WSDL 适配，同时保留 REST。<br>
   需要明确回答：SOAP/WSDL 是否必须？若否，REST 服务是否可以作为 Web Service 验收？<br>
   影响：S2 依赖选择、服务描述和答辩演示。

2. **RMI**<br>
   背景：课件要求 Java RMI 远程对象访问。<br>
   推荐：两个独立 JVM 的实际调用及远端不可用演示。<br>
   需要明确回答：是否要求独立进程、是否要求异常/降级演示、统计服务场景是否足够？<br>
   影响：S2 DTO、服务部署和演示材料。

3. **JavaFX**<br>
   背景：课件将 JavaFX 列为可视化框架。<br>
   推荐：保留独立轻量运维看板，不重写 Vue 业务系统。<br>
   需要明确回答：是否为硬性要求？最小展示范围是否可为健康、延迟、任务概览？<br>
   影响：张蕊 S5/S6 工作量和部署方式。

4. **缓存工具**<br>
   背景：课件推荐 MemCached，现有项目已采用可选 Redis。<br>
   推荐：复用 Redis 完成缓存演示；如教师强制指定 MemCached，再小范围补适配。<br>
   需要明确回答：Redis 是否可满足缓存验收，还是必须使用 MemCached？<br>
   影响：赵书亚缓存实现、容器部署和性能测试。

5. **其他强制技术点**<br>
   背景：目前课件文本列出的重构、质量、UI 和运维技术已映射至本文。<br>
   推荐：按上述映射准备演示并保留每项失败证据。<br>
   需要明确回答：是否还有课程说明、实验要求或验收格式要求中未被课件覆盖的强制项？<br>
   影响：对应模块阶段排期和最终验收材料。

## S1 最终冻结检查表

本表将“后端可开工基线”和“全组最终冻结/联调”分开。未完成全组最终确认不阻止后端 S2/S3 在隔离分支使用 Mock 开始。

### 后端 S2/S3 开工基线

- [x] 分工文件给出的四位能力主责和接口方向已直接采用。
- [x] 当前个人集成基线与 snapshot SHA 已核实为 d6b0b87b74af7a413214d7da9dd30b075cec882b。
- [x] AI 任务四态、幂等键、Outbox、有限重试和安全错误语义已有后端建议。
- [x] Java→Python 两个接口、版本字段、材料引用和降级路径已有 1.0 Mock 结构。
- [x] 迁入字段、DataScope、材料 PENDING、申请 DRAFT 和显式 executeIn 安全边界已有约定。
- [x] 共改 Java 文件建议按能力主责与双人复核，不要求后端负责人独自承担全部 shared code。
- [x] 当前 S1 变更范围只包含三份 Markdown 文档。
- [ ] 教师给出验收答案。此项不阻止按 SOAP/WSDL、双 JVM RMI、JavaFX 最小看板的保守方案开始准备。

### 后端与外部服务最终联调/契约冻结（后续检查项）

- [ ] 王家兴服务对接文档分析与政策接口字段、认证、版本和性能实测。
- [ ] 赵书亚提供 MQ、Redis、MySQL 主从、共享文件卷和容器的运行参数及故障行为。
- [ ] 张蕊按 task 状态、字段冲突、候选掩码和显式确认完成 UI 对接。
- [ ] Java 与 Python 的消息/HTTP schema 版本、时间和错误码完成集成回归。
- [ ] 实际存储保留期限、APPLICATION_FORM 是否入正式编码字典确定。
- [ ] 全链路审计、追踪和跨实例访问得到真实部署证据。
- [ ] 教师课程口径及其他强制技术点书面记录。

### 业务安全验收检查项

- [ ] 未授权用户无法查询或读取他人的暂存材料、任务结果、字段候选和确认令牌。
- [ ] 模型输出不能决定 personId/toHouseholdId，确认时重查 DataScope、家庭户状态、区划、现有户籍及批次。
- [ ] AI 成功结果中的正式材料 verifyStatus 仍为 PENDING。
- [ ] AI 确认只能创建 DRAFT；正常提交、L3 核验、审批和显式 executeIn 继续独立进行。
- [ ] Python 或 MQ 故障不会破坏现有 POST /api/migrations/in/applications 人工入口。
- [ ] PersonIdCardImageService 的长事务问题仍作为独立后续风险，不能由新异步任务替代关闭。

## S2/S3 并行施工前置判断

**判断：后端可以按本稿的 PROPOSED 契约基线启动 S2/S3 的独立施工准备，不需要等待其他成员签字或 Python/MQ 实例上线。**

- S2 可按保守教学方案准备独立 JVM RMI、动态代理和 SOAP/WSDL；外部技术依赖可先使用本地示例/Mock。
- S3 可先定义 Java task/outbox/message/consumer 接口和状态持久化边界，使用可替换的 MockDocumentAnalysisClient 验证消息消费与失败路径。后续接入赵书亚提供的 RabbitMQ，再完成真实 Broker、publisher confirm、DLQ 和恢复验收。
- Python 不可用时使用 MockPolicyQueryProvider/MockDocumentAnalysisClient 和 Java 本地 fallback；实际对接时通过版本化 DTO 适配。
- S2/S3 仍应从施工时核实的最新个人集成 SHA 创建单独分支；本轮不创建长期集成分支，不修改 integration/main/snapshot。
- “可以开工”不等于外部联调或课程验收已经完成。真实服务、教师口径和双实例证据留在各后续阶段。

## 本轮重要现状及契约冲突

1. application_material.application_id 非空外键与“先上传、后正式申请”冲突，需要独立暂存对象或改变交互顺序。
2. PersonIdCardImageService 只处理身份证影印本；OCR HTTP 请求处在事务方法中，最大默认等待时间在 application.properties 为 15 秒；审计账本 #3 仍 OPEN。
3. 路线图把现有身份证影印本 API 写成复数 /api/persons/idcard-images；真实 Controller 路径为单数 POST /api/persons/idcard-image。backend-api-index.md 还漏列此接口和 /api/assistant/policy。
4. MigrationService.createIn 在当前方法未显式对 person 和目标 household 查询应用 DataScope；审计账本 #21 指出目标户区划与迁入区划未比对。S1 规定新解析流程约束，不代表已修现有接口。
5. TokenRevocationService Redis 不可用时使用 JVM 本地 Map；在双实例环境下不是共享撤销状态。双实例一致性需由赵书亚与张锦源后续联调。
6. 当前 pom.xml 和 application.properties 没有 RabbitMQ/Actuator/Micrometer/读写路由配置；部署目录有单后端 Nginx 示例，不是完整双实例 Compose。
7. 当前数据库迁移目录为 V4_001—V4_014；数据库/部署指南仍提到只执行 V4_001—V4_010。S1 未运行或修改迁移。

以上现状来自基线源码和现有 API/部署/迁移文档，仅作为设计输入；本轮未重新执行 86 项审计账本。
