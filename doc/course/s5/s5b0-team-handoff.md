# S5-B0 团队交接与S5-B1准入候选

日期：2026-11-06（Asia/Shanghai）。源码基线：`9f9c13727c552f031b97862dea1595db7e643453`。当前交付状态：`CODE_CANDIDATE`。本文没有向任何成员发送消息，没有声称取得组员确认。

环境字段、接口和准入条件均为 **PENDING_CONFIRMATION**；所有真实运行验收均为 **NOT_VERIFIED**。三位成员可直接回复下面的清单，张锦源将确认来源、版本、时间和证据汇总后再提出冻结版本。未回复、仅提供地址或只读健康200均不构成冻结/通过。

关联：[环境契约和14组件矩阵](s5b0-environment-contract.md)、[正式联调手册](s5b0-integration-runbook.md)、[证据模板](s5b0-evidence-template.md)、[只读预检指南](s5b0-preflight-guide.md)、[本轮验证](s5b0-verification.md)。分工文字来源为主工作区 `专业工程实训/邑循项目详细分工.md` 第7及10.2节和个人路线图；这些文件不在本worktree，不生成失效相对链接。

## 1. 协作交付和确认方式

| 接口/交付 | 主责 | 共同复核 | 需要的实际信息和证据 | 当前状态 |
|---|---|---|---|---|
| 隔离环境、Compose/Nginx、A/B、共享卷、故障/压测 | 赵书亚 | 张锦源、王家兴、张蕊按模块参加 | 环境可用窗口、配置入口/commit、镜像/JAR版本、地址/端口、instanceId、网络/TLS、安全凭据引用、回滚和日志负责人 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| MySQL主从与DataSource路由 | 赵书亚 | 张锦源复核业务一致性 | 主/从节点身份、复制与延迟、路由配置名称/机制、连接池、39矩阵物理SQL节点、主/从故障策略、迁移和备份授权 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| Redis严格撤销和普通缓存 | 赵书亚 | 张锦源 | 版本≥6.2、共享一致读写端点、DB/prefix、ACL、PXAT、noeviction、持久化/丢失窗口、恢复隔离和旧JWT失效方案 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| RabbitMQ环境和Outbox/DLQ观测 | 赵书亚 | 张锦源负责消息业务，王家兴参与推理 | Broker/VHOST、最小授权、交换机/队列/DLQ参数、持久卷、confirm/return、容量/租约、DLQ只读观察和人工重放责任 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| Document Analysis | 王家兴 | 张锦源，赵书亚核环境 | 实际内部服务/schema/错误/认证/超时/模型版本；受控材料读取方式；Java缺HTTP适配和地址/认证配置，不能以填env替代实现 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| Policy Query | 王家兴 | 张锦源，张蕊核引用展示 | schema1.0和实际HTTP服务、知识库/模型/检索/重排版本、可信来源及章节、HTTPS/service-token、错误/性能/健康方式 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| 上传/进度/修正/确认及传统入口 | 张蕊 | 张锦源，王家兴供输出样例 | 页面/前端commit和环境地址、状态/失败/重试映射、候选显式选择、幂等交互、DRAFT/材料绑定、受控引用展示与截图操作 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| JavaFX/运维看板 | 张蕊 | 赵书亚采集，张锦源鉴权，王家兴Python指标 | 教师验收口径、最小窗口/字段/角色/新鲜度、失败展示、接口实现分工；S1聚合ops路径仅PROPOSED且未实现 | PENDING_CONFIRMATION；真实NOT_VERIFIED |

每个实际确认至少登记：条目ID、确认人、确认时间与时区、版本/配置入口、有效范围、未决项、脱敏证据位置、恢复负责人。密钥只填secret引用/版本和“已安全注入”的受控确认，不在回复中贴密码、JWT、连接串凭据、个人数据或内部材料路径。

冻结前，张锦源复核当前源码支持的名称和能力；涉及 `pom.xml`、SecurityConfig、DataSource、application配置、迁移等公共文件，由能力主责提供单一改动集并由相关负责人复核。S5-B0不擅自补这些生产实现，也不修改其他组员分支。

