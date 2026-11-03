> S1 REVIEW DRAFT<br>
> 尚未完成全组及教师确认<br>
> 不得视为最终冻结接口

# 课程要求与责任映射

## 范围与依据

本文按《邑循_个人后端课程改造总体开发路线图_v1.1_2026-11-03》界定阶段，直接采用《邑循项目详细分工》已明确的四位成员主责，并用当前个人后端集成基线的真实代码核对 CURRENT 能力。两份规划文件位于项目资料目录“专业工程实训”，没有复制或修改规划文件。后端可按本文推荐的 Mock/兼容契约开始 S2/S3，不以其他成员逐项签字为前置条件。

已读取课程课件《专业工程实训 4-3(1).pptx》：第 2 页列目标，第 3 页列进度，第 4 页列重构技术，第 5—6 页重复列质量提升技术，第 7 页列 UI，第 8 页列智能运维。以下课件要求是按原课件文本核对的；组内分工仍以《邑循项目详细分工》为准。课件列出的第 5、6 页内容重复，不重复计任务。

本稿依据的代码基线为 integration/20261101-backend-p0-security，提交 d6b0b87b74af7a413214d7da9dd30b075cec882b。S1 只形成评审材料；本稿没有新增代码或数据库结构。

### 状态用语

| 标记 | 含义 |
|---|---|
| CURRENT | 在该基线源码或现有文档中可核实的能力。 |
| PROPOSED | 本稿提出的阶段安排、接口或责任建议，尚未获得真实外部服务联调验证或教师确认。 |
| EXTERNAL_PENDING | 真实外部服务接口或教师验收口径尚未确定；记录实际对接事项，不阻止后端按 Mock 基线开发。 |
| DEFERRED | 留给指定后续阶段处理，本阶段不实现。 |

《邑循项目详细分工》给出的四位成员能力主责直接作为已确定的分工依据。没有查到新接口字段或教师验收口径的确认记录，因此这些设计仍标为 PROPOSED 或 EXTERNAL_PENDING，没有使用 CONFIRMED。

## 课程要求、能力缺口与验收证据

