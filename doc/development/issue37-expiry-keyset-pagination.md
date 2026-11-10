# #37 到期任务有界主键扫描

施工基线：`3268a831ffc83948c686e1afd7bc7cae35d11e2e`。
分支：`fix/20261110-issue37-expiry-keyset-pagination`。
独立 worktree：`C:\Users\jxgm\.codex\worktrees\issue37-expiry-keyset-pagination\小学期`。
本轮状态为 CODE_CANDIDATE，等待 ChatGPT 独立复核。本文记录实际验收结果，不表示已合并或部署。

## 根因与 RED

原 `runNow()` 分别一次读取所有过期居住证和流动登记，查询包含人员信息等完整对象列，没有 LIMIT；两个 List 在调用期间保留。Worker 只重新检查 ACTIVE 状态，候选读取之后 valid_until 或 planned_leave_date 被延长时仍会误处理。

先保持全部生产代码为指定基线，新增 MyBatis Executor 查询拦截器，记录真实 statement、SQL 和返回集合大小。批次目标为 3，合成 11 条（超过三批）。旧实现实际一次返回 11 个完整对象，边界断言失败；日期更新后的误过期和误关闭也被复现。未估算或编造堆内存。

首轮 H2：52 项，7 项失败、0 错误；其中既有 Phase05 的 40 项全部通过，包含失效幂等、历史保留、Worker 回滚等原行为。原始日志和 Surefire 报告已独立封存。

MySQL RED：8.4.8、127.0.0.1:3809、REPEATABLE-READ、独立 schema 与非 root 账号。13 项，8 项失败、0 错误；两个旧候选 SQL 分别一次返回 10,000 个完整对象。该运行的新 launcher 在复制报告时遇到报告目录缺失，数据库已正常关闭；Maven 原始日志、报告和 datadir 已保留，报告另行补存。后续 launcher 补充了复制前的目录检查。

## 批次、配置、日期和返回值

`residence-permit.expiry-batch-size` 默认 256，允许 1–1000（含边界），绑定越界配置直接失败。cron 和 `scheduled()` 入口不变。

每次 `runNow()` 只调用一次 today()，把该 LocalDate 传给所有分页查询与 Worker。日期条件仍为严格 `< today`，当天到期的记录保留。

两类查询分别读取 `List<Long>`，SQL 为 `id > afterId ORDER BY id LIMIT batchSize`，同时限定 ACTIVE、current_flag=1 和过期日期。没有 JOIN、完整对象和 OFFSET。每类游标从 0 开始，以最后一个候选主键推进，Worker 因竞争跳过记录也不影响推进；空结果立即停止，不足一批后结束。两个扫描顺序仍为 permit 后 floating。

`runNow()` 返回本次实际访问的候选数之和，不表示成功更新数。候选日期在加锁前延长时仍计 1、Worker 更新为 0；已 EXPIRED/CANCELLED 和非当前记录不再作为候选读取。相比旧版，这会排除过去被计数但 UPDATE 必然失败的非当前 ACTIVE 历史行。这是候选资格收紧，未把返回值改成成功数。

候选扫描不包裹长事务。逐条 Worker 仍由独立 Spring bean 代理执行 REQUIRES_NEW。失败继续向调用者传播，本轮中断，先前记录的独立事务保留，失败记录的状态和日志全部回滚；下次运行从 0 扫描剩余记录，没有内部失败重试循环。

## 锁后重验、日志与 #32

permit Worker 先只读取得单个 floating_id，锁该登记/person，再锁 permit，最后检查关联 ID 未改变、ACTIVE、current_flag=1、非空 valid_until 严格早于冻结日期。关联 ID 的初次读取不是资格依据，锁后对象才是最终依据；登记状态本身不被新增为 permit 到期条件，保留原业务资格语义。

真实 MySQL 并发验证发现：原 permit-only Worker 清除 current_flag 时会产生隐式 FK person S 锁。`performance_schema` 实际观测到 `person PRIMARY S,REC_NOT_GAP` 等待，InnoDB 死锁图显示到期事务持 permit X 等 person S，而关闭事务持 person X 等 permit X。保留原 Worker 锁路径即使实现分页仍会死锁。额外锁顺序 RED 运行 1 项、1 错误，并记录了真实 MySQL 1213；不是由超时推断死锁。

因此本轮把到期 Worker 的锁顺序统一到既有登记/person → permit。该调整在已批准的 service/Mapper 文件内完成，没有修改人工关闭、签注或其他审计项。MySQL GREEN 探针将观测真实父记录等待，并断言等待时该扫描事务持有的 permit X 记录锁为 0。

floating Worker 仍先调用现有 lockFloating（登记/person），随后检查 ACTIVE、current_flag=1、非空 planned_leave_date 严格早于冻结日期；关闭成功后按原规则将关联的当前有效居住证置 EXPIRED，并写 REGISTRATION_CLOSED。这条关联失效规则不要求证件自身已到期，与旧版一致。

保留现有 version 条件更新、EXPIRE/REGISTRATION_CLOSED 轨迹和系统操作日志，以及 floating/person → permit 顺序。没有新增 permit → floating 的锁依赖，也没有更改人工关闭/取消/签注服务。在受控交错中人工关闭先持父锁，扫描等待，释放关闭后关闭返回 200，扫描锁后见 CANCELLED 而跳过，不新增 EXPIRE 日志。

## H2 GREEN

初次 GREEN：62 项全部通过（新增集成 18、配置绑定 4、既有 Phase05 40）。

覆盖 0/2/3/4/11 候选、两类分页、候选过滤、重复运行、候选日期或 current_flag 改变、跨午夜日期冻结、逐记录回滚和重跑、双线程扫描、真实签注接口在候选读后提交、人工关闭与扫描交错、历史和日志保留。双扫描最终使用 barrier 确保两个线程先读到相同首批，再一起执行 Worker；不会仅凭提交两个 Future 就声称发生重复候选竞争。

大数据：每类 10,000 个候选，共 20,000 次 Worker 访问；batch=3，共 6,668 次候选查询，单次结果集峰值 3。H2 首次实测扫描耗时 4,464 ms（不含 fixture 插入），重跑返回 0。该时间仅代表本机合成数据运行，不表示生产容量。

## MySQL 执行计划和索引评估

基线已有 permit `(issue_region_code,status,valid_until)`、floating `(current_region_code,status)` 等二级索引，都以 region 开头。新增全局扫描没有 region 条件。

在真实 MySQL 8.4.8 的每类 10,000 条候选上，对首部、中部、尾部三个游标执行拟采用的真实 keyset SQL EXPLAIN：两表均 `type=range,key=PRIMARY,key_len=8,Extra=Using where`，无 filesort。首/中游标 rows 估计约 5,000，尾游标为 1。rows 是优化器估算的可能检查行数，不是返回行数；LIMIT 只保证结果集有界。

当前证据支持利用已有主键顺序完整遍历，尚未证明新增索引必要，因此没有修改 schema、canonical SQL 或已发布迁移，尤其没有改 V4_019。对极大且大部分未到期的表，主键过滤仍可能检查大量行；本轮不把有界结果读取宣称为所有分布下的索引最优方案。

## 最终验收记录

- MySQL 最终 GREEN：18 项 #37 全部通过，另行运行的 #32 真实并发门禁 1 项通过；全部为 MySQL 8.4.8、非 root 应用账号、REPEATABLE-READ。实际父锁等待为 `floating_population PRIMARY X,REC_NOT_GAP`，等待时持有的 permit X 记录锁为 0，测试期间 MySQL 1213 计数不增加。
- 最终 MySQL 大数据：每类 10,000 条候选，合计 20,000 条，6,668 次查询，单次集合峰值 3，扫描耗时 109,029 ms（不含插入），再次运行返回 0。没有遗漏或重复成功转换；LIMIT 仍为 3，不是一次加载 10,000 条。
- 两个扫描线程经 barrier 先读到相同首批；本次各自返回 22 个访问候选，实际每类仅 11 次成功状态转换。这说明候选数不能解释为成功数。
- 全量 `mvnw.cmd test`：81 套件、965 项，0 失败、0 错误、4 跳过（实际执行 961 项），BUILD SUCCESS，耗时 03:22。跳过的是已有 Issue34 RED 3 项和需显式启用的万条测试 1 项；万条测试已在定向 H2/最终 MySQL 中执行通过。只汇总本轮时间戳之后的 Surefire XML，计数与 Maven 最终汇总一致，未混入旧 MySQL/RED XML。
- 相关完整回归：Phase05 40、#34 GREEN 35、#15 24、#19 13、#22 6、CoreTransactionBoundary 14，全部 0 失败/0 错误。
- diff --check 与受保护引用复核通过。提交和推送仅针对指定 feature；精确交付 SHA 以最终交付消息及该远端 feature HEAD 为准，避免文档自引用提交哈希。
- 审计账本仅将 #37 更新为 CODE_CANDIDATE，其他项目的状态和文本均保持不变。

## 原始证据位置

- H2 RED/GREEN：`C:\Users\jxgm\AppData\Local\Temp\population_issue37_audit_df94ba27bb3c4f59a50fc151b2fcb88e`。
- MySQL RED：`C:\Users\jxgm\AppData\Local\Temp\population_mysql84_issue37_20261110T050601190Z_77129cb6d05949748430b3bc7bdcd139`，datadir 和原始合成数据现场保留。
- 隐式反向父锁 RED：`C:\Users\jxgm\AppData\Local\Temp\population_mysql84_issue37_20261110T052335573Z_8e1eff659a2045f688953847c3af5557`，包含 performance_schema 等待、真实死锁图和失败 Surefire 报告。
- 最终 MySQL GREEN：`C:\Users\jxgm\AppData\Local\Temp\population_mysql84_issue37_20261110T052518664Z_3f44735e2e054a7aad01ccc060c0778f`。两个测试选择均成功，实例 PID 39452 已正常关闭，datadir 保留。
- 新增 probe 会导出只含合成 ID、日期和状态的 CSV 快照，路径记录在运行日志中。
- 大型原始日志、Surefire XML、数据库目录和随机凭据不提交 Git；仓库保留脱敏摘要和 `issue37-evidence-sha256.tsv` 的 204 个证据文件校验项，包括全量 Surefire 压缩包、CSV 和已关闭 datadir 中的合成表文件。

本轮不涉及 #3、#33 或其他审计项的状态变更；不合并 integration/main，不清理任何 worktree。

扫描冻结的是业务日期，并非跨批次的数据库快照。已越过游标的记录若在并发修改后才变得符合资格，由下一次 runNow() 处理；持续新增的高主键候选也不属于本轮固定总量保证。结果集上限不等于整个 JVM 堆使用上限，未对堆占用作数值承诺。

## 验收映射

| 要求 | 本轮证据 |
|---|---|
| A–E 零、短页、整页、多一条、三页以上 | `permitPagesAreBoundedAndComplete`：0/2/3/4/11，floating 11，另有双类各 10,000 |
| F 每次查询有界 | MyBatis Executor 拦截实际结果大小、实际 SQL，3 为峰值 |
| G/H 幂等、终态过滤 | 同日重跑、EXPIRED/CANCELLED 与 current_flag=NULL fixture |
| I 签注延长 | 日期直接变更，以及真实审批后签注接口在候选读取后提交，证件保持 ACTIVE，仅 ENDORSE 日志 |
| J 计划离开日期变更 | 候选读取后把 planned_leave_date 延长，登记及关联证件保持 ACTIVE |
| K 历史标记 | 查询过滤与候选读取后 current_flag=NULL 两个窗口 |
| L/M 逐条回滚、重跑 | 第一条成功，第二条 hook 抛错；仅第一条状态/轨迹/操作日志提交；重跑完成其余三条 |
| N 双实例等价并发 | 两线程同首批 barrier，最终状态转换/轨迹不重复，MySQL 1213 不增加 |
| O 与关闭/签注交错 | 真实关闭持父锁，performance_schema 证实安全等待；真实签注候选窗口；原 #32 MySQL 门禁通过 |
| P 操作日志与轨迹 | EXPIRE/REGISTRATION_CLOSED、两种系统操作日志数量，以及失败回滚后的数量 |
| Q #32/#34/#15/#19/#22 | #32 MySQL 门禁；全量 Maven 中 Phase05 40、#34 GREEN 35、#15 24、#19 13、#22 6 均通过 |

## 保护引用核对

下列远端引用在施工前及交付前只读复核均一致，本轮只允许推送新的 feature。

| 引用 | 前后相同 SHA |
|---|---|
| integration/20261101-backend-p0-security | `3268a831ffc83948c686e1afd7bc7cae35d11e2e` |
| main（远端） | `b54579b6b7f8b1feeda60f43906da05fb2ffda99` |
| S3 | `758014cbcccdfe820efb2a6fae73bc3168030484` |
| Snapshot | `d6b0b87b74af7a413214d7da9dd30b075cec882b` |
| #43 | `8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2` |
| #81/#83 | `f5c3a959cf99f69fed8e8640d845a99b23428563` |

本地 main 开始即为 `d7fc1192967a13c7d80d2b60fdc12749fd5218df`，与远端 main 不同，本轮保持该原有状态；原工作区 `.gitignore` 修改以及 `.agent-teams/`、`.claude/` 未跟踪内容均保留。`.gitignore` SHA256 前后为 `8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`。

额外受保护 detached checkout `C:\Users\jxgm\.codex\worktrees\issue22-archive-rowcount\小学期` 仍保留在 `3f2730882583fdb0cfb3b5aafb50583ea1aecade`；没有删除、回收、reset、检出或 prune。现有 S3、#81/#83 和 integration worktree 均未操作。

## 变更文件

- `src/main/java/com/wjx871/population/floating/ResidencePermitExpiryService.java`：主键扫描、日期冻结、锁顺序与锁后重验。
- `src/main/java/com/wjx871/population/floating/ResidencePermitProperties.java`：默认批次和绑定校验。
- `src/main/java/com/wjx871/population/floating/FloatingResidenceMapper.java`：有界 ID 查询和单 ID 的关联查找。
- `src/main/resources/mapper/FloatingResidenceMapper.xml`：对应 SQL，无新增索引/迁移。
- `src/test/java/com/wjx871/population/floating/Issue37ExpiryBatchIntegrationTest.java`：边界、资格变更、幂等、并发、事务、万条规模和 SQL 探针。
- `src/test/java/com/wjx871/population/floating/Issue37ExpiryPropertiesTest.java`：默认值、边界和非法绑定。
- `src/test/java/com/wjx871/population/floating/Issue37MySql84IT.java`：私有 MySQL 保护、身份/隔离验证、真实锁等待和 1213 计数。
- `scripts/windows/tests/issue37-mysql84-gate.ps1`：创建/关闭本轮私有实例及 schema，原始日志留在受控临时目录。
- `doc/testing/2026-11-02-antigravity-audit-ledger.md`：仅 #37。
- 本说明及 `doc/development/issue37-evidence-sha256.tsv`：脱敏摘要和原始证据校验清单。

本轮实际使用 Java 21.0.7；未验证 JDK 17、生产部署、团队数据库或超出本轮合成数据分布的性能。所有临时 MySQL 实例均由 launcher 创建并关闭，datadir/日志保留，不执行目录清理。

## 配置和复验命令

配置示例（不设置时采用默认值）：

```yaml
residence-permit:
  expiry-batch-size: 256
```

H2 定向测试及可选万条 fixture：

```powershell
.\mvnw.cmd '-Dtest=Issue37ExpiryBatchIntegrationTest,Issue37ExpiryPropertiesTest,Phase05FloatingResidenceIntegrationTest' '-Dissue37.large=true' test
```

真实 MySQL 门禁会自行创建全新私有实例、独立 schema 和非 root 账号，验证后关闭服务并保留 datadir；不会连接已有 3306 或团队数据库。

```powershell
.\scripts\windows\tests\issue37-mysql84-gate.ps1 -TestSelection 'Issue37MySql84IT,MySql84CriticalAcceptanceIT#closeThenEndorseLocksFloatingBeforePermitWithoutDeadlock'
```

全量回归：

```powershell
.\mvnw.cmd test
```