## 2. 给赵书亚的可复制对接文本

```text
赵书亚，S5-B0已整理真实环境契约候选；当前尚未获得你的环境确认，以下全部为PENDING_CONFIRMATION，真实验收仍NOT_VERIFIED。请按条目回复实际信息/可提供时间/阻断项，不要在回复中粘贴任何密码、JWT、服务token或真实人员数据。

基线：9f9c13727c552f031b97862dea1595db7e643453。
请先复核 doc/course/s5/s5b0-environment-contract.md 的14组件和GAP-01—GAP-08，再按以下清单交付：

1. 环境：可用隔离测试环境、部署窗口/时区、环境负责人；Compose/Nginx配置入口和commit；各镜像/Java JAR版本及哈希。不要把既有文件或计划写成“已部署”。
2. A/B：实际内部地址/端口与入口代理地址；分别用-Ds2.instanceId配置安全且不同的ID。源码没有INSTANCE_ID env映射。同宿主监听不能冲突，容器内端口可以相同。请提供upstream与Java日志关联方案，不能依靠粘滞会话掩盖共享问题。
3. 配置：两实例JWT_SECRET必须同一随机密钥，JWT_REVOCATION_MODE=SHARED_REDIS_REQUIRED、REDIS_ENABLED=true，并声明生产环境。只确认secret引用/版本与安全注入，不打印密钥。Redis端点/DB/prefix须是同一一致读写资源；PORT/TIMEOUT/TTL按环境契约核对。
4. MySQL：给出PRIMARY/REPLICA版本、节点身份采集方式、复制/延迟预算、连接池与故障策略、隔离库及备份恢复点。仓库目前只有单DataSource，没有主从路由器/专属env。请提供路由候选改动集与真实配置名，由我复核S5-A2的39条矩阵；RO注解或SELECT本身不是从库许可。
5. 迁移：核对实际DDL/索引/权限与迁移记录至V4_018。旧部署仍写001—010、数据库指南016、旧Verify只覆盖014，不能据旧Verify通过声称S3/S4齐备。请明确隔离库、升级和备份授权/执行人。本轮不会对团队DB执行Fresh、迁移、DROP/TRUNCATE或写入。
6. Redis：版本至少6.2，确认SET/PXAT/EXISTS及普通缓存GET/KEYS/DEL所需ACL、noeviction、容量、时钟、AOF/复制/备份和可接受丢失窗口。旧“Redis可清理/可关闭/无需备份”不适用于strict撤销。PING/恢复连接不能证明撤销历史完整；请给恢复隔离、历史核验、旧JWT失效与重新开放流量责任人。
7. RabbitMQ：提供Broker、隔离VHOST、凭据引用、拓扑/队列/DLQ参数、持久卷、容量水位、confirm/return观察和DLQ只读观测入口。Java已有Outbox/Inbox/lease/manual ACK；本轮不发布、消费或重放真实消息。拓扑不兼容时请给环境侧处置候选。
8. 文件：分别确认上传/正式材料及身份证影印本、AI私有暂存/BOUND材料、导出目录的共享后端、A/B绝对挂载映射、UID/ACL、容量、备份与生命周期。相同相对路径不等于同一份文件；BOUND不能按暂存TTL盲删。Python取文件方式需与你和王家兴共同冻结，不向浏览器公开内部路径。
9. 故障与回滚：列出每次Redis/MySQL/Nginx/Broker/文件故障注入的窗口、隔离范围、执行/停止/恢复负责人、备份验证和旧会话处理；任何删除/清理先列文件清单和范围并取得用户许可。本轮不执行故障注入。
10. 观测/压测：提供traceId/taskId/eventId/instanceId的脱敏日志、实际DB节点/复制位点、慢SQL、KEYS规模、P50/P95/错误率采集方案及证据保存处。Web/JavaFX受保护ops聚合接口尚未实现，字段/角色/采集窗口需与你、张蕊共同确认，不公开中间件管理口。

请回复：条目号｜实际值或安全引用｜版本/配置位置｜确认人/时间｜尚缺内容｜预计可用时间｜恢复负责人｜脱敏证据位置。
请不要直接启动本轮联调或变更共享环境。我们先汇总确认，再按s5b0-team-handoff.md的B1-01—B1-10和runbook安排独立授权的真实联调。
```