| 课程要求及原课件位置 | CURRENT：真实代码或文档 | 缺口与建议阶段 | 能力主责（按《详细分工》） | 后续验收证据建议 |
|---|---|---|---|---|
| Java RMI；第 4 页 | 未发现 RMI 接口、远程对象或 RMI 配置。当前运行主体是 Spring Boot 单体服务。 | S2：做一个最小、低敏感数据的独立进程调用示例；RMI 演示边界须由教师确认。 | 张锦源；赵书亚协助独立进程运行环境。 | 两个独立 JVM 间成功调用；关闭远端后的明确错误或受控降级；服务端和客户端运行说明。 |
| Java 反射、动态代理；第 4 页 | pom.xml 含 Spring AOP，但未发现以反射/动态代理完成课程演示的专用服务调用链。 | S2：以统计或安全的非敏感服务展示代理、方法耗时和异常。 | 张锦源。 | 可重复调用记录、耗时和异常日志；解释代理边界，不以存在 AOP 依赖代替演示。 |
| Web Service；第 4 页 | 现有 REST Controller 是 Web API；未发现 SOAP/WSDL 服务或独立 Web Service 描述。 | S2：保守方案是复用同一统计逻辑增加最小 SOAP/WSDL 适配；REST 继续服务前端。SOAP/WSDL 是否必做由教师确认。 | 张锦源；教师确认验收口径。 | 若要求 SOAP，提供 WSDL、独立客户端调用和失败示例；不得仅以 REST 代替而宣称已满足。 |
| 多线程；第 4 页 | 有定时业务处理等后台逻辑，但未发现材料预审专用的有界线程池、任务持久化和可查状态链路。 | S3：在 RabbitMQ 消费侧采用受控并发，并记录并发限制、失败恢复与幂等证据。 | 张锦源负责任务业务并发；赵书亚负责运行环境。 | 正常、并发、重复消息、消费者中断和恢复记录；不得用无限线程或一次发送成功作为可靠性证据。 |
| RabbitMQ 异步任务；第 4 页 | pom.xml 与 application.properties 未配置 Spring AMQP、RabbitMQ 连接或消费者；没有 AI 任务 API/任务状态持久化。 | S1 冻结状态与消息契约；S3 实现生产者、消费者、Outbox、有限重试和死信。 | 张锦源负责业务消息与任务；赵书亚负责 Broker、持久化和监控；王家兴提供推理端点；张蕊展示进度。 | PENDING/RUNNING/SUCCEEDED/FAILED 状态可查；重复投递无重复业务结果；故障恢复、重试上限和 DLQ 有证据。 |
| 缓存；第 5—6 页 | CURRENT：可选 Redis，默认 REDIS_ENABLED=false；行政区、字典、部分统计和 Token 撤销使用 OptionalRedisService。Redis 故障可退回 MySQL/本地模式。 | S5：在教师口径确认后扩展 Key、TTL、失效和性能对比；是否必须 MemCached 为 EXTERNAL_PENDING。 | 赵书亚负责缓存方案与代码建议；张锦源复核数据范围是否进入缓存键。 | 命中、过期、更新失效、故障回源、DataScope 隔离和相同条件下的性能对比。 |
| MySQL 主从、读写分离；第 5—6 页 | 当前配置为单一 spring.datasource.url；未发现复制拓扑或读写路由 DataSource。 | S5：赵书亚负责主从与路由方案，张锦源核对事务、写后读取和审批/执行一致性。 | 赵书亚主责复制与路由；张锦源负责业务事务边界和代码复核。 | 实际从库读路由、复制状态和延迟；事务写后读主库；从库延迟/故障时回退或失败行为。 |
| Nginx 双实例、负载均衡；第 5—6 页 | 有 config/nginx.population.conf.example，目前只代理一个 127.0.0.1:8080；未发现两个后端实例的 Compose/upstream 部署。 | S5：双实例、统一入口、故障切换、文件卷和 Token 撤销一致性。 | 赵书亚负责 Nginx/实例部署；张锦源协作验证身份状态、任务和材料访问。 | 两实例分流证据；关闭一个实例仍可访问；任意实例都可安全访问文件和任务；记录故障窗口。 |
| Docker 与云化部署；第 4 页 | 部署文档主要说明 Java 17、MySQL、可选 Redis 的原生部署；仓库未发现 Dockerfile 或 Compose 文件。 | S5：按组员提供的构建参数集成 Docker/Compose；远程云部署仅在真实完成后报告。 | 赵书亚主责集成；各服务负责人提供自身镜像/启动/健康检查信息。 | 清洁环境启动、持久化、升级和备份恢复说明；若仅本地 Compose 验证，要如实标为本地部署。 |
| 日志与监控；第 8 页 | CURRENT：Log4j2 控制台与滚动文件；健康接口只检查数据库和 Redis。未发现 Actuator/Micrometer 依赖、延迟分位数、错误率或队列积压聚合接口。 | S5：定义 traceId/taskId/instanceId、指标口径和受保护聚合接口；S1 只定边界。 | 赵书亚负责指标真实性和采集；张锦源负责 Java 埋点与安全暴露；张蕊负责看板消费。 | P50/P95、错误率、服务健康和任务积压来自真实采样；能够关联日志及一个故障恢复过程；不展示凭据。 |
| JavaFX、ECharts、页面/数据压缩；第 7 页 | 仓库已有 Vue 前端与图表使用，未发现 JavaFX 模块。Nginx 示例没有完整的压缩/双实例配置。 | UI 按课程周次安排推进；JavaFX 最小运维看板建议先保留，最终是否要求由教师确认；页面压缩与 Nginx 传输分别协作。 | 张蕊主责 UI、ECharts、JavaFX；赵书亚负责 Nginx Gzip/缓存头和传输测量。 | 图表数据可核对；若教师要求 JavaFX，独立启动并展示健康/延迟/任务；记录拆包、压缩和加载耗时。 |
| RAG、文档预审、智能办理；第 2、4 页及《详细分工》 | CURRENT：Java 本地 Markdown 知识检索（中文二元组计分）、可选外部大模型补充回答、规则式清单/受控动作。当前没有 Python 混合检索、重排序、文档预审任务或 AI 建草稿链路。 | S1 冻结契约；AI 检索与文档理解在 S2/S4 按路线安排；任务及 MQ 在 S3；正式草稿确认属于 S4。 | 王家兴负责 Python RAG、OCR/文档理解和评测；张锦源负责 Java 任务、权限和业务写入；张蕊负责交互与材料标注。 | 引用可追溯；无依据明确拒答；虚构材料评测；低置信度/冲突交人工确认；模型不写数据库、不代审批。 |

