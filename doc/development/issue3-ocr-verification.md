# #3 OCR 事务治理验证记录

## 环境和证据来源

- 基线：`88206ff7188f930e420a80142409ee40cc98bf57`。
- 分支：`fix/20261106-issue3-ocr-transaction-boundary`。
- 工作树：`C:\Users\jxgm\.codex\worktrees\issue3-ocr-transaction-boundary\小学期`。
- 本机 Oracle JDK 21.0.7；Maven 编译目标为 Java 17，没有声称在 JDK 17 运行时验证。
- 测试使用随机独立内存 H2（MySQL 模式）、受控 `127.0.0.1` HTTP Stub 及合成字节、合成人口信息。没有访问团队数据库、真实 OCR 服务或真实个人资料。
- 新测试不使用测试级 `@Transactional` 包裹请求；Spring 代理、真实 JDBC 事务及独立 HTTP 请求实际执行。
- 运行日志保存在工作树忽略目录 `target/issue3-evidence/`，测试报告在 `target/surefire-reports/`。仅本文件中的脱敏摘要进入 Git。

## 旧实现 RED：已在生产代码修改前运行

执行：

```powershell
.\mvnw.cmd '-Dtest=Issue3OcrTransactionIntegrationTest#ocrWaitHasNoSpringTransactionOrBorrowedConnection' test
```

实际结果：1 项，1 个预期断言失败，0 错误，0 跳过。请求在真实回环 HTTP Stub 中被锁存器阻塞时，原实现输出：

```text
ISSUE3_OCR_OBSERVATION springTransaction=true resourceBound=true activeConnections=1
[OCR caller has no actual Spring transaction]
Expecting value to be false but was true
```

RED 摘要及原测试报告分别保存在 `target/issue3-evidence/red-summary.txt`、`red-test.txt`；原运行日志为 `red-console.txt`。日志证明的是本地旧实现的事务占用，不以源代码注解推测代替动态证据。

## 新实现动态验证矩阵

| 要求 | 实际观测和断言 |
| --- | --- |
| OCR 不持事务、资源、连接 | 同一调用线程进入真实 HTTP 请求前，Spring actual transaction=false、DataSource resource=false；Stub 阻塞时 Hikari active connections=0 |
| OCR 不延长唯一键锁 | 第一请求 OCR 仍被阻塞时，独立第二请求以相同 SHA 跳过 OCR 并在超时窗口内成功提交；第一请求尚未结束 |
| 成功结果完整提交 | 实际数据库保存识别字段、强制掩码、文件引用；审计在可写事务中执行，完成回调为 COMMITTED |
| HTTP 错误、超时、非法响应 | 真实 HTTP 503、真实读超时、非法响应体/身份证/日期/置信度返回 FAILED；持久化结果与响应一致 |
| 显式 skipOcr | 返回 SKIPPED，远程请求计数为 0 |
| 同上传者草稿复用 | SUCCESS、FAILED、SKIPPED 各自复用；改变 skipOcr 也不覆盖原结果，后续 OCR 次数不增加 |
| 跨上传者及已绑定拒绝 | 返回 409，已有行及文件引用不变，不触发 OCR |
| 并发首次去重 | 两个请求允许都发起 OCR；最终一条记录、一次成功结果写入、一个新文件，两个合法调用返回同一胜者 |
| 并发失败方重新授权 | 不同上传者同时首次上传只有一个成功；另一个拒绝。胜者在失败方 OCR 期间被绑定后，失败方也拒绝 |
| OCR 未完成不能绑定半成品 | 阻塞期间查不到本请求草稿，猜测 imageId 不能创建人口；完成后身份证不一致继续拒绝 |
| 数据库、审计失败 | 实际插入过的审计与影印本共同回滚，完成状态 ROLLED_BACK；本请求文件被补偿，已有文件不受影响 |
| 提交前/提交后失败 | beforeCommit 失败回滚并补偿；afterCommit 失败返回错误而保留已提交行及其文件 |
| 提交确认丢失 | 在真实 JDBC Connection.commit 上注入确认丢失，分别实际 commit 和实际 rollback；两者完成状态都为 UNKNOWN，文件都保留 |
| 文件失败与补偿 | 部分写入和输入流关闭失败不留下正常可补偿孤儿；同 SHA 路径独立。删除失败明确返回补偿失败，保留本请求文件并输出随机定位名 |
| 外部事务 | NEVER 在保存文件和 OCR 前拒绝调用者已有事务 |
| 脱敏与兼容 | 原 Phase14 上传/详情/跨用户/绑定测试和原 RBAC、人口归属、核心事务回归；错误响应不含注入的 SQL、审计、文件异常敏感正文 |