## 3. 给王家兴的可复制对接文本

```text
王家兴，S5-B0需要冻结Java↔Python真实接口候选。当前所有部署字段/接口均PENDING_CONFIRMATION，真实Python服务NOT_VERIFIED；Java Mock/HTTP Stub通过不代表你的服务已验收。请分别回复Document Analysis和Policy Query，不要粘贴服务token、模型key、个人数据或物理文件路径。

基线：9f9c13727c552f031b97862dea1595db7e643453。

1. Document Analysis：S1拟议POST /internal/document-analysis；Java当前只有DocumentAnalysisClient接口、强类型Contracts/校验器及MOCK bean，没有真实HTTP适配、base-url/auth配置或受控取内容接口。请确认实际路径/方法、服务commit/镜像/部署状态、内部基址与非推理健康端点；设置AI_PRECHECK_ANALYSIS_CLIENT=HTTP不会自动产生实现。
2. Schema：请逐字段确认schemaVersion=1.0、requestId/taskId/traceId、businessType/migrationType、materialRequirements、materials(materialRef/contentType/sizeBytes/sourceRoleHint)，以及响应analysisDisposition/materials/alerts、字段value/status/confidence/source(page/boundingBox)、modelVersion/ocrEngineVersion/ruleSetVersion/elapsedMs/mode。确认必填、枚举、空值、范围、未知字段/不支持版本和ID不匹配规则，并提供纯合成样例。
3. 认证/读取：请给服务间认证/TLS、token轮换和失败语义候选。浏览器JWT不转发；Python不获业务数据库写权限、不接用户指定路径。现有请求只有不透明materialRef而无文件内容；需共同确定受控内容接口/文件流或经授权的只读共享卷及引用解析、过期/越权/缺对象行为，再由赵书亚确认挂载和内部网络。
4. 时间/资源：Java配置默认connect2s/read120s/processing125s/lease180s，read必须短于processing、lease长于processing。请给实际端到端耗时与P50/P95、并发/内存/模型挂载预算、取消/超时/限流行为；这些是待确认参数，不是已测性能。
5. 错误/重试：请明确401/403/429/5xx、连接/读取超时、非法JSON/schema/引用、永久失败与可恢复失败；返回安全error code/retryable/safeMessage及追踪ID，禁止原始堆栈/凭据。Java做有限重试和租约/version防迟到，不由Python自行审批、改主档或直接创建正式迁入。
6. Policy Query：S1 PROPOSED v1.0 POST /internal/policy-query已有Java HTTP客户端。请确认实际HTTPS基址、服务认证引用、schema、requestId/traceId回显、question/history/businessType/可信regionCode/locale输入；不从浏览器接收内部URL/服务凭据。
7. 政策来源：请提供answer/citationIdsUsed/citations、知识库/模型/检索/重排版本、sourceId/title/section/sourceType/excerpt/sourceVersion/生效期/区划及evidenceLevel。Java会核可信注册表和章节摘录；无法核验来源不会升级为强制材料要求。失败回JAVA_LOCAL_FALLBACK，不将降级显示为真实Python成功。
8. Policy性能：当前源码默认connect2s/request8s，有效connect上限10s、request上限15s，完整响应64KiB，禁止重定向；旧文出现30s不能照抄为现行值。请确认服务端时限/响应大小和可取消行为，避免超时放大或真实付费推理用于健康预检。
9. 交互协作：请与张蕊提供合成材料、冲突/低置信/缺页样例、可信引用展示和人工修正说明；无论AI结果如何，显式确认只创建DRAFT并绑定PENDING材料，人工核验/审批/执行继续由Java权限和事务控制。

请按“服务/条目｜接口及schema版本｜认证引用｜文件读取方案｜错误/耗时｜部署状态及可用时间｜确认人/时间｜脱敏证据”回复。确认前不要把PROPOSED字段写成已冻结；本轮不调用实际推理、不部署你的服务。
```

