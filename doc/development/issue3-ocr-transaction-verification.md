# 审计 #3：OCR 事务与数据库资源独立验证

- 日期：2026-11-10（Asia/Shanghai）。任务类型：EXISTING FIX VERIFICATION FIRST。
- 判定：**EXISTING_FIX_CONFIRMED**。未发现本审计范围内的当前生产缺陷；本轮生产代码修改为零。
- 精确基线：`aec60c4f4536144851d4156b8918ef12d46af252`。创建前以 `git ls-remote` 核对远端个人 integration 完全一致。
- 历史修复：`a1cff942f7f9d7370ad7e4858133435ca2b70115`，已确认是基线祖先。
- Feature：`fix/20261110-issue3-ocr-tx-verification`。
- 独立 worktree：`C:\Users\jxgm\.codex\worktrees\issue3-ocr-tx-verification\小学期`（桌面 managed worktree 工具创建的位置，未使用或更改历史 worktree）。
- 审计账本仅 #3 改为 CODE_CANDIDATE，明确为历史修复验证；等待 ChatGPT 独立复核。没有标记 FIXED_VERIFIED，没有合并或快进 integration/main。

## 1. 原始风险与判定边界

原问题是远程 OCR 等待是否延长数据库事务、占用借用连接，或持有业务行锁。必须分开判断：

| 项 | 本轮观测 | 含义 |
|---|---|---|
| A 同步 HTTP 请求时间 | 约 1.4 秒受控等待、约 5.1 秒真实读超时 | 请求线程仍同步等待，接口延迟确实存在 |
| B 数据库事务时间 | OCR 前后同线程 Spring 实际事务 false；等待窗口数据库活动事务 0 | 没有把网络等待包含在数据库事务中 |
| C 数据库连接借用 | 等待窗口 Hikari active=0 | 没有长期借用连接；连接池可以保有空闲物理连接 |
| D InnoDB 锁 | 等待窗口 data_locks=0、data_lock_waits=0 | 没有跨 OCR 等待持有数据锁（包含行锁） |

A 不能推出 B/C/D。本轮没有制造当前版本“安全 RED”，也没有复现或改写历史工作区。首次 MySQL 功能夹具失败为缺少合成登录账号，不能计作原始漏洞的 RED。

## 2. 当前修复位置与事务边界

- `src/main/java/com/wjx871/population/person/idcard/PersonIdCardImageService.java`：`uploadAndRecognize` 使用 `Propagation.NEVER`，拒绝带事务调用；先保存独占随机文件，再调用 `transactions.reuse`；reuse 事务结束后才进入 `recognize` 和真实 HTTP。OCR 完成后调用独立 persist 事务。
- `IdCardImageTransactions.java`：局部 `DataSourceTransactionManager` + `TransactionTemplate(REQUIRES_NEW)`。reuse 的 `SELECT ... FOR UPDATE` 锁只覆盖复用检查与必要审计；persist 将草稿插入、最终 OCR 状态、必要审计放在同一个可写事务中。
- `PaddleOcrHttpClient.java`：同步 RestTemplate，连接/读取超时；HTTP 503 等映射为稳定错误码，远端响应体不进入异常内容。同步方式本身不构成数据库长事务。
- `src/main/java/com/wjx871/population/material/LocalFileStorageService.java`：随机文件名、CREATE_NEW 取得所有权、SHA 只用于业务去重；写入失败只能补偿当前调用创建的文件。

短事务可能因数据库自身竞争而等待；本轮结论限定为 OCR 网络等待没有拖住数据库资源，不宣称所有数据库操作在任意负载下都有固定时长上限。

## 3. 文件生命周期与提交完成结果

| 阶段/完成结果 | 数据库与审计 | 文件处理 |
|---|---|---|
| reuse 命中或拒绝、OCR 前异常 | 原行保持；reuse 的必要审计使用自己的短事务 | 只移除本请求额外创建的随机路径 |
| 明确 ROLLED_BACK | 新业务行、状态与已插入审计一起回滚 | 补偿本请求拥有的文件，不动原文件 |
| 明确 COMMITTED | 最终业务状态与必要审计提交 | 保留已提交文件，即使 afterCommit 随后抛异常 |
| commit ACK 丢失 / UNKNOWN | 应用无法确定物理提交是否发生 | 两种物理结果都保留文件；UNKNOWN 不能视为确定回滚 |