`Issue3OwnedFileCompensationTest` 负责部分写入、输入流关闭及同 SHA 文件所有权故障；`Issue3CommitOutcomeIntegrationTest` 负责提交确认丢失；其余新场景位于 `Issue3OcrTransactionIntegrationTest`。

文件删除失败及已知实际回滚的提交故障测试中，测试夹具在断言后会根据自己注入的确定故障事实，核实无引用并清理自己生成的文件。这是测试清理，不是应用具备持久化自动恢复机制的证据。

## 回归结果

定向命令覆盖新故障/并发测试、Phase14 两组测试、Phase08 人口身份证、人口归属、Auth RBAC、核心事务：

```powershell
.\mvnw.cmd '-Dtest=Issue3CommitOutcomeIntegrationTest,Issue3OcrTransactionIntegrationTest,Issue3OwnedFileCompensationTest,Phase14PersonIdCardOcrIntegrationTest,Phase14PersonIdCardImageIntegrationTest,Phase08PersonIdentityIntegrationTest,PersonRegionOwnershipAuditIntegrationTest,AuthRbacIntegrationTest,CoreTransactionBoundaryIntegrationTest' test
```

实际结果：**93 项，0 失败，0 错误，0 跳过，BUILD SUCCESS**，耗时 2 分 25 秒。该轮后补充的文件保存异常脱敏测试将由最终完整回归覆盖。

定向日志中动态观测为：

```text
ISSUE3_OCR_OBSERVATION springTransaction=false resourceBound=false activeConnections=0
```

扫描该轮运行输出，未发现注入的 SQL/审计/文件敏感异常正文、合成私密地址和完整合成身份证号。响应和数据库审计字段另有测试断言。

最后执行原要求的完整命令：

```powershell
.\mvnw.cmd test
```

实际结果：**770 项，0 失败，0 错误，0 跳过，BUILD SUCCESS**，耗时 **6 分 31 秒**，完成时间为 **2026-11-06 23:40:43 +08:00**。相对历史基线 736 项新增 34 项：OCR 事务/并发集成 29 项、提交确认丢失 2 项、文件补偿 3 项。后补充的文件保存异常脱敏测试已包含在完整回归中。

最终控制台记录为 `target/issue3-evidence/full-console.txt`；按本轮开始时间筛选 Surefire XML 汇总，排除以前定向运行的陈旧报告，汇总保存为 `full-summary.json`。本轮完整日志中也未检出注入的敏感异常正文及合成私密地址标记。JaCoCo 输出提示没有执行数据，因此本轮不声称生成了覆盖率报告。

## 变更文件

生产代码仅涉及 OCR、文件保存及人口绑定所需的 9 个文件：

```text
src/main/java/com/wjx871/population/material/LocalFileStorageService.java
src/main/java/com/wjx871/population/person/PersonService.java
src/main/java/com/wjx871/population/person/idcard/IdCardImageTransactions.java
src/main/java/com/wjx871/population/person/idcard/IdCardOcrProperties.java
src/main/java/com/wjx871/population/person/idcard/OcrLogRedactor.java
src/main/java/com/wjx871/population/person/idcard/PaddleOcrHttpClient.java
src/main/java/com/wjx871/population/person/idcard/PersonIdCardImageMapper.java
src/main/java/com/wjx871/population/person/idcard/PersonIdCardImageService.java
src/main/resources/mapper/PersonIdCardImageMapper.xml
```

新增 3 个测试文件及本任务 2 份文档：

```text
src/test/java/com/wjx871/population/material/Issue3OwnedFileCompensationTest.java
src/test/java/com/wjx871/population/person/idcard/Issue3CommitOutcomeIntegrationTest.java
src/test/java/com/wjx871/population/person/idcard/Issue3OcrTransactionIntegrationTest.java
doc/development/issue3-ocr-transaction-boundary.md
doc/development/issue3-ocr-verification.md
```

## 限制与结项门槛

- 不保证并发首次上传只执行一次 OCR；保证的是数据库唯一记录、胜者结果不被覆盖、胜者文件不被误删，以及已有草稿的后续上传不再 OCR。
- 正常、确定回滚的异常路径可补偿；进程崩溃、断电、提交结果不明及文件补偿自身失败仍可能留下未引用文件。没有持久化恢复机制，不宣称这些风险完全解决，也不猜测删除。
- 没有验证实际 MySQL 锁、隔离级别和驱动网络故障行为；H2 故障注入只能证明本地 Spring/JDBC 边界与补偿决策。没有验证真实 PaddleOCR、部署网络、混合版本部署、前端联调、RabbitMQ 或团队环境。
- 全部本地测试及保护核验通过、提交并仅推送指定分支后，#3 才可标记 CODE_CANDIDATE；仍等待 ChatGPT 独立复核，不能标记 FIXED_VERIFIED。