## 4. 给张蕊的可复制对接文本

```text
张蕊，S5-B0已准备前后端真实联调候选，当前页面/环境/字段确认仍PENDING_CONFIRMATION，真实前端联调NOT_VERIFIED。请按下面清单回复前端commit、可用环境和操作步骤；不要发真实人员/证件、JWT、服务token或内部存储路径。

基线：9f9c13727c552f031b97862dea1595db7e643453。

1. 环境：请提供Web构建commit、实际前端地址/基路径及VITE_API_BASE_URL。仓库示例为/api；正式dist没有Vite开发代理，需要赵书亚确认同源Nginx配置。浏览器仅调用Java，不直连Python、DB、Redis或MQ管理口。
2. 上传/进度：确认POST /api/ai/materials、POST /api/ai/tasks、GET /api/ai/tasks/{taskId}的页面入口和权限提示；任务状态PENDING/RUNNING/SUCCEEDED/FAILED来自持久化状态，不能把202受理当分析成功。上传/任务幂等键保留策略、重复点击/重试、失败/超时/503及恢复交互需可演示。
3. 人工修正：确认resolution-preview页面、冲突字段/证据页码/位置/置信度展示；只显示授权范围内的脱敏候选。唯一候选也需显式选择；修改预览字段后重取token，过期/版本或权限变化/409要提示重新预览，不提交personId/toHouseholdId代替候选token。
4. 显式确认：POST /api/ai/tasks/{taskId}/confirm只创建迁入DRAFT并绑定PENDING正式材料，不代表提交/审批/执行成功。请给确认按钮、防重复提交、回执重放、材料列表、申请跳转与各独立状态的界面映射。之后提交、人工独立核验、审批、执行仍按原权限和业务步骤处理。
5. 双轨：传统迁入DRAFT入口需继续可用；AI/Python/Broker不可用时的入口和安全提示请确认，不能强迫走AI或把本地降级标作Python成功。Redis strict不可用导致的503是认证安全行为，不能显示退出成功。
6. 政策引用：与王家兴确认可展开引用、章节/来源版本/证据等级、needHumanHelp和sourceMode展示。JAVA_LOCAL_FALLBACK与PYTHON_HYBRID_RAG应如实呈现，不把未核验来源自动变成强制材料清单。
7. 跨实例：请准备A上传B读取、A暂存B继续确认、A绑定B查材料、A生成导出B按授权下载、A受理B查任务、A/B同key并发的合成操作及截图；不能以同JVM成功代替通过。文件故障/无权限不展示内部路径或其他用户内容。
8. Web/JavaFX：请确认教师对JavaFX的口径、最小看板需求/启动方式/服务失败展示。S1的GET /api/ops/overview?window=5m和ops:overview:view目前只是PROPOSED、未实现；请与赵书亚确认健康/延迟/错误率/任务积压、窗口/observedAt/新鲜度及角色，再由我核Java接口最小化。未采集显示未知/降级，不用0伪装健康，不让客户端直连管理口。
9. 验收材料：请给合成角色类别、页面操作顺序、预期提示、截图保存位置、traceId/taskId与证据关联方案。截图不记录密码、JWT、证件原文或个人真实数据；请按证据模板区分PASS/FAIL/BLOCKED/NOT_VERIFIED。

请回复：条目号｜页面/字段及接口版本｜前端commit/环境｜操作步骤｜未决问题｜确认人/时间｜预计可用时间｜截图/脱敏证据位置。本轮只收集候选，不启动真实联调或修改你的前端。
```

## 5. S5-B1逐项准入条件