`Completion` 注册真实 Spring `afterCompletion`；只有未开始 persist 或明确 ROLLED_BACK 才允许丢弃。局部事务管理器不在 commit SQLException 后把一次 rollback 尝试解释为确定回滚。它不替换应用默认事务管理器。

已有 `Issue3CommitOutcomeIntegrationTest` 代理真实 JDBC Connection：分别在底层实际 commit/rollback 后抛合成 SQLException，验证 completion=UNKNOWN、数据库行真实存在与否、文件在异常时均保留。MySQL 入口另外检查必要审计与业务行数量一致。故障注入位于 JDBC 返回边界，不等同于真实 TCP 代理丢包；数据库物理提交/回滚和后续查询均真实。

## 4. 独立可观测实验

新增 `Issue3ResourceObservationIntegrationTest` 和 opt-in `Issue3ResourceMySql84IT`。OCR 服务只绑定 `127.0.0.1` 随机端口，使用 latch 控制等待；覆盖快速、慢响应、真实读超时和 HTTP 503。新观测上传使用 ImageIO 生成的 8×8 随机 PNG；返回的身份字段全部为合成测试值。历史专项继续使用其原有合成字节夹具。没有真实身份证图片，没有请求第三方 OCR。

`Phase14PersonIdCardOcrIntegrationTest` 原样保留：它使用绑定 RestTemplate 的 `MockRestServiceServer` 拦截 `http://ocr.test`，不实际解析或请求该域名。这一组用于功能/权限回归，真实网络时序证据来自上述 loopback 测试及原 Issue3 事务测试。

同一上传线程在 HTTP 调用之前/之后读取 `TransactionSynchronizationManager.isActualTransactionActive()` 和 `hasResource(dataSource)`。服务端确认收到 HTTP 且保持阻塞后，观察线程栈仍在 HTTP socket 读取之间，连续采集 Hikari 和 MySQL 状态。Java ThreadLocal 无法从观察线程直接跨线程读取，所以 Spring 状态是同线程前后边界探针；等待期间的资源结论另由 Hikari 和数据库独立观测佐证，并非只依据注解。

管理员 observer 使用独立 DriverManager 连接；查询均按本次非 root 应用账号的 `performance_schema.threads.PROCESSLIST_USER` 过滤，不混入管理员自身连接。SQL 形状在新观测测试源码中完整保留：

- `information_schema.innodb_trx` JOIN `performance_schema.threads`：未完成 InnoDB 事务。
- `performance_schema.events_transactions_current`：STATE='ACTIVE' 的应用事务。
- `performance_schema.data_locks`：应用线程当前数据锁。
- `performance_schema.data_lock_waits`：应用线程请求的锁等待。

私有实例开启 transaction instrument/current/history consumer。正向对照用应用账号开启事务并对合成 sys_user 行执行 FOR UPDATE，实际观察到：InnoDB 事务 **1**、活动事务 **1**、数据锁 **3**、锁等待 **0**；锁类型为 TABLE IX×1、RECORD X,REC_NOT_GAP×2。对照 rollback 后全部归零。这证明“零锁”结果来自可见探针。

MySQL 慢 OCR 两用户等待场景共 7 个样本（约 42–1437 ms），每个样本均为 Spring false、资源绑定 false、Hikari active **0**，上述四项数据库计数全 **0**。期间独立人员列表 HTTP 查询与 skipOcr 上传合计 **106 ms** 完成，两条慢 OCR 请求仍未完成。放行后两条结果均 SUCCESS、OCR elapsed **1425 ms**、文件仍存在，新增必要识别审计恰为 **2**。

MySQL 超时场景等待窗口另有 3 个零资源样本；超时前没有可绑定草稿，实际 elapsed **5101 ms**，之后提交恰一条 FAILED 状态和一条必要审计，错误码 OCR_UNAVAILABLE。503 为 OCR_HTTP_503，响应/数据库错误字段/审计均不含模拟敏感错误体；skipOcr 不发送 HTTP。

## 5. H2 与 MySQL 执行结果

