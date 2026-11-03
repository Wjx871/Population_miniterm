# S2 验收与运行记录

日期：2026-11-03
施工分支：`feat/20261103-s2-java-distributed-tech`
基线：`1dd1d05ce9e1ed8c0680081505d9366ded9de5ae`

## 构建与依赖

- Windows 11 x64；Apache Maven 3.9.16；本机仅发现 Oracle JDK 21.0.7，没有安装 JDK 17。项目及测试源码由 Maven 使用 `--release 17` 编译；本轮没有在 JDK 17 运行时上执行测试，也未安装或修改系统 JDK。
- `mvn dependency:tree` 实际解析到 `cxf-rt-frontend-jaxws:4.1.9`、`cxf-rt-transports-http-jetty:4.1.9` 和 Jetty `12.0.22`。独立 SOAP 服务已实测由 CXF Jetty HTTP Transport 提供；没有添加 Spring Boot CXF Starter。
- `.\mvnw.cmd test`：Surefire 报告汇总 500 tests、0 failures、0 errors、0 skipped。包含新 S2 H2 集成测试、远程进程测试和 #78 XLSX 测试；没有连接 MySQL 或课程共享数据库，也没有执行数据库迁移。
- `.\mvnw.cmd -DskipTests package`：BUILD SUCCESS，重新编译 269 个生产源码和 49 个测试源码并生成 Spring Boot JAR；此条命令跳过测试，测试结论以上一条全量 test 结果为准。

## RMI 回环监听与 stub 端点

Windows 演示脚本为 Registry 和 Remote Object 分配不同固定端口，并读取 `Get-NetTCPConnection -State Listen` 的实际监听地址、端口和进程 PID。成功演示记录：

| 服务端点 | 实际监听地址 | 端口 | 监听进程 PID |
|---|---|---:|---:|
| RMI Registry | `127.0.0.1` | 1199 | 38776 |
| RMI Remote Object | `127.0.0.1` | 1200 | 38776 |

服务进程同时将 `java.rmi.server.hostname` 设为 `127.0.0.1`。每次新建 Socket 时，客户端工厂会校验并记录实际 host/port；RMI 连接复用时不会为每次业务调用重复记录。业务调用的成功判定不读取诊断日志或共享观察状态。

合成数据调用成功，RMI 客户端 PID 为 24856；客户端与服务端日志共享 traceId `trace-demo-rmi-20261103`，instanceId 分别为 `7a785481-ea9e-4fd6-8fed-c5845b6719e6` 和 `2f7a4c1d-33e1-4dcf-9849-ff6c4d2f978c`。停止服务后分类为 `REMOTE_UNAVAILABLE`。


## RMI 连接复用与并发回归

修复前在旧版本（基线提交 `aecce2ea23b12a399e1a9543b985d88b590cc149`）运行新回归时，连续调用场景的第三次业务调用（requestId `serial-2`）报 `REMOTE_FAILURE`；服务端日志确认前两次 `serial-0`、`serial-1` 已成功，第三次未到达服务端。根因异常为旧逻辑的“本次未重新观察到 Registry 与对象两个 Socket”。这直接复现了 RMI 复用连接与逐调用队列断言不兼容的问题。

修复后，`S2RmiClientConcurrencyIntegrationTest` 以独立子 JVM 启动真实 RMI 服务端，并在同一个 Java 动态代理上完成 12 次串行成功调用和 8 线程各 12 次并发调用（共 96 次）。每次均校验 requestId、traceId 和四个计数回传一致，没有跨请求串值或误判。测试还分别验证两个正确回环端点可连接、错误 host/port 被拒绝、停止服务返回 `REMOTE_UNAVAILABLE`、3500 ms 延迟触发 `REMOTE_TIMEOUT`。

修复前回归运行结果为 3 个测试中 1 个错误（重复调用在断言处失败）；修复后定向运行 3 个测试全部通过。现有 `S2RmiHttpEndToEndIntegrationTest` 继续覆盖 Spring HTTP 适配到独立 RMI JVM 的链路。

## SOAP/WSDL 独立进程

独立 SOAP JVM 的 Jetty 日志记录绑定地址 `127.0.0.1:8091`；Windows 监听检查得到 `127.0.0.1:8091`、PID 43756。脚本通过 HTTP GET 获取 `http://127.0.0.1:8091/services/statistics?wsdl`，状态码为 200，WSDL 服务 QName 为 `{urn:com:wjx871:population:s2}PopulationMetricsSoapContractService`。

SOAP 客户端 PID 36984 完成合成数据调用。客户端与服务端日志共享 traceId `trace-demo-soap-20261103`，instanceId 分别为 `f7f311a1-27a7-446b-ac0a-df52967bfbac` 和 `0ad8c339-4c13-4b7e-94df-3515746dad94`；负计数返回 SOAP Fault `NEGATIVE_COUNT`；停止服务后客户端报告不可用并以预期非零码退出。Spring Boot 原有测试在包含 CXF 依赖的完整 Maven 测试中一并通过。

## 统计语义与边界测试

业务计数和不同 SQL 范围见 [架构与实现说明](architecture-and-implementation.md)。比率字段使用 `floatingRegistrationToResidentRatio`、`activeKeyPopulationToResidentRatio`、`residentToActiveHouseholdRatio`，不表示总人口构成百分比。计算器测试覆盖比率大于 1、分母为零、负计数拒绝、`Long.MAX_VALUE` 边界及避免整数乘法溢出。

Spring 适配入口默认关闭；启用时仅在当前授权请求范围内从原有统计服务获取汇总，远程请求不携带个人实体信息。集成测试覆盖默认关闭、认证/权限及 H2 统计适配路径。

## 审计账本 #78：真实 XLSX 检查

新增定向测试两次调用真实导出服务生成 XLSX，再用 Apache POI 重新打开工作簿检查 22 个输入值。每份文件的数据格均为 `STRING`，工作簿公式格数量为 0，读回值符合现有转义行为。没有复现公式单元格或公式执行，因此没有修改导出生产代码。测试现使用 JUnit 默认 `@TempDir` 清理模式，测试结束自动清理临时 XLSX。

这项结果只描述生成文件的 OOXML/POI 检查，不证明 Microsoft Excel、LibreOffice、第三方导入器、CSV 转换或后续去空白/类型转换链的所有消费方式均安全。前导空白绕过当前前缀转义检查仍是静态缺口；应在已确定受支持的消费端上验证后再决定是否需要最小生产修复。审计账本 #78 状态未被修改。

## 可复现命令

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
.\mvnw.cmd dependency:tree "-Dincludes=org.apache.cxf:*,org.eclipse.jetty:*"
powershell -ExecutionPolicy Bypass -File scripts\windows\s2-demo.ps1
```

跨进程演示脚本在 `target/s2-demo-logs/20261103-222832-328` 写入本轮进程日志；该目录位于 Git 忽略的 `target` 下。脚本退出后只停止本次启动的子进程。复核时 1199、1200、8091 均无监听套接字，也没有残留的 S2 RMI/SOAP Java 服务进程。