下表是后续真实联调的**阶段准入候选**，不是“全部环境已经就绪”的声明。B1-01—B1-10全部保持 `PENDING_CONFIRMATION`；未满足时相应阶段BLOCKED，真实测试NOT_VERIFIED。可先确认不依赖未实现组件的阶段，但不能跳过缺口后把完整S5验收标PASS。后续实际部署、迁移、故障注入和删除/清理另按用户授权及环境主责确认执行，S5-B0不会自动进入。

| ID | 前置条件与检查点 | 主责 / 复核 | 准入所需证据及不满足时行为 | 当前状态 |
|---|---|---|---|---|
| B1-01 | 确认联调范围、隔离环境、合成人员/角色/文件、时间窗口、参与者及停止/回滚/清理责任；按分工10.2的A→F阶段开展 | 赵书亚 / 四人 | 环境确认单、数据合成说明、风险/恢复负责人和用户对实际动作的授权；缺任一项不执行依赖动作，环境就绪BLOCKED | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-02 | 固定A/B同代码/JAR、Java运行时、依赖版本及安全配置；同secret、strict模式、生产识别、Redis DB/prefix一致；instanceId不同、端口无冲突 | 赵书亚 / 张锦源 | SHA/JAR哈希、版本、脱敏配置分类核对、secret引用确认及进程/实例归属；不打印密钥；仅模板存在不通过 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-03 | MySQL隔离库/备份、应用迁移至018及表/字段/索引/权限可证；升级/恢复权限明确；旧Fresh/Verify不当作018证明 | 赵书亚 / 张锦源 | 备份恢复点、实际DDL与版本账本、015—018差异清单、账号权限；未经授权不执行迁移或DB写入，缺结构阻断S3/S4 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-04 | 两个Python服务契约/部署/认证/TLS/健康/文件读取/错误/时限确认；DocAnalysis缺HTTP与内容读取的Java实现经独立任务完成；Mock不冒充真实 | 王家兴 / 张锦源、赵书亚 | 双方schema样例、服务commit/模型版本、受控引用取内容及越权/过期行为、适配代码/测试证据；仅Policy可用不得把DocAnalysis标通过 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-05 | RabbitMQ真实Broker、隔离VHOST、durable拓扑、最小授权/持久卷/容量/DLQ观测及恢复确认；任务事实在MySQL | 赵书亚 / 张锦源 | 环境/拓扑配置、confirm/return、Outbox/Inbox/lease、失败重试/DLQ与A受理B查询步骤；预检不发布/消费，缺服务则Phase C BLOCKED | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-06 | 上传/影印本、AI私有暂存与BOUND、导出共享后端/绝对挂载/权限/配额/备份及生命周期冻结；Python文件读取不绕授权 | 赵书亚 / 张锦源、王家兴 | A上传B读取/确认/绑定/导出下载的哈希与授权证据计划；BOUND保护及缺对象处理；同JVM或目录存在不通过；共享未定阻断跨实例文件验收 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-07 | Redis≥6.2、共享一致读写、ACL、noeviction、持久化/复制/丢失窗口及恢复隔离/旧JWT失效责任确认；S5-A1真实场景可执行 | 赵书亚 / 张锦源 | 配置/时钟、PXAT与ACL方案；A登录B使用、双向退出401、断连/超时/写失败/登录预检503、不虚假成功及完整历史恢复用例；PING不作替代 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-08 | 主从复制/路由器候选实现独立复核，复用39条矩阵；强一致含RW/锁/授权/状态/事务外幂等/写后读；从库候选有延迟预算和安全失败策略 | 赵书亚 / 张锦源 | DataSource/路由配置和代码版本、物理server_id/read_only/SQL节点、复制位点/延迟、故障回主或失败、缓存权限/DataScope/旧回填/KEYS性能计划；只有注解不能准入“主从已验收” | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-09 | Nginx双upstream与分流/摘除配置、A/B实例日志关联、Web上传/进度/修正/显式确认及传统入口；JavaFX口径和受保护ops实现范围确认 | 赵书亚、张蕊 / 张锦源、王家兴按模块 | 实际代理/前端版本、入口/截图、跨实例追踪、单实例下线恢复步骤；ops无实现则该子项BLOCKED并另行立项，不能新增匿名内部诊断 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| B1-10 | Runbook/证据模板、故障注入/恢复授权、相同压测条件与审计核销规则共同确认，先保存原始脱敏证据再判定 | 四人 / 张锦源汇总 | 每Test ID的SHA/instanceId/traceId/taskId/eventId/真实DB节点/HTTP与业务码/实际结果/时间/负责人/缺陷号；#64需真实分流+strict故障恢复复核才可提议关闭；未执行不得记PASS | PENDING_CONFIRMATION；真实NOT_VERIFIED |