“总计 48 课时”是分工文档对课程安排的汇总，不代表每位成员的总投入工时。第 3 页显示第 2—3 周重构、第 4—6 周质量提升、第 7 周 UI、第 8 周运维。

## 技术栈与部署现状

| 范围 | CURRENT 证据 |
|---|---|
| Java 与 Web | Java 17、Spring Boot 3.5.3、Spring Web/Security/Validation；MyBatis Spring Boot 3.0.5。 |
| 数据库 | MySQL Connector/J 为运行驱动，H2 仅用于测试；application.properties 配置单一 spring.datasource.url，没有主从 DataSource 路由。 |
| 缓存 | spring-boot-starter-data-redis 已存在；population.redis.enabled 默认 false；OptionalRedisService 对 Redis 故障提供 MySQL/本地回退。 |
| 日志 | Log4j2 控制台与按日/大小滚动文件，最多 14 个归档；当前 pattern 未包含统一 traceId/taskId/instanceId 字段。 |
| OCR 与助手 | 身份证 OCR URL 可配置，application.properties 默认 15 秒 HTTP 超时；政策外部 LLM 默认关闭，Java 本地检索可用。 |
| 缺少的依赖/配置 | pom.xml 未见 Spring AMQP、Actuator、Micrometer；未见 RabbitMQ URL、读写路由或指标聚合配置。 |
| 上传 | APP_UPLOAD_DIR 默认 ./data/uploads，APP_UPLOAD_MAX_SIZE_MB 默认 10；正式材料仅允许 PDF/JPEG/PNG。 |
| 部署 | doc/deployment 说明原生 Java/MySQL 与可选 Redis；config/nginx.population.conf.example 只代理一个 Java 后端；仓库未发现 Dockerfile/Compose 文件。 |
| 数据迁移 | migrations 目录有 V4_001—V4_014；backend-v1-database-guide.md 和部署说明仍写到 V4_010，后续应修正文档版本清单。 |

以上为源码与文档静态核对，不代表本轮实际启动了 MySQL、Redis、OCR 或部署环境。
## 当前能力与新增能力边界

| 主题 | CURRENT | PROPOSED / DEFERRED |
|---|---|---|
| 正式申请材料 | /api/applications/{id}/materials 需要已存在 applicationId；application_material.application_id 是非空外键。材料服务存储 UUID 文件名、路径、MIME、大小、SHA，并将材料初始化为 PENDING。 | 正式申请创建前增加有归属、有期限的预审暂存对象；AI 输出转正式材料时仍由 Java 绑定并设置 PENDING。 |
| 迁入办理 | POST /api/migrations/in/applications 可独立创建 DRAFT；申请提交、材料核验、审批和 /execute 是分开的已有业务步骤。 | AI 入口只辅助准备字段、解析授权实体并创建 DRAFT；不替代原人工入口、不提交申请、不核验材料、不审批、不执行迁入。 |
| OCR | PersonIdCardImageService 上传身份证影印本后同步调用 OCR；记录所有者、草稿 UUID、影像元数据、OCR 状态与结果。 | 多模态文档分析是新 Python 服务能力；现有身份证 OCR 仍需单独评估事务范围，不因增加异步预审而算作已修复。 |
| 助手 | PolicyAssistantService 本地检索知识库，可选调用外部 Chat Completions API；PolicyWorkflowService 生成只读清单，PolicyTaskAgentService 过滤有权操作。 | Python 混合检索、重排序和政策接口属于新增服务；Java 本地检索保留为显式降级路径。 |
| 鉴权与数据范围 | Spring Security 使用 Bearer JWT、权限码和 DataScope；材料按父申请检查所有者/可见范围；Redis 可选并保存撤销标记。 | 新暂存物、AI 任务、实体候选和结果均需要 Java 按当前身份授权；Python 不接收浏览器用户授权、不接收可自行选择的数据库主键。 |
| 部署、缓存和路由 | MySQL 是业务库；Redis 可选；Log4j2 有文件滚动；单实例 Nginx 样例存在。 | 双实例、RabbitMQ、MySQL 复制/路由、Actuator/Micrometer 和 Docker 均按后续阶段另行设计与验收。 |

