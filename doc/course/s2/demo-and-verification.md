# S2 验收与运行记录

日期：2026-11-03
施工分支：`feat/20261103-s2-java-distributed-tech`
基线：`1dd1d05ce9e1ed8c0680081505d9366ded9de5ae`

## 构建与依赖

- Windows 11 x64；Apache Maven 3.9.16；本机运行时 Oracle JDK 21.0.7。项目及测试源码由 Maven 使用 `--release 17` 编译；本轮没有在 JDK 17 运行时上执行测试。
- `mvn dependency:tree` 实际解析到 `cxf-rt-frontend-jaxws:4.1.9`、`cxf-rt-transports-http-jetty:4.1.9` 和 Jetty `12.0.22`。独立 SOAP 服务已实测由 CXF Jetty HTTP Transport 提供；没有添加 Spring Boot CXF Starter。
- `.\mvnw.cmd test`：Surefire 报告汇总 497 tests、0 failures、0 errors、0 skipped。包含新 S2 H2 集成测试、远程进程测试和 #78 XLSX 测试；没有连接 MySQL 或课程共享数据库，也没有执行数据库迁移。
- `.\mvnw.cmd -DskipTests package`：BUILD SUCCESS，重新编译 269 个生产源码和 49 个测试源码并生成 Spring Boot JAR；此条命令跳过测试，测试结论以上一条全量 test 结果为准。

## RMI 回环监听与 stub 端点

Windows 演示脚本为 Registry 和 Remote Object 分配不同固定端口，并读取 `Get-NetTCPConnection -State Listen` 的实际监听地址、端口和进程 PID。成功演示记录：

| 服务端点 | 实际监听地址 | 端口 | 监听进程 PID |
|---|---|---:|---:|
| RMI Registry | `127.0.0.1` | 1199 | 37108 |
| RMI Remote Object | `127.0.0.1` | 1200 | 37108 |

服务进程同时将 `java.rmi.server.hostname` 设为 `127.0.0.1`。客户端拿到 Registry stub 与 Remote Object stub 后，二者的 `RMIClientSocketFactory.createSocket(host, port)` 都会校验实际由 stub 提供的 host/port；任何非 `127.0.0.1` 或非预期端口都会被拒绝。成功调用记录实际端点 `[127.0.0.1:1199, 127.0.0.1:1200]`；Remote Object stub 的代理类为 JDK proxy，远程引用类型为 `UnicastRef2`。因此端点结论同时基于服务端真实监听和客户端实际收到的 stub 连接参数，不只基于客户端配置。

合成数据调用成功，RMI 客户端 PID 为 18804。客户端与服务端日志共享 traceId `trace-demo-rmi-20261103`，并分别记录 instanceId `417d6828-bc86-41fb-a16b-b4e36a18b115` 与 `a55cb5aa-1467-430d-bd64-c89e2b212af3`；停止服务后分类为 `REMOTE_UNAVAILABLE`。将服务端演示延迟设为 3500 ms 后，客户端在约 2082 ms 返回 `REMOTE_TIMEOUT`，与 2 秒读取超时配置相符。

## SOAP/WSDL 独立进程

独立 SOAP JVM 的 Jetty 日志记录绑定地址 `127.0.0.1:8091`；Windows 监听检查得到 `127.0.0.1:8091`、PID 39204。脚本通过 HTTP GET 获取 `http://127.0.0.1:8091/services/statistics?wsdl`，状态码为 200，WSDL 服务 QName 为 `{urn:com:wjx871:population:s2}PopulationMetricsSoapContractService`。

SOAP 客户端 PID 37556 完成合成数据调用。客户端与服务端日志共享 traceId `trace-demo-soap-20261103`，并分别记录 instanceId `1cf50047-1e10-4405-84bf-ac1658eb24f7` 与 `ca0aa33a-c2e2-4a08-b333-3aec7a8532ef`；负计数返回 SOAP Fault `NEGATIVE_COUNT`；停止服务后客户端报告不可用并以预期非零码退出。Spring Boot 原有测试在包含 CXF 依赖的完整 Maven 测试中一并通过。

## 统计语义与边界测试

业务计数和不同 SQL 范围见 [架构与实现说明](architecture-and-implementation.md)。比率字段使用 `floatingRegistrationToResidentRatio`、`activeKeyPopulationToResidentRatio`、`residentToActiveHouseholdRatio`，不表示总人口构成百分比。计算器测试覆盖比率大于 1、分母为零、负计数拒绝、`Long.MAX_VALUE` 边界及避免整数乘法溢出。

Spring 适配入口默认关闭；启用时仅在当前授权请求范围内从原有统计服务获取汇总，远程请求不携带个人实体信息。集成测试覆盖默认关闭、认证/权限及 H2 统计适配路径。

## 审计账本 #78：真实 XLSX 检查

新增定向测试两次调用真实导出服务生成 XLSX，再用 Apache POI 重新打开工作簿检查 22 个输入值。每份文件的数据格均为 `STRING`，工作簿公式格数量为 0，读回值符合现有转义行为。没有复现公式单元格或公式执行，因此没有修改导出生产代码。

这项结果只描述生成文件的 OOXML/POI 检查，不证明 Microsoft Excel、LibreOffice、第三方导入器、CSV 转换或后续去空白/类型转换链的所有消费方式均安全。前导空白绕过当前前缀转义检查仍是静态缺口；应在已确定受支持的消费端上验证后再决定是否需要最小生产修复。审计账本 #78 状态未被修改。

## 可复现命令

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
.\mvnw.cmd dependency:tree "-Dincludes=org.apache.cxf:*,org.eclipse.jetty:*"
powershell -ExecutionPolicy Bypass -File scripts\windows\s2-demo.ps1
```

跨进程演示脚本在 `target/s2-demo-logs/20261103-220000-055` 写入本轮进程日志；该目录位于 Git 忽略的 `target` 下。脚本退出后只停止本次启动的子进程。复核时 1199、1200、8091 均无监听套接字，也没有残留的 S2 RMI/SOAP Java 服务进程。