## 6. 分阶段交接与停止条件

顺序遵循分工10.2：Phase A环境/契约→B Java↔Python→C真实Broker/持久任务→D人工修正/确认DRAFT/绑定/提交/人工核验/审批/执行→E Redis/主从/Nginx双实例→F故障恢复/压测/演示。每阶段的操作/预期/停止/回滚/证据按[Runbook](s5b0-integration-runbook.md)逐项执行；不得通过改变顺序绕过授权、结构缺失或真实实现缺口。

| 停止信号 | 处置及交回责任 |
|---|---|
| 环境归属/隔离或合成数据不明确，未获相关操作授权 | 停止依赖动作，交赵书亚和张锦源确认；不扫描内网/猜端口，不连接未知DB |
| 实际配置仍本地撤销、占位密钥、A/B不共享Redis/DB或实例不可区分 | Phase E及跨实例验收BLOCKED，赵书亚给配置修订候选；不临时关strict绕过 |
| DocAnalysis或主从路由/ops仍无真实实现 | 对应子阶段BLOCKED，记录GAP-02/01/08；先独立评审实现，不能只改配置或虚报接口 |
| 写入落从库、授权/锁/幂等读落从库、当前状态旧值误放行 | 停止该业务流和依赖流量，保存事务/SQL节点/回滚证据，交赵书亚与张锦源定位；不在失败事务随意换连接 |
| 已确认撤销键疑似丢失或恢复旧备份 | 隔离认证流量，交赵书亚核持久化/历史完整性并与张锦源确认旧JWT失效；PING不作为恢复安全证据 |
| 文件共享/授权失败、BOUND被误删、其他用户内容或凭据泄露风险 | 停止对应操作，保存最小脱敏证据并由环境/业务负责人恢复；不复制未知文件兜底，不扩大删除范围 |
| 出现不可逆/删除/批量移动/覆盖需求 | 先列拟操作文件/对象、原因、范围和恢复手段，等待用户明确许可；归属不明保留；未经授权不清理 |

## 7. 结果记录和审计状态

使用[统一证据模板](s5b0-evidence-template.md)：Test ID、环境/服务版本、commit SHA、A/B instanceId、角色类别、请求/预期/实际、HTTP/业务code、traceId/taskId/eventId、真实DB节点、脱敏日志/截图、时间、负责人、恢复步骤、BUG-xxx或INT-xxx。真实PASS必须对应本次真实运行和全部判据；既有Mock/H2结果另列背景，不填真实PASS栏。

默认Offline预检只证明已检查的本地准备项，无法获得真实环境确认。S5-B0实际检查结果以[验证记录](s5b0-verification.md)为准；未运行的完整Maven回归记 `NOT_RUN`，不能沿用历史730冒充本轮。

真实Redis/Nginx双实例、真实MySQL主从路由、真实RabbitMQ、真实Python服务、真实前端联调均 **NOT_VERIFIED**。审计 `#64 OPEN`、`#3 OPEN`、`#33 OPEN`、`#21 FIXED_VERIFIED`；同步身份证OCR长事务#3和区划规则#33不因新异步链或准备文档而修复。完成S5-B0后保留feature和worktree等待独立复核，不自动进入部署、故障注入、S5-B1或S6。