| 执行 | 真实计数 | 结果 |
|---|---:|---|
| 修改前已有三组 #3 专项 | 39 | 0 失败 / 0 错误 / 0 跳过 |
| 新 H2 资源观测首跑 | 3 | 全部通过 |
| 最终相关 H2 专项（含 owned-file 三项） | 45 | 0 失败 / 0 错误 / 0 跳过 |
| MySQL 原 #3 事务/补偿流程 | 29 | 全部通过 |
| MySQL commit UNKNOWN 两种物理结果 | 2 | 全部通过 |
| MySQL 资源观测与持锁正向对照 | 4 | 全部通过 |
| MySQL Phase14 首跑 | 8 | 7 通过 / 1 夹具登录失败，非生产缺陷 |
| MySQL Phase14 补齐夹具重跑 | 8 | 0 失败 / 0 错误 / 0 跳过 |
| MySQL 加强审计一致性断言重跑 | 31 | 0 失败 / 0 错误 / 0 跳过 |
| 完整 `.\mvnw.cmd test` | 972（968 通过、4 跳过） | 0 失败 / 0 错误；BUILD SUCCESS |

不同 MySQL 场景去重合计为 **43**（29+2+4+8）；重跑不叠加到覆盖总数。初次 Phase14 因 canonical MySQL 种子没有 H2 中的 `approver-other` 返回登录 401；只在新 runner 中补齐独立外区合成账号，生产代码与旧测试都未改。

MySQL runner 为 `scripts/windows/tests/issue3-mysql84-gate.ps1`：始终创建新 datadir，随机非 3306 端口，独立 schema 和 schema-scoped DML 应用账号，REPEATABLE READ。runner 与新 Java opt-in 入口均拒绝非 loopback/默认端口/错误 schema 或 datadir/非 8.4/非 RR/root 应用账号。每个类使用独立 schema。管理员仅用于初始化与观察；未连接团队数据库。

首轮实例 MySQL **8.4.8 / 5470**，schema 前缀 `population_miniterm_mysql_gate_issue3_886978bb1f14`，应用账号 `i3_886978bb1f14`。Phase14 修正后重跑为 **8.4.8 / 11551**，schema `population_miniterm_mysql_gate_issue3_45628106efcf`。各轮 datadir 与身份、退出记录见 evidence；实例完成后正常关闭，临时 datadir 保留，未永久删除。

本轮实际 Java runtime 为 **21.0.7**、编译 release 17；没有声称原生 JDK 17 已验证。Maven/Surefire 实际汇总在 full-surefire-summary.json，未沿用历史 969。四个跳过来自原 #34 RED 三项及未启用 `issue37.large` 的一项万级测试；本轮没有新增跳过。JaCoCo 因缺少执行数据跳过报告，本轮不作覆盖率结论。

## 6. 并发、回滚与不确定提交门禁

| 要求 | 实际断言路径与结果 |
|---|---|
| 慢 OCR 不长期占用连接/业务锁 | 连续零资源样本；正向持锁对照有效 |
| 不同用户上传不阻塞无关业务 | 双用户真实 loopback OCR 等待；独立 HTTP 查询及 skip 上传及时完成 |
| 同文件并发唯一性 | 两请求都到 OCR 后并行放行，唯一键只提交一个图像结果；一条结果写入、一份文件；同用户读回同一 winner |
| 并发用户归属 | 不同 owner 同 SHA 最终单 200 / 单 409；不会读取另一 owner 的图像 |
| 超时/503/skip | FAILED 持久化与脱敏；skip 无 HTTP；超时前无草稿 |
| 必要审计失败 | 先真实插入审计再抛合成异常，afterCompletion=ROLLED_BACK；数据库业务行和审计一起回滚 |
| 仅补偿自有文件 | 插入失败、reuse 审计失败、重复上传等均保持其他文件；partial-write/close 失败另有 owned-file 三项 H2 测试 |
| 提交后异常 | afterCommit 注入异常，数据库仍有一条业务行，已提交文件仍存在 |
| UNKNOWN | 底层实际 commit 与 rollback 两分支均观察 UNKNOWN、异常发生时文件均保留；业务与必要审计共同有或共同无 |
| 无重复成功审计/孤立状态 | 并发 winner 一条识别成功审计，loser 仅有一次复用审计；明确回滚无新业务或审计，成功/UNKNOWN物理提交时业务和审计一致 |

Mockito spy 用于观测或可控故障注入，不替换业务 Service/Mapper/JDBC。完成结果还通过实际数据库查询、事务 synchronization 状态及文件系统断言核对，不以 mock 方法次数单独作为提交证据。

## 7. 修改范围与限制

