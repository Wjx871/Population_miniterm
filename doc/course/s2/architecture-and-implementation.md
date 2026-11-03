# S2 架构与实现说明

## 目标与边界

S2 在现有 Java 17、Spring Boot 3.5.3、MyBatis 单体后端上增加独立的 RMI、反射/动态代理和 SOAP/WSDL 演示链路。原有 `/api/statistics/**` 响应、权限规则和业务写入流程保持不变；远程进程只接收聚合计数，不访问数据库，不接收人口实体或个人字段。

SOAP/WSDL 与双 JVM RMI 是按 S1 保守教学方案实施。教师尚未确认 Web Service 是否必须使用 SOAP/WSDL，本文不把该教学口径描述为已确认。

## 当前统计来源与业务定义

主后端的 `StatisticsService.getSummary()` 先读取 `DataScopeCriteria.current()`，再执行 `StatisticsMapper.xml` 中的统计 SQL。S2 只复用这个受当前用户范围约束的汇总，不把 Service、Mapper 或安全上下文移进远程 JVM。

| S2 字段 | 来自现有汇总 | 当前 SQL 定义 |
|---|---|---|
| `residentCount` | `residentCount` | `person.current_status_code='REGISTERED'` 且至少有一条 `residence.status='ACTIVE'` 的去重人员数。 |
| `floatingRegistrationCount` | `floatingCount` | `floating_population` 中 `current_flag=1` 且 `status='ACTIVE'` 的登记行数，并按来源申请关联范围过滤；这是行数，不保证是去重人员数。 |
| `activeKeyPopulationCount` | `keyPopulationCount` | `key_population.status='ACTIVE'` 的去重人员数，按 person 范围过滤；现有 SQL 不要求人员当前状态为 REGISTERED。 |
| `activeHouseholdCount` | `householdCount` | `household.status='ACTIVE'` 的家庭户行数，按 household 范围过滤；SELF 范围当前 SQL 返回 0。 |

这些指标共用用户的 `DataScopeCriteria`，但使用各自的 SQL 范围规则，分子也不保证是 `residentCount` 的子集。因此 `floatingRegistrationToResidentRatio`、`activeKeyPopulationToResidentRatio` 和 `residentToActiveHouseholdRatio` 只表示对应计数的比率，不是总人口构成百分比，值可以大于 1。

所有线协议计数使用 Java `long`，合法范围为 `0..Long.MAX_VALUE`。比率使用 `BigDecimal.divide`，保留 4 位小数并采用 `HALF_UP`；分母为 0 时结果为 `null`。计算不先做整数乘法，避免溢出。

## 共享计算契约

请求包含 `schemaVersion="1.0"`、`requestId`、`traceId` 以及四个计数；响应回传关联字段和计数，并给出三个命名比率。`requestId` 和 `traceId` 使用 1—64 位 ASCII 字母、数字、点、下划线或连字符。`traceId` 用于关联日志，绝不是身份凭据。

独立 CLI 的固定输入明确标为 `SYNTHETIC_DEMO`。受保护的 Spring 入口不接收客户端统计数字，而是从当前认证请求中的 `StatisticsService` 汇总生成远程请求。`isMock=true` 的汇总会被拒绝，避免把虚构数据标成真实数据库统计。

## RMI 与 Java 动态代理

- `PopulationMetricsRemote` 是 RMI Remote 接口；请求、响应和校验异常可序列化。
- `RmiServerMain` 在独立 JVM 中创建 Registry 和远程对象；端口默认分别为 1199、1200，均使用绑定 `127.0.0.1` 的 `RMIServerSocketFactory`。`java.rmi.server.hostname` 同样固定为 `127.0.0.1`。
- 客户端连接 Registry 时使用的 `RMIClientSocketFactory` 会校验实际 host/port；Registry 返回的 Remote Object stub 使用同样的端点校验，实际 `createSocket(host, port)` 参数来自 RMI stub 引用，非 `127.0.0.1` 或非预期端口会被拒绝。连接超时为 1 秒、读取超时为 2 秒。另由 `RmiStubVerifier` 检查 JDK 远程代理/引用类型；演示脚本用 `Get-NetTCPConnection` 核对 Registry 与对象的实际监听地址、端口及 PID。
- `PopulationMetricsClientFactory` 使用 `Proxy.newProxyInstance` 创建 JDK 动态代理。`RmiInvocationHandler` 用 `Method` 反射调用远程接口，记录接口、方法、耗时、结果或异常分类，不记录请求参数。
- 错误分类为 `REMOTE_UNAVAILABLE`、`REMOTE_TIMEOUT`、`REMOTE_FAILURE`；业务参数校验错误单独报告为 `INVALID_REQUEST`。服务端演示延迟通过 `s2.rmi.demo-delay-millis` 注入，默认 0，最大 30 秒。

## SOAP/WSDL

使用 Apache CXF 4.1.9 的 JAX-WS frontend 与 HTTP Jetty transport。Maven 依赖树实际解析到 `cxf-rt-transports-http-jetty` 和 Jetty 12.0.22；未增加 Spring Boot CXF Starter。CXF 官方 [4.1.9 Release Notes](https://cxf.apache.org/cxf-419-release-notes.html) 将其定为 Jakarta EE 10、JDK 17 基线；项目内仍以依赖树、编译及 Spring Boot/H2 上下文结果作为兼容验收。当前工作站运行时为 JDK 21，Maven 编译使用 `--release 17`。

`SoapServerMain` 是独立 Java 进程，不启动 Spring Boot。服务绑定 `http://127.0.0.1:8091/services/statistics`，可通过 `s2.soap.port` 覆盖端口；WSDL 位于 `?wsdl`。WSDL 当前服务 QName 为 `{urn:com:wjx871:population:s2}PopulationMetricsSoapContractService`。客户端从 WSDL 创建 JAX-WS `Service`，连接/读取超时分别为 1 秒/2 秒；WSDL 装载使用进程级 URLConnection 超时。

无效输入通过 `InvalidPopulationMetricsException` 映射为 SOAP Fault；服务不可达由客户端捕获并以非零退出码结束。HTTP transport 由 Jetty 提供，地址只绑定回环接口。

## Spring 后端适配与追踪

`GET /api/s2/statistics/aggregate` 是独立新增入口，默认由 `population.s2.remote.enabled=false` 关闭。开启后它沿用现有 `/api/**` JWT 认证和 `statistics:view` 方法权限，读取当前用户范围内的 StatisticsService 汇总，再调用 RMI；不接受请求体或前端提供的计数。远程不可用时返回 `ApiResponse` 503 和安全错误码，不影响原统计 API 或应用启动。

HTTP 可传入安全格式的 `X-Trace-Id`；缺失或不合规时 Java 生成 UUID，并在响应头与响应数据中返回。RMI/SOAP 请求把同一 `traceId` 送至服务端。每个 JVM 自行生成 `instanceId`。Log4j2 ThreadContext 在 `finally`/try-with-resources 中恢复，日志不写统计请求参数、身份信息、凭据或远程堆栈。