## 四维责任矩阵（既定能力主责与后端实现建议）

四位成员的能力主责已由《邑循项目详细分工》明确，本表直接采用。跨组 Java 文件的具体代码修改、接口协作和验收细节由路线图列为待决，本表给出后端建议；后续在实际接口对接时协调，不要求成员在 S1 逐项签字，也不阻止 S2/S3 用 Mock 开工。

| 协作事项 | 能力主责 | 代码修改责任建议 | 接口协作责任 | 验收责任 |
|---|---|---|---|---|
| 预审材料暂存及正式材料绑定 | 张锦源负责业务生命周期 | 张锦源负责 Java 暂存/授权/绑定代码和后续迁移；赵书亚负责文件卷与保留策略配置 | 张蕊确认上传/预览/取消交互；王家兴确认材料引用和文件内容读取；赵书亚确认双实例卷 | 张锦源验权限、事务与标准码；张蕊/王家兴联调；赵书亚验跨实例文件可读性 |
| OCR 文本实体解析、迁入草稿 | 张锦源负责数据库权威解析和业务状态 | 张锦源维护解析、DataScope、并发锁、幂等和 DRAFT 创建 | 王家兴提供结构化字段/来源/置信度；张蕊提供确认与修正 UI；赵书亚提供可运行环境 | 张锦源验越权拒绝与状态重检；四人验正常、无匹配、冲突、重试及原人工办理 |
| 政策问答 | 王家兴负责 RAG；张锦源负责 Java 编排 | 王家兴维护 Python 服务；张锦源修改 Java 调用、超时和本地 fallback | 共同冻结引用、版本、错误与模式字段；张蕊显示引用和模式 | 王家兴验检索与引用质量；张锦源验内部认证及降级；张蕊验说明可理解 |
| RabbitMQ 任务 | 张锦源负责业务消息；赵书亚负责 MQ 运维 | 张锦源负责 task/outbox/producer/consumer；赵书亚负责 broker、Compose 和监控；共享 pom/配置双人复核 | 王家兴提供推理端点；张蕊使用任务 API 展示状态 | 张锦源验任务状态/幂等/恢复；赵书亚验队列、确认和 DLQ 指标；前端验状态语义 |
| MySQL 复制与读写路由 | 赵书亚负责复制和路由；张锦源负责业务事务 | 赵书亚负责路由组件、数据源配置初稿；张锦源负责事务注解/查询归属核对并共同复核共享配置 | 赵书亚提供主从参数和延迟约定；张锦源标注必须读主库的业务路径 | 赵书亚验路由真实生效；张锦源验审批、材料核验、迁入执行一致性 |
| Redis 缓存及 JWT 一致性 | 赵书亚负责缓存及运维 | 赵书亚提缓存接入/Redis 配置改动；张锦源复核 DataScope 缓存键、TokenRevocationService 和安全配置 | 共同定义 Redis 不可用和双实例的撤销行为 | 赵书亚验 Redis 与恢复；张锦源验无越权缓存、跨实例撤销和 JWT 行为 |
| 运维聚合 API、Actuator/Micrometer | 赵书亚负责指标采集与指标口径；张锦源负责安全边界 | 建议赵书亚负责依赖、采集配置和指标服务；张锦源负责受保护 Controller/SecurityConfig 变更，必要时结对实现 | 张蕊消费运维 API；赵书亚给出采样窗口与字段定义 | 赵书亚验数据真实；张锦源验权限、无凭据；张蕊验图表与错误状态 |
| Nginx、Docker 和服务部署 | 赵书亚 | 赵书亚维护 Nginx/Compose/卷；其余成员只提供各自服务构建、端口、健康和资源说明 | Java/Python/前端共同确认代理、内部网络和回调边界 | 赵书亚主验部署/恢复，服务负责人复核本模块，张锦源验 JWT/材料权限 |
| 前端预审、JavaFX、ECharts | 张蕊 | 张蕊维护 Vue/JavaFX 页面；后端和 AI 仅修改各自服务 | 张锦源给业务 API；王家兴给展示结果；赵书亚给运维字段 | 张蕊验交互与页面性能；各服务负责人验接口结果 |

### Java 公共文件共改建议

依 v1.1 路线图与详细分工，以下文件应由提出改动的主责人起草，避免多人并行覆盖，并在整合前由张锦源和对应能力主责人双重核对：

| 文件/改动面 | 建议起草人 | 必须共同复核人 | 需要核对的内容 |
|---|---|---|---|
| pom.xml | 引入组件的能力主责人（例如 RabbitMQ 由张锦源、Actuator/Micrometer 由赵书亚） | 张锦源 + 对应主责人 | 依赖用途、版本、传递依赖和现有 H2/MySQL 构建影响。 |
| SecurityConfig、服务间认证过滤器 | 张锦源 | 张锦源 + Python 接口主责王家兴；如牵涉运维则加赵书亚 | 用户 JWT、服务身份、权限码、匿名/内部路由不能因 anyRequest 配置而意外开放。 |
| DataSource/事务/读写路由配置 | 赵书亚起草，张锦源标注业务读写语义 | 赵书亚 + 张锦源 | 事务、审批/执行和写后读强制主库；故障 fallback；启动参数。 |
| application.properties / 环境模板 | 引入相应服务或中间件的能力主责人 | 张锦源 + 对应主责人；部署变量由赵书亚再核对 | 不提交密钥；变量名、默认关闭行为、容器网络和本地演示说明。 |
| 数据迁移 SQL | 对应业务数据主责人；材料/任务由张锦源 | 张锦源 + 赵书亚（数据库运维） | 仅追加版本号；先独立库演练；不改既有迁移、不触碰共享课程库。 |
| traceId/taskId/instanceId 日志格式 | 张锦源维护 Java 埋点；赵书亚维护采集/诊断格式 | 张锦源 + 赵书亚 | 同一请求、消息和 Python 调用能按字段关联，不记录证件原文、Token 或路径凭据。 |
| 受保护的聚合运维 Controller | 建议张锦源维护授权与 Controller，赵书亚维护指标服务 | 张锦源 + 赵书亚；张蕊验证消费 | 只返回约定聚合数据，不暴露 Actuator 管理端口或 MQ/Redis/MySQL 凭据。 |

此表中四位能力主责依分工文件执行；共享 Java 文件的细化代码责任为 PROPOSED。后端可先按本表隔离实现，涉及 SecurityConfig、pom.xml、数据源和迁移的共改仍按双人复核规则处理。

## 重要当前依据

- 材料：src/main/java/com/wjx871/population/material/MaterialController.java、MaterialService.java、LocalFileStorageService.java、src/main/resources/mapper/ApplicationMaterialMapper.xml、doc/database/migrations/V4_002_business_application_approval.sql。
- 迁入及显式执行：src/main/java/com/wjx871/population/migration/MigrationController.java、MigrationService.java、MigrationInRequest.java、MigrationMaterialRequirementService.java；API 说明 doc/api/household-migration-api.md。
- 身份 OCR：src/main/java/com/wjx871/population/person/idcard/PersonIdCardImageController.java、PersonIdCardImageService.java、PaddleOcrHttpClient.java；表迁移 doc/database/migrations/V4_013_person_idcard_image.sql。
- 政策助手：src/main/java/com/wjx871/population/assistant/PolicyAssistantService.java、PolicyWorkflowService.java、PolicyTaskAgentService.java 和 src/main/resources/policy-knowledge/*.md。
- 权限与撤销：src/main/java/com/wjx871/population/security/SecurityConfig.java、DataScope.java、DataScopeCriteria.java、TokenRevocationService.java、src/main/java/com/wjx871/population/application/ApplicationService.java。
- 技术栈/运行：pom.xml、src/main/resources/application.properties、src/main/resources/log4j2-spring.xml、src/main/java/com/wjx871/population/system/HealthController.java、config/nginx.population.conf.example、doc/deployment/。
- 当前 API 总索引 doc/api/backend-api-index.md 没有列出实际存在的 /api/assistant/policy 与 /api/persons/idcard-image；课程路线图把后者写成复数 /api/persons/idcard-images，与 PersonIdCardImageController 中的单数路径不同。索引和路线图均需在后续文档维护阶段对齐，本轮只记录差异。
- 当前迁移清单有 V4_001—V4_014；doc/database/backend-v1-database-guide.md 和部署说明仍写 V4_001—V4_010。该文档差异需要后续维护者核对，S1 不运行或修改迁移。