本轮只新增验证测试、MySQL runner、文档和脱敏证据，并按用户单独授权仅更新审计账本 #3 一行。`src/main/` 和已有测试不修改。没有队列、状态机、常驻服务、新业务表或云 OCR 改造；#34 与其他审计行保持原样。

仍存在且未在本轮扩大处理的边界：同步 HTTP 占用请求线程；同文件并发可能请求 OCR 两次；进程崩溃、文件补偿失败和 UNKNOWN 保留可能留下待人工核对的文件，当前没有持久化恢复协议。UNKNOWN/物理回滚分支的额外保留是避免误删已提交文件的保守策略，不能据此声称绝无残留文件。测试只覆盖有限并发调度和受控故障，不是容量或真实 OCR 故障恢复认证。5 秒 timeout 的测试在极慢环境可能受调度影响。没有生产部署、真实第三方 OCR、原生 JDK17 或真实网络级 ACK 丢包验证。

## 8. 原始证据、复现与保护引用

原始证据根目录：`doc/development/evidence/issue3-verification/`。

- `TEST-*.xml`：修改前 39 项已有专项原始 Surefire 报告。
- `h2-resource-first.log`、`h2-final.log`、`h2-final-reports/`：新增观察与最终 45 项专项。
- `mysql-first/`：首轮全部 Maven 日志、schema 输入 SHA256、种子账号、身份和 Surefire（含如实保留的 Phase14 夹具失败）。
- `mysql-phase14-retry/`：修正夹具后的 8 项成功回归。
- `mysql-audit-final/`：加强实际成功审计/UNKNOWN审计一致性断言后的 31 项回归。
- `full-maven.log`、`full-surefire-summary.json`：本轮完整测试，统计仅取本轮更新的 XML，排除陈旧 opt-in MySQL 报告。
- `protected-remote-before.txt`、`protected-remote-final.txt`、`protected-local*.txt`：保护引用与主工作区状态。
- `source-sha256.tsv`：关键生产/测试/runner 文件 SHA256 和 Git blob；`sha256.tsv`：原始证据清单及 SHA256（不包含自身，避免循环）。清单本体 SHA256：`22707F4235E52F2306488767089A97D03252915DDCD2523498E95D71D3BF213B`。

证据目录的 `.gitattributes` 仅对原始 LOG/XML/TXT 禁止 Git 换行转换和尾空白检查，以保留日志原字节与 SHA256；源码和文档仍正常执行 whitespace 检查。证据不写入凭据、Bearer token、身份证原图或远端敏感响应体。生成的数据库身份及测试账号名称可以公开，密码只在私有进程环境中存在。所有 schema 都是空库初始化的独立测试 schema；文件补偿只作用于本次测试新建文件，未清理历史工作区。

复现命令：

```powershell
.\mvnw.cmd '-Dtest=Issue3OcrTransactionIntegrationTest,Issue3CommitOutcomeIntegrationTest,Phase14PersonIdCardOcrIntegrationTest,Issue3ResourceObservationIntegrationTest,Issue3OwnedFileCompensationTest' test
.\scripts\windows\tests\issue3-mysql84-gate.ps1
.\mvnw.cmd test
```

保护引用：

| 引用 | 本轮要求保持的完整 SHA |
|---|---|
| 远端 main | b54579b6b7f8b1feeda60f43906da05fb2ffda99 |
| 个人 integration | aec60c4f4536144851d4156b8918ef12d46af252 |
| S3 | 758014cbcccdfe820efb2a6fae73bc3168030484 |
| Snapshot | d6b0b87b74af7a413214d7da9dd30b075cec882b |
| #43 | 8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2 |
| #81/#83 | f5c3a959cf99f69fed8e8640d845a99b23428563 |
| 主工作区 HEAD | d7fc1192967a13c7d80d2b60fdc12749fd5218df |

主工作区 `.gitignore` SHA256 保持 `8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`；已有修改、`.agent-teams/`、`.claude/`、#22 detached checkout、#43 现场及历史 worktree 都保留。仅提交并推送新 feature；保留 worktree 等待独立审查。后续任何清理都需另行授权并使用 Windows 回收站。

最终判定：**EXISTING_FIX_CONFIRMED**。原始长事务风险已由历史实现解决，本轮用真实等待、连接池、MySQL 事务/锁和提交结果交叉验证确认；当前无需新增生产修复。CODE_CANDIDATE 待独立复核。