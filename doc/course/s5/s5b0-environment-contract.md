# S5-B0 环境契约冻结候选

日期：2026-11-06（Asia/Shanghai）。源码基线：`9f9c13727c552f031b97862dea1595db7e643453`。交付状态：`CODE_CANDIDATE`。

本文只整理真实联调的输入、责任和阻断项。**候选不等于全组冻结**：实际主机、服务名、端口映射、部署版本、账号权限、共享卷、健康端点和接口变更均未取得组员确认，统一为 `PENDING_CONFIRMATION`；真实环境验收统一为 `NOT_VERIFIED`。下文源码默认值用于识别配置，不代表实际部署值。`EXAMPLE` 只表示候选占位标识，不能直接解析为部署目标。

本轮不修改 Java、pom、DataSource、数据库迁移、Nginx、Compose 或前端，不部署、迁移、写入中间件、调用真实推理或注入故障。只读预检只能验证其明确检查的文件/配置/HTTP健康响应，不能替代真实验收。S5-B1 的逐项准入见[团队交接](s5b0-team-handoff.md)，执行顺序见[联调手册](s5b0-integration-runbook.md)，结果记录见[证据模板](s5b0-evidence-template.md)。

## 1. 依据和责任边界

- 源码事实：[application.properties](../../../src/main/resources/application.properties)、[JWT撤销实现](../../../src/main/java/com/wjx871/population/security/TokenRevocationService.java)、[Redis适配器](../../../src/main/java/com/wjx871/population/cache/OptionalRedisService.java)、[AI预审配置](../../../src/main/java/com/wjx871/population/ai/precheck/AiPrecheckProperties.java)、[Python政策配置](../../../src/main/java/com/wjx871/population/assistant/PolicyPythonProperties.java)。
- 已有约束：[S5-A1严格撤销](s5a1-token-revocation-consistency.md)、[S5-A2事务/缓存及39条路由矩阵](s5a2-transaction-cache-boundaries.md)、[S3消息状态](../s3/message-and-state-contract.md)、[S4-B确认及绑定](../s4/s4b-confirm-draft-architecture.md)、[S4-C政策适配](../s4/s4c-policy-python-adapter.md)。
- 跨组草案：[S1架构决策](../s1/architecture-decisions-and-confirmations.md)、[S1接口草案](../s1/cross-team-contract-draft.md)。其中 PROPOSED 路径/字段保留候选身份；已有Java实现不意味着Python或其他组员已经确认。
- 分工文字来源：主工作区 `专业工程实训/邑循项目详细分工.md` 第7、10.2节及 `专业工程实训/邑循_个人后端课程改造总体开发路线图_v1.1_2026-11-03.md`。这些材料不随本worktree交付，故不生成仓库相对链接。

张锦源负责Java业务、权限、事务和异步编排；赵书亚负责MySQL复制与路由、Redis、RabbitMQ环境、Nginx、Compose、共享卷、日志采集和压测；王家兴负责Python分析/检索服务和算法；张蕊负责Web与按课程口径确认的JavaFX界面。矩阵中的主责表示能力归属，Java部署仍由赵书亚集成，公共文件修改须单一改动集并由相关负责人复核。

## 2. 14组件环境矩阵

矩阵第一列ID保持稳定，供预检和证据引用。网络方向是候选允许的调用方向；任何实际服务名、监听地址和发布端口仍须主责确认。除明确公开的Web/Nginx入口外，数据库、中间件、Python及存储均应仅对受控内部网络/运维访问开放。示例端口均为源码或协议默认值，**不是部署观测**。

| ID | 主责人 | 组件 | 用途与当前仓库实现 | 候选服务名及网络方向 | 连接参数名称 | 认证方式 | 端口与可见性 | 存储与生命周期 | 健康检查方式及限制 | 故障预期 | 真实证据 | 当前状态与待确认问题 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| SVC-01 | 张锦源 | Java Backend A | 已有Spring Boot业务、JWT/RBAC/DataScope、S3任务及S4确认/政策适配；未部署双实例 | EXAMPLE `java-a`；Nginx→A；A→DB/Redis/RabbitMQ/Python/共享存储 | `SERVER_PORT`、`DB_*`、`JWT_*`、`REDIS_*`及下列功能参数；JVM `-Ds2.instanceId` | 浏览器JWT；内部调用用独立服务凭据；运维不复用用户JWT | 源码默认8080；内部端口、宿主映射和TLS终止 PENDING_CONFIRMATION | MySQL保存业务状态；A不独占材料/导出；日志独立归档 | 已有 `GET /api/health`，只查DB SELECT 1及可选Redis PING，不证明strict写/路由/完整依赖 | strict撤销不明503；业务主库失败明确失败；普通缓存可回源；AI故障不自动执行业务 | NOT_VERIFIED | PENDING_CONFIRMATION；确认构建SHA/JAR哈希、Java运行时、A地址、instanceId、资源/日志和重启策略 |
| SVC-02 | 张锦源 | Java Backend B | 复用同一业务版本；跨实例幂等、文件和撤销需要真实证据 | EXAMPLE `java-b`；Nginx→B；B→与A共同资源 | 同A；`SERVER_PORT`和 `-Ds2.instanceId`按实例核对 | 与A同等授权策略；凭据经安全注入 | 源码默认8080；同机监听须无冲突，容器内端口可相同；不得靠粘滞会话验收 | 与A访问同一权威DB/文件对象；日志和进程归属独立 | 同A；健康响应没有统一instanceId字段，分流需Nginx/Java脱敏日志关联 | A下线后B承接正常请求；B故障不能伪造成功或读旧状态继续办理 | NOT_VERIFIED | PENDING_CONFIRMATION；确认B地址、instanceId、A/B调度/摘除和同版本要求 |
| SVC-03 | 赵书亚 | Nginx | 仅有单后端示例配置，尚无双upstream、Compose或实际分流证据 | EXAMPLE `edge`；Web/JavaFX→Nginx→A/B；静态资源→Web产物 | upstream目标、监听/TLS、body-size、超时、健康/摘除、日志格式均非现有Java env | 入口TLS及Java端JWT；禁止通过代理公开内部管理口 | 示例listen80、单目标127.0.0.1:8080；正式端口/证书 PENDING_CONFIRMATION | 只读Web构建产物；代理日志独立保留/脱敏 | 指定HTTP入口健康及upstream日志；200不证明请求到A/B | 单实例下线要安全摘除；重试不得造成非幂等业务重复；禁止以粘滞掩盖共享缺陷 | NOT_VERIFIED | PENDING_CONFIRMATION；提交实际配置入口、upstream标识、trace透传及脱敏日志方案 |
| SVC-04 | 赵书亚 | MySQL PRIMARY | 已有MyBatis/JDBC单数据源；无生产路由器 | EXAMPLE `mysql-primary`；A/B→主库；主库→复制链路 | `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`；主从专属配置当前不存在 | 最小权限数据库账号，凭据不入仓库/证据；复制账号另行管理 | MySQL默认3306，仅内部；实际地址/端口 PENDING_CONFIRMATION | MySQL8、utf8mb4；隔离库、备份、迁移顺序及恢复点须确认；业务/任务/Outbox事实存此 | 当前health只SELECT1；真实联调须经授权采集 `server_id`、`read_only`、实际SQL节点及事务身份 | 写/锁/授权/当前状态仅主库；主库不可用失败，不改投从库写 | NOT_VERIFIED | PENDING_CONFIRMATION；确认隔离库、版本、授权、迁移记录至018、连接池、备份和物理节点证据 |
| SVC-05 | 赵书亚 | MySQL REPLICA | 仓库没有复制部署及读写分离实现；39条矩阵仅建议路由 | EXAMPLE `mysql-replica`；复制来自主库；A/B仅经已复核路由访问候选展示查询 | 不存在可直接填写的replica env/property；禁止虚构 `DB_REPLICA_URL` 等已实现名称 | 只读访问及复制认证由环境主责确认，不从浏览器直连 | 默认3306仅内部；实际端口 PENDING_CONFIRMATION | 复制位点、延迟预算、只读保护及追平策略；不作为主库备份替代 | 需物理节点身份、复制健康/位点/延迟；连接成功不代表追平 | 延迟未知/超阈值/断连时整次只读安全回主或明确失败；失败事务中不任意换连接 | NOT_VERIFIED | PENDING_CONFIRMATION；先交付路由候选和白名单，确认复制/故障恢复后才验收 |
| SVC-06 | 赵书亚 | Redis | 已有普通缓存可选语义与JWT strict语义，共用客户端但故障策略不同 | EXAMPLE `redis-consistent`；A/B→同一主写/一致读端点；禁止读落后副本 | `REDIS_ENABLED/HOST/PORT/PASSWORD/DATABASE/TIMEOUT/KEY_PREFIX`、四类TTL；`JWT_REVOCATION_*` | 密码/ACL安全注入；strict需SET/PXAT及EXISTS；普通缓存另需GET/SET/KEYS/DEL；健康PING | 默认6379，仅内部；TLS/用户名等部署需求未在现有显式env模板定义，需另评审 | ≥6.2支持PXAT；撤销命名空间noeviction、容量、AOF/复制/备份/丢失窗口；不可随意清空 | PING只证当次可达；strict写读及TTL必须在另行授权真实测试中验证 | strict鉴权/正确凭据登录/退出无法确认则503；普通缓存失败回源；数据丢失不被自动识别 | NOT_VERIFIED | PENDING_CONFIRMATION；确认版本/ACL/noeviction/持久化、时钟、历史完整性、恢复隔离和旧会话失效责任 |
| SVC-07 | 赵书亚 | RabbitMQ | 已有拓扑声明、DB Outbox/Inbox、manual ACK、confirm/mandatory及有限重试；真实Broker未验收 | EXAMPLE `rabbitmq`；A/B发布器→Broker→A/B消费者；DLQ仅授权运维观测 | `RABBITMQ_HOST/PORT/USERNAME/PASSWORD/VHOST/CONNECTION_TIMEOUT`；`AI_PRECHECK_EXCHANGE/QUEUE/ROUTING_KEY/DLX/DLQ/DLQ_ROUTING_KEY`等 | 独立VHOST及最小权限账号；管理端不向浏览器/JavaFX直连 | AMQP默认5672；管理端是否开放及端口 PENDING_CONFIRMATION，不能臆定已部署 | durable交换机/队列与持久消息；容量1000默认；Broker持久卷、备份、DLQ和租约观测须确认 | 由主责提供只读健康/观测方式；不以预检发布/消费验收 | DB容量允许且task+Outbox提交后仍可202/PENDING；confirm失败不假标PUBLISHED；有界恢复及DLQ | NOT_VERIFIED | PENDING_CONFIRMATION；核对拓扑参数已有队列兼容性、VHOST、DLQ读权限、恢复和日志 |
| SVC-08 | 王家兴 | Python Document Analysis | 有Java Request/Response/校验器及Mock；**无真实HTTP客户端、分析地址/认证配置** | EXAMPLE `document-analysis`；仅Java worker→拟议 `POST /internal/document-analysis`；文件读取方案另定 | 现有仅 `AI_PRECHECK_ANALYSIS_CLIENT`、CONNECT/READ_TIMEOUT；实际base-url/auth参数不存在 | 服务间认证/TLS及受控文件授权待双方冻结；不转发浏览器JWT | 内部端口及健康路径 PENDING_CONFIRMATION；不允许浏览器直连 | 模型/规则/OCR版本和资源由王家兴提供；不持有业务写权限；不接用户自选路径 | 无已确认健康端点；不能在预检调用推理或付费模型 | 超时/可恢复与永久错误分类后由Java有限重试；失败不改人口/审批/迁移主档 | NOT_VERIFIED | PENDING_CONFIRMATION；确认schema1.0、字段/枚举、错误、实际接口、受控引用取内容、认证及性能；缺口GAP-02 |
| SVC-09 | 王家兴 | Python Policy Query | 已有Java HTTP客户端、引用核验和本地降级；Python服务未验收 | EXAMPLE `policy-query`；Java→基址拼接 `/internal/policy-query`；PROPOSED v1.0 | `POLICY_PYTHON_ENABLED/CLIENT/BASE_URL/AUTH_TOKEN/CONNECT_TIMEOUT/REQUEST_TIMEOUT/ALLOW_INSECURE_LOCAL_HTTP` | 独立Bearer服务token；生产HTTPS；禁止转发用户JWT及模型key | 远端端口/健康路径 PENDING_CONFIRMATION；禁止客户端指定URL和HTTP重定向 | 知识库/模型/检索/重排版本与来源真实性；Python只读检索，不写业务DB | 主责另提供非推理健康端点；业务响应须schema/ID/引用校验 | 超时、401/403/429/5xx或核验失败回Java本地来源，明确sourceMode；不伪称Python成功 | NOT_VERIFIED | PENDING_CONFIRMATION；确认实际服务、schema/来源版本、token轮换、HTTPS、端到端耗时与引用范围 |
| SVC-10 | 赵书亚 | 上传及正式材料共享存储 | 普通材料LocalFileStorage、身份证影印本本地子目录；正式材料resolver已有LOCAL/AI_STAGE来源 | EXAMPLE `shared-upload`逻辑资源；A/B经Java鉴权读写，浏览器只访问业务API | `APP_UPLOAD_DIR`、`APP_UPLOAD_MAX_SIZE_MB`；影印本复用上传根；AI_STAGE转SVC-11解析 | OS/卷访问控制加Java owner/DataScope/申请/材料授权；路径不对浏览器暴露 | 无固定TCP端口；卷/对象服务类型和挂载点 PENDING_CONFIRMATION；置于web root之外 | 大小/MIME/SHA检查；与DB非原子；备份、配额、删除/替换补偿和影印本清理策略待确认 | Offline仅查契约；真实验证A上传B授权下载、摘要一致及缺对象安全失败，不创建探针文件 | 文件不可用明确失败，不泄露其他用户/路径；单JVM可读不算共享通过 | NOT_VERIFIED | PENDING_CONFIRMATION；确认共享后端、A/B绝对挂载基准、UID/ACL、容量、备份与生命周期 |
| SVC-11 | 赵书亚 | AI预审私有暂存 | AiPrivateFileStorage、stage/任务关联、BOUND引用、恢复/回收；无共享卷部署 | EXAMPLE `private-ai-stage`逻辑资源；A/B→私有对象；Python只按最终受控方案读取 | `AI_PRECHECK_STORAGE_DIR`、READY/COMPLETED_RETENTION、MAX_MATERIAL_SIZE_MB及resolution清理项 | 不透明stage/material引用，Java所有权/任务关联；Python只获被授权内容 | 不作为静态站点/公开文件端点；内部内容接口目前未实现 | READY/完成默认24h；BOUND受正式材料引用保护，不能按24h盲删；session保留30d默认；与DB备份协调 | A暂存→B轮询/确认→A/B下载绑定材料；观察对象/DB引用和补偿，真实服务另验 | 不存在/损坏对象不得假READY或绑定成功；旧worker结果受租约/version约束 | NOT_VERIFIED | PENDING_CONFIRMATION；确认共享方式、Python最小读取权限、原子提升/一致性、BOUND保留及恢复责任 |
| SVC-12 | 赵书亚 | 导出文件共享存储 | ExportService本地文件、授权下载/计数、清理任务已存在；跨实例未验证 | EXAMPLE `shared-export`；A/B→共享导出；浏览器经Java下载 | `EXPORT_DIR`、`EXPORT_FILE_RETENTION_DAYS/CLEANUP_CRON/NORMAL_MAX_ROWS/SENSITIVE_MAX_ROWS` | 业务导出权限、敏感审批与当前授权检查；文件系统最小权限 | 无固定端口；不得映射为公开静态目录 | 默认保留7d；行数上限5000/20000；备份/清理与DB记录一致，清理由主责授权 | A生成→B按授权访问，记录哈希/行数；无权限拒绝和缺文件表现须验证 | 不绕过审批/下载授权；共享缺失明确失败，禁止从其他用户文件兜底 | NOT_VERIFIED | PENDING_CONFIRMATION；确认卷、权限、容量、并发清理/备份和生成实例下线后访问 |
| SVC-13 | 张蕊 | Web前端 | 已有Vue/Vite前端与业务页面；S3/S4完整交互及实际部署待联调 | EXAMPLE `web`；浏览器→同源Nginx `/api`→Java；不直连Python/MQ/DB | `VITE_API_BASE_URL=/api`；开发脚本 `FRONTEND_PORT`默认5180不是生产监听契约 | Java登录JWT与现有RBAC/DataScope；服务凭据不得打入前端构建 | 正式域名/TLS/路径和静态服务端口 PENDING_CONFIRMATION；Vite代理不随dist存在 | 静态构建SHA；不缓存敏感材料和内部路径；上传/任务UI需失败恢复与幂等约定 | 指定静态入口+合成业务操作截图；页面打开不代表流程完成 | 正确显示503/失败/降级；候选需人工显式选择确认；保留传统草稿入口 | NOT_VERIFIED | PENDING_CONFIRMATION；确认页面提交、上传/进度/修正/确认状态、引用展示、地址和操作截图 |
| SVC-14 | 张蕊 | JavaFX/运维看板数据接口 | 分工与S1只有PROPOSED聚合接口；**无 `/api/ops/overview`、ops权限或聚合采集实现证据** | EXAMPLE `ops-client`；Web/JavaFX→受保护Java聚合API；不直连中间件管理口 | 候选路径 `/api/ops/overview?window=5m`、权限 `ops:overview:view`均非已实现配置 | 角色授权及只读数据最小化待赵书亚/张锦源/张蕊共同确认 | API端口复用Java候选入口；运维暴露边界和JavaFX教师口径 PENDING_CONFIRMATION | 聚合窗口/新鲜度/保留策略；不含个人记录、连接串、token、文件路径、消息内容 | 未实现不能探测为PASS；health不能代替受保护指标和看板链路 | 未采集/不可达显示UNKNOWN或DEGRADED及时间，不用0伪造健康 | NOT_VERIFIED | PENDING_CONFIRMATION；确认课程需求、字段/角色/数据来源、接口实现分工；缺口GAP-08 |

## 3. A/B配置分类与真实名称

分类含义：`MUST_EQUAL`为双实例一致的策略/值；`MUST_DIFFER`为能区分实例的标识；`SHARED_RESOURCE`为同一权威资源（字符串可因安全账号或挂载映射不同）；`INSTANCE_LOCAL`为允许独立但须预算协调；`TO_BE_CONFIRMED`为当前不存在实现或尚无部署契约。所有部署取值和确认记录均为 `PENDING_CONFIRMATION`，表内默认值仅为已核实源码事实。密钥不打印、不写证据；由密钥管理员在受控通道确认相同secret引用/版本及注入结果，预检不读取或计算真实密钥指纹。

| 参数/输入 | 当前property或读取位置 | 源码默认值 | 分类 | A/B核对及待确认要求 |
|---|---|---|---|---|
| 代码/JAR、迁移集合 | 构建产物、迁移目录 | 基线SHA见文首 | MUST_EQUAL | 全部升级同版本；记录commit和JAR哈希；混合版本不算缓存隔离验收 |
| `JWT_SECRET` | `security.jwt.secret` | 本地开发占位密钥 | MUST_EQUAL | 同一随机≥32 UTF-8字节密钥；长度校验不会识别占位值，禁止沿用示例 |
| `JWT_EXPIRE_MINUTES` | `security.jwt.expire-minutes` | 120 | MUST_EQUAL | 正值；统一旧会话最长失效等待窗口和时钟 |
| `JWT_REVOCATION_MODE` | `security.jwt.revocation.mode` | SINGLE_INSTANCE_LOCAL | MUST_EQUAL | 双实例必须SHARED_REDIS_REQUIRED；不能用本地例外通过 |
| `JWT_REVOCATION_PRODUCTION` | `security.jwt.revocation.production` | false | MUST_EQUAL | 生产显式true；prod/production profile也触发门禁，false不绕过profile |
| `SPRING_PROFILES_ACTIVE` | Spring原生profile激活 | 未设置 | MUST_EQUAL | prod/production或已确认生产profile；非这两个profile须显式production=true |
| `JWT_REVOCATION_ALLOW_PRODUCTION_SINGLE_INSTANCE` / `JWT_REVOCATION_PRODUCTION_SINGLE_INSTANCE_APPROVAL` | `security.jwt.revocation.allow-production-single-instance` / `.production-single-instance-approval` | false / 空 | MUST_EQUAL | 双实例不启用例外；编号不是跨实例保证 |
| `JWT_REVOCATION_CLEANUP_INTERVAL/BATCH_SIZE` | `security.jwt.revocation.cleanup-interval/cleanup-batch-size` | PT30S / 256 | INSTANCE_LOCAL | 必须正值；只是本地清理参数，不是共享撤销TTL |
| `REDIS_ENABLED` | `population.redis.enabled` | false | MUST_EQUAL | 双实例strict必须true；普通缓存“可关闭”不适用于strict |
| `REDIS_HOST` | `spring.data.redis.host` | localhost | SHARED_RESOURCE | 同一主写/一致读端点；各容器localhost通常不是共享服务 |
| `REDIS_PORT` | `spring.data.redis.port` | 6379 | SHARED_RESOURCE | 与HOST共同定位同一端点；不猜测部署端口 |
| `REDIS_DATABASE` | `spring.data.redis.database` | 0 | MUST_EQUAL | 同一逻辑DB；与其他测试空间隔离 |
| `REDIS_KEY_PREFIX` | `population.redis.key-prefix` | population | MUST_EQUAL | 同一前缀；区分团队/隔离环境，不能按A/B分开 |
| `REDIS_TIMEOUT` | `spring.data.redis.timeout` | 2s | MUST_EQUAL | 确认连接/命令预算和上层截止时间；不能用PING推断strict可用 |
| `REDIS_PASSWORD` | `spring.data.redis.password` | 空 | INSTANCE_LOCAL | 可同一secret或分别授权；均满足ACL；不输出明文；用户名/TLS额外需求待评审 |
| `REDIS_REGION_TTL/DICTIONARY_TTL/OVERVIEW_TTL/TREND_TTL` | `population.redis.region-ttl/dictionary-ttl/overview-ttl/trend-ttl` | 30m / 30m / 5m / 5m | MUST_EQUAL | 展示TTL叠加复制延迟；不承担授权/办理强一致 |
| `DB_URL` | `spring.datasource.url` | localhost:3306/population_miniterm的MySQL URL | SHARED_RESOURCE | 当前唯一数据源必须指向同一权威隔离库；默认含createDatabaseIfNotExist=true，预检不得据此连接/建库 |
| `DB_USERNAME` | `spring.datasource.username` | root | INSTANCE_LOCAL | 可分账号但目标/授权能力需相同；部署不沿用root默认；账号不回显 |
| `DB_PASSWORD` | `spring.datasource.password` | 空 | INSTANCE_LOCAL | 安全注入；不写文档/日志；无凭据不进行DB探测 |
| 主从专属路由参数 | 无现有property/生产路由器 | 不存在 | TO_BE_CONFIRMED | 由赵书亚提交候选名称/选择时机/连接池/故障规则，张锦源按39矩阵复核；不得声称只填env即可启用 |
| `SERVER_PORT` | `server.port` | 8080 | INSTANCE_LOCAL | 同宿主同地址绑定必须不同；不同容器可都8080，宿主映射由赵书亚确认 |
| JVM `-Ds2.instanceId` | `S2TraceContext`读取system property | 随机UUID | MUST_DIFFER | A/B明确安全ID，格式[A-Za-z0-9._-]、1–64；没有INSTANCE_ID env映射 |
| `LOG_DIR` | Log4j2：JVM system property优先、其次env | logs | INSTANCE_LOCAL | 独立可归属路径/文件，日志权限/采集/保留期待确认；traceId和instanceId模板现有 |
| `APP_UPLOAD_DIR` | `application.upload.dir` | ./data/uploads | SHARED_RESOURCE | 普通正式材料及身份证影印本必须跨实例可读；确认绝对解析基准/挂载映射 |
| `APP_UPLOAD_MAX_SIZE_MB` | `application.upload.max-size-mb`及multipart两项 | 10 MB | MUST_EQUAL | Nginx body限制与Web约束同步；不以代理放宽替代业务校验 |
| `EXPORT_DIR` | `export.dir` | ./data/exports | SHARED_RESOURCE | A生成B下载；不可静态公开；确认权限/保留/清理责任 |
| `EXPORT_NORMAL_MAX_ROWS/SENSITIVE_MAX_ROWS` | `export.normal-max-rows/sensitive-max-rows` | 5000 / 20000 | MUST_EQUAL | 同一业务预算，敏感导出仍须审批和当前授权 |
| `EXPORT_FILE_RETENTION_DAYS/CLEANUP_CRON` | `export.file-retention-days/cleanup-cron` | 7 / 0 30 2 * * * | MUST_EQUAL | 确认双实例并发清理和备份协调，不在本轮执行 |
| `AI_PRECHECK_STORAGE_DIR` | `population.ai.precheck.storage-dir` | ./data/private-ai-stage | SHARED_RESOURCE | 同一私有对象集合；A上传B确认和AI_STAGE下载；相同相对字符串不证明同一卷 |
| `AI_PRECHECK_ENABLED` | `population.ai.precheck.enabled` | false | MUST_EQUAL | 真实S3/S4流程须双方明确true；关闭时不将普通业务成功算AI成功 |
| `AI_PRECHECK_RABBIT_ENABLED` | `population.ai.precheck.rabbit-enabled` | false | MUST_EQUAL | 真实Broker阶段须true；仅开enabled不证明MQ已接入 |
| `RABBITMQ_HOST/PORT` | `spring.rabbitmq.host/port` | localhost / 5672 | SHARED_RESOURCE | 同一Broker集群/隔离资源；主责提供真实端点 |
| `RABBITMQ_VHOST` | `spring.rabbitmq.virtual-host` | / | MUST_EQUAL | 同一隔离VHOST，不复用未知团队队列 |
| `RABBITMQ_USERNAME/PASSWORD` | `spring.rabbitmq.username/password` | guest / guest | INSTANCE_LOCAL | 不沿用guest生产默认；两实例均获所需声明/发布/消费权限；不输出 |
| `RABBITMQ_CONNECTION_TIMEOUT` | `spring.rabbitmq.connection-timeout` | 2s | MUST_EQUAL | 与confirm/恢复策略预算共同确认 |
| `AI_PRECHECK_EXCHANGE/QUEUE/ROUTING_KEY` | `population.ai.precheck.exchange/queue/routing-key` | population.ai.precheck.exchange / population.ai.precheck.queue / population.ai.precheck.request | MUST_EQUAL | 同一持久拓扑；A/B竞争消费，不建立独立重复业务队列 |
| `AI_PRECHECK_DLX/DLQ/DLQ_ROUTING_KEY` | `population.ai.precheck.dead-letter-exchange/dead-letter-queue/dead-letter-routing-key` | population.ai.precheck.dlx / population.ai.precheck.dlq / population.ai.precheck.dead | MUST_EQUAL | 确认DLQ观测、保留和重放授权，预检不消费/发布 |
| `AI_PRECHECK_CONCURRENCY/PREFETCH` | `population.ai.precheck.consumer-concurrency/prefetch` | 2 / 1 | INSTANCE_LOCAL | 每实例预算与总容量协调，不能把2误认为集群总并发 |
| `AI_PRECHECK_QUEUE_CAPACITY` | `population.ai.precheck.queue-capacity` | 1000 | MUST_EQUAL | 拓扑x-max-length与DB capacity事实均核对；现有队列参数冲突需环境主责处置 |
| `AI_PRECHECK_MAX_ATTEMPTS/BACKOFF` | `population.ai.precheck.max-processing-attempts/backoff` | 3 / PT2S,PT10S | MUST_EQUAL | 有限重试；永久/可恢复分类按双方schema冻结 |
| `AI_PRECHECK_PROCESSING_TIMEOUT/WORKER_LEASE` | `population.ai.precheck.processing-timeout/worker-lease-duration` | PT125S / PT180S | MUST_EQUAL | lease必须大于processing；时钟/运行预算由双方确认 |
| `AI_PRECHECK_CONNECT_TIMEOUT/READ_TIMEOUT` | `population.ai.precheck.analysis-connect-timeout/analysis-read-timeout` | PT2S / PT120S | MUST_EQUAL | 当前Mock配置已有；read必须小于processing；不代表真实HTTP已实现 |
| `AI_PRECHECK_ANALYSIS_CLIENT` | `population.ai.precheck.analysis-client` | MOCK | MUST_EQUAL | 目前只有MOCK bean；设置HTTP不会产生HTTP实现，可能导致启用流程缺bean |
| Document Analysis实际地址/认证参数 | 无Java显式配置、HTTP客户端或受控取内容实现 | 不存在 | TO_BE_CONFIRMED | GAP-02：王家兴和张锦源确认后另行实现；不得虚构现成env名称 |
| `AI_PRECHECK_READY_RETENTION/COMPLETED_RETENTION` | `population.ai.precheck.ready-retention/completed-retention` | PT24H / PT24H | MUST_EQUAL | BOUND不按暂存TTL删除；保留及恢复待主责确认 |
| `AI_PRECHECK_MAX_MATERIAL_SIZE_MB` | `population.ai.precheck.max-material-size-mb` | 10 | MUST_EQUAL | 合成文件样本遵守MIME/容量预算 |
| `AI_RESOLUTION_TOKEN_TTL/SESSION_RETENTION` | `population.ai.precheck.resolution-token-ttl/resolution-session-retention` | PT5M / P30D | MUST_EQUAL | 短期预览令牌与会话留存不同；session留存须长于tokenTTL |
| `AI_RESOLUTION_CLEANUP_ENABLED/BATCH_SIZE/POLL_MS` | `population.ai.precheck.resolution-cleanup-enabled/resolution-cleanup-batch-size/resolution-cleanup-poll-ms` | true / 100 / 3600000 | INSTANCE_LOCAL | batch为1–1000且poll正值；双实例清理协调待确认 |
| `POLICY_PYTHON_ENABLED/CLIENT` | `policy.python.enabled/client` | false / MOCK | MUST_EQUAL | 真实政策阶段选择HTTP并明确启用；本地降级不冒充Python |
| `POLICY_PYTHON_BASE_URL` | `policy.python.base-url` | 空 | SHARED_RESOURCE | 同一兼容版本服务，可是同集群不同入口；固定拼接/internal/policy-query |
| `POLICY_PYTHON_AUTH_TOKEN` | `policy.python.auth-token` | 空 | INSTANCE_LOCAL | 两实例均有独立服务授权；可同secret或不同有效token；禁止用户JWT转发 |
| `POLICY_PYTHON_CONNECT_TIMEOUT/REQUEST_TIMEOUT` | `policy.python.connect-timeout/request-timeout` | PT2S / PT8S | MUST_EQUAL | 源码有效connect上限10s、request上限15s，connect不超过request；完整响应有界64KiB |
| `POLICY_PYTHON_ALLOW_INSECURE_LOCAL_HTTP` | `policy.python.allow-insecure-local-http` | false | MUST_EQUAL | 生产强制HTTPS；开发例外只限回环，不允许远程明文HTTP |
| `VITE_API_BASE_URL` | Vite构建时读取，非Java property | /api（前端示例） | TO_BE_CONFIRMED | 张蕊确认同源路径/构建环境；Nginx负责正式反代；不填内部Python地址 |
| ops API/角色/采集参数 | 只有S1 PROPOSED草案 | 未实现 | TO_BE_CONFIRMED | GAP-08；不能把/api/health直接当作运维聚合接口 |

Mock控制项 `AI_PRECHECK_MOCK_MODE/DELAY/FAILS_BEFORE_SUCCESS` 与 `POLICY_PYTHON_MOCK_MODE/DELAY`仅用于合成测试，不能混入真实能力PASS。S2另有 `S2_REMOTE_ENABLED=false`、registry1199/object1200默认；若纳入实际环境，由赵书亚与张锦源确认仅内部开放及RMI主机发布参数。它们不代替双业务实例。

## 4. 版本、启动和迁移核对

| 对象 | 已核实仓库事实 | 候选要求及当前边界 |
|---|---|---|
| Java / Spring Boot | [pom.xml](../../../pom.xml)目标Java17、Boot3.5.3；既有S5-A1/A2报告运行JDK21.0.7 | 实际Java vendor/完整版本、JAR哈希由赵书亚登记PENDING_CONFIRMATION；不能写为JDK17运行时验收 |
| MySQL | 源码driver为MySQL Connector/J；迁移按MySQL8编写 | 实际8.0/8.4、sql_mode、字符集、时区、隔离级别、连接池和节点身份PENDING_CONFIRMATION；H2不证明物理路由 |
| Redis | strict使用原子SET+PXAT与EXISTS | ≥6.2、ACL、noeviction、内存容量、持久化、时钟同步PENDING_CONFIRMATION；普通SET确认不等于副本刷盘/零丢失 |
| RabbitMQ | [AiRabbitConfiguration](../../../src/main/java/com/wjx871/population/ai/precheck/AiRabbitConfiguration.java)声明durable直连拓扑、reject-publish、manual ACK | Broker版本/镜像摘要、VHOST、已有队列参数、磁盘水位和持久卷PENDING_CONFIRMATION；无真实运行结论 |
| Python | DocAnalysis只有Mock；PolicyQuery已有HTTP适配 | 两服务commit/镜像、模型/OCR/规则/知识库/检索/重排版本、schema1.0均PENDING_CONFIRMATION |
| Nginx / Web / JavaFX | [Nginx示例](../../../config/nginx.population.conf.example)只有单目标；前端Vite；ops聚合未实现 | 配置commit、入口TLS、前端构建SHA、JavaFX教学口径及客户端版本PENDING_CONFIRMATION |
| 数据库迁移 | [迁移目录](../../database/migrations/)含V4_001—V4_018；未发现Flyway/Liquibase自动版本治理 | 逐项核对实际已应用版本、DDL/索引/权限及备份；不能依据文件存在宣称已迁移 |

V4_015为AI stage/task/material/capacity/outbox/inbox/audit；V4_016增加person.version和resolution session/candidate；V4_017增加session清理索引；V4_018增加确认权限、application_material的storage_kind/storage_reference及唯一索引、receipt和binding表。顺序为015→016→017→018，参照[018升级说明](../../database/s4b-V4_018-upgrade.md)。本轮只读核对，不执行升级/回滚、不重放全量建库脚本。

[旧部署中文说明](../../deployment/backend-v1-deployment.md)和[旧部署英文说明](../../deployment/deployment-guide.md)仍写001—010；[数据库指南](../../database/backend-v1-database-guide.md)称最新016，均落后于当前018。[init_database.ps1](../../../scripts/windows/init_database.ps1)的Fresh导入总SQL而非逐项迁移，Verify的显式表/索引集合仅覆盖到014，不含S3/S4表；**旧Verify通过不证明018齐备**。不擅自修改旧升级流程，交由赵书亚提出隔离库与备份下的实施候选。

[start_backend.bat](../../../scripts/windows/start_backend.bat)会读取start.local.env/.env.backend并执行Maven启动；[start_backend.ps1](../../../scripts/windows/start_backend.ps1)会调用停止脚本、创建目录、删除旧日志和启动JAR。这些都不是只读预检依赖，S5-B0不调用。根[.env.example](../../../.env.example)不会自动成为Spring启动输入；BAT优先现有进程变量，其次本地env文件；直接JAR由部署方显式注入。双方需确认实际启动入口和有效配置来源，不能只比较模板文本。

## 5. 文件、任务和服务契约

### 5.1 存储边界

| 对象 | 当前源码事实 | 跨实例验收与待确认输入 |
|---|---|---|
| 普通正式材料 | [LocalFileStorageService](../../../src/main/java/com/wjx871/population/material/LocalFileStorageService.java)写APP_UPLOAD_DIR；数据库记录不是物理文件备份 | A上传B在授权下下载并核摘要；共享后端/挂载/权限/备份PENDING_CONFIRMATION |
| 身份证影印本 | [PersonIdCardImageService](../../../src/main/java/com/wjx871/population/person/idcard/PersonIdCardImageService.java)复用上传根下私有目录，业务接口鉴权 | 合成影印本A上传B读取；保留本人/当前授权边界，不能静态公开；同步OCR长事务审计#3仍OPEN |
| AI预审暂存 | [AiPrivateFileStorageService](../../../src/main/java/com/wjx871/population/ai/precheck/AiPrivateFileStorageService.java)写私有根；stage状态、摘要和任务关联存MySQL | A暂存B创建/查询任务和继续确认；缺失对象不得假成功；Python读取机制PENDING_CONFIRMATION |
| AI绑定正式材料 | [ApplicationMaterialStorageResolver](../../../src/main/java/com/wjx871/population/material/ApplicationMaterialStorageResolver.java)区分LOCAL/AI_STAGE；[BoundStageMaterialStorage](../../../src/main/java/com/wjx871/population/material/BoundStageMaterialStorage.java)从同一私有根解析 | A绑定B列表/下载一致；BOUND保护、解绑后RELEASE_PENDING补偿与备份一致性PENDING_CONFIRMATION；不随暂存TTL盲删 |
| 导出 | [ExportService](../../../src/main/java/com/wjx871/population/export/ExportService.java)生成本地文件，下载有权限和计数 | A生成B下载；敏感导出保留审批与当前授权；故障不能泄露其他用户文件；共享与清理责任PENDING_CONFIRMATION |
| task / Outbox / Inbox / DLQ | 数据库是任务状态事实源；Broker不是业务事务；eventId去重、task version/lease及owner/endpoint幂等 | A受理B查询，A/B并发相同key只生成一份；Broker confirm/return、重复投递、DLQ和恢复须真实证据，当前NOT_VERIFIED |

四类文件根必须分别核对：绝对路径解析、A/B是否同一对象、权限/UID、配额、原子提升/可见性、备份恢复点及清理授权。允许后续评审共享卷或受控对象服务，但当前实现以本地Path/文件系统为主，不能在文档中虚称已有S3/MinIO适配。相同路径文本、同一JVM成功或已有目录均不构成共享证明。任何删除、清理或迁移操作先列清单/范围并获得用户许可，归属不明文件保留。

### 5.2 Java↔Python冻结输入

Document Analysis当前[强类型契约](../../../src/main/java/com/wjx871/population/ai/precheck/DocumentAnalysisContracts.java)包含请求 `schemaVersion/requestId/taskId/traceId/businessType/migrationType/materialRequirements/materials`；材料项为 `materialRef/contentType/sizeBytes/sourceRoleHint`。响应包含 `analysisDisposition/materials/alerts/modelVersion/ocrEngineVersion/ruleSetVersion/elapsedMs/mode`及对应ID；字段证据带materialRef/page/boundingBox。对值、枚举、空值、错误结构和重试分类应逐项比对[S1草案](../s1/cross-team-contract-draft.md)，以当前校验器兼容为Java事实，跨组冻结状态仍PENDING_CONFIRMATION。

当前请求只含受控不透明引用，不含文件字节、物理路径或取内容凭据；仓库未实现草案受控内容接口及真实HTTP分析客户端。不能要求Python“按引用自行读文件”而不冻结授权和解析方案。王家兴与张锦源确认接口/文件流或受控读取设计后另行实现，赵书亚确认网络/卷；浏览器JWT不转发，Python不能直接执行人口、迁移、审批写入。

PolicyQuery请求为schemaVersion/requestId/traceId/question/history/businessType/regionCode/locale；regionCode取Java认证上下文。响应需答案、引用及知识库/模型/检索/重排版本，通过结构与可信来源/章节摘录核验；失败整体回退本地，不能保留伪Python成功标记。当前[HttpPolicyQueryClient](../../../src/main/java/com/wjx871/population/assistant/HttpPolicyQueryClient.java)禁止重定向、端到端截止时间覆盖完整响应、响应上限64KiB。当前源码effective request上限15秒，旧S4-C文字曾提30秒，联调按实际源码核对；性能与服务端预算PENDING_CONFIRMATION。

## 6. 稳定缺口台账

以下ID只标识已发现的准备缺口，既不意味着实现任务已获授权，也不意味着组员未做工作；实际外部交付须由主责提供证据确认。本轮不补生产实现。

| ID | 已核实缺口/冲突 | 对真实联调的影响 | 主责及需要的交付 | 当前状态 |
|---|---|---|---|---|
| GAP-01 | 仅单spring.datasource，未发现生产主从路由器/专属配置；39矩阵为建议 | 无法证明PRIMARY/REPLICA_ELIGIBLE实际节点；readOnly/SELECT不是从库许可 | 赵书亚提出DataSource/路由/复制/连接池改动集，张锦源按[S5-A2矩阵](s5a2-transaction-cache-boundaries.md)复核事务外幂等、授权、锁/写后读及失败策略 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| GAP-02 | DocAnalysis只有Mock，无HTTP实现、地址/认证配置和受控取内容接口 | 不能将ANALYSIS_CLIENT改HTTP或填虚构env就宣称Java↔Python已联通 | 王家兴提供服务/schema/认证/读取/性能候选，张锦源独立评审适配，赵书亚确认内部链路 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| GAP-03 | Nginx示例只有127.0.0.1:8080一个目标；未见实际双实例编排 | 不能证明A/B分流/故障摘除，不得靠粘滞会话掩盖一致性 | 赵书亚提供Compose/Nginx配置入口、版本、upstream/instanceId日志关联和回滚计划 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| GAP-04 | 上传/影印本、私有AI_STAGE与导出以本地路径实现；共享卷和Python取内容未冻结 | A写B可能缺文件；同名相对目录可能落不同磁盘；BOUND/清理/备份风险 | 赵书亚提供共享后端/挂载/ACL/容量/生命周期；张锦源核授权/引用，王家兴确认最小文件读取 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| GAP-05 | 根env模板缺JWT_REVOCATION全部、Redis prefix/timeout/TTL、生产profile与PolicyPython参数；本地启动示例也不足 | 复制模板可能仍本地撤销/Mock/单数据源，健康200也无法发现所有偏差 | 张锦源提供本表真实映射，赵书亚确认有效配置来源和安全注入，后续改模板另行授权 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| GAP-06 | 旧部署001—010、数据库指南016、现有迁移018；init Verify只覆盖014/S3S4缺项，无自动迁移账本证明 | 旧Verify或文件存在可产生错误就绪判断，真实确认/绑定流可能缺表/索引/权限 | 赵书亚确认隔离库/备份/实际DDL和迁移记录，张锦源核015—018结构及权限；不运行旧Fresh升级团队库 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| GAP-07 | 旧运维称Redis可直接清理、无需备份、故障可关闭；现strict保存撤销安全事实 | 数据丢失可使旧JWT重新被视为缺key；PING/重连不能证明历史完整；关闭Redis使strict门禁失败 | 赵书亚采用S5-A1 noeviction/持久化/复制/恢复隔离/旧JWT失效契约，张锦源复核；旧文不能用作strict操作依据 | PENDING_CONFIRMATION；真实NOT_VERIFIED |
| GAP-08 | ops聚合API/权限/真实采集未实现；真实Redis/Nginx、MySQL路由、Broker、Python、前端证据均缺 | JavaFX只能候选设计；不得新增匿名内部诊断接口或用旧Mock730当真实PASS | 张蕊确认课程/展示字段，赵书亚确认采集/窗口/安全暴露，张锦源核鉴权最小接口，王家兴补Python指标；按证据模板逐项实测 | PENDING_CONFIRMATION；真实NOT_VERIFIED |

旧运维冲突来源：[backend-v1-operations.md](../../deployment/backend-v1-operations.md)的Redis降级/清理说明及[database-guide](../../database/backend-v1-database-guide.md)“Redis无需备份”。在SHARED_REDIS_REQUIRED场景必须以[S5-A1安全边界](s5a1-token-revocation-consistency.md)为准；任何FLUSH/回滚/旧备份恢复都不是本轮授权动作。无法证明历史完整时先隔离认证流量，由负责人协调旧会话失效（如统一轮换secret或等待全部旧Token失效），不能恢复网络后直接宣布安全。

## 7. 观测、预检判定和验收边界

[S2TraceContext](../../../src/main/java/com/wjx871/population/s2/trace/S2TraceContext.java)接受 `X-Trace-Id`安全格式 `[A-Za-z0-9._-]{1,64}`，否则生成UUID；实例号由 `-Ds2.instanceId`读取，无该参数则随机UUID。[日志模板](../../../src/main/resources/log4j2-spring.xml)包含traceId/instanceId；S3额外持久化taskId和eventId关联。JWT Filter与登录strict预检建立相应trace scope，但没有全局统一响应instanceId头，health也无instanceId；Nginx分流证据须由环境主责提供脱敏upstream日志与Java实例日志对应，不能要求一个不存在的诊断API。

允许预检输出 `PASS/FAIL/BLOCKED/NOT_VERIFIED`及明确理由；**PASS必须绑定检查项**。文件存在≠已部署，端口通≠功能正常，HTTP200≠双实例一致，Redis PING≠strict写权限或撤销历史完整，H2 PASS≠MySQL主从PASS。无实际输入/证据时环境就绪应BLOCKED，真实能力保持NOT_VERIFIED。默认Offline不读取真实凭据、不联网、不连接DB、不启动服务；指定HTTP健康探测需显式模式和地址，非本机另有明确授权开关，详见[预检指南](s5b0-preflight-guide.md)。

已有[S5-A1验证](s5a1-verification.md)677及[S5-A2验证](s5a2-verification.md)730为各轮H2/Mock/Stub回归，S2独立JVM为本机SYNTHETIC_DEMO；不能复制到本轮或真实环境PASS。本轮实际检查/测试以[S5-B0验证记录](s5b0-verification.md)为准，未执行的Maven回归必须写NOT_RUN。

当前：真实Redis/Nginx双实例、真实MySQL主从路由、真实RabbitMQ、真实Python服务、真实前端联调全部 **NOT_VERIFIED**；`#64 OPEN`、`#3 OPEN`、`#33 OPEN`、`#21 FIXED_VERIFIED`。#64只有真实双实例+共享Redis+Nginx分流+故障恢复证据齐备并复核后，才允许提议关闭；本文和只读工具不核销任何这些事项。
