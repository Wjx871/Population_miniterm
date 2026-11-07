# 邑循 #70：统计面板操作日志读取侧脱敏

状态：`CODE_CANDIDATE`，等待 ChatGPT 独立复核。本轮仅处理 #70，不自动关闭审计。

## 基线与交付范围

- 精确父基线：`8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2`，来自个人 `integration/20261101-backend-p0-security`。
- 分支：`fix/20261107-issue70-statistics-log-redaction`。
- 实现及测试提交：`4a188f0b7231279a7d7cf453eb16a13af007b434`；后续提交补充本文、脱敏证据、账本 #70 及文件末尾格式，不改变生产或测试逻辑。
- 最终施工目录：`C:\Users\jxgm\.codex\worktrees\issue70-statistics-log-redaction-2963\小学期-integration-20261101-backend-p0-security`。
- 管理工具长时间未返回期间，先在另一个精确基线 worktree 完成 RED、修复和专项测试。工具延迟完成后，将实现提交的分支切到托管目录运行全量测试。先前目录 `C:\Users\jxgm\.codex\worktrees\issue70-statistics-log-redaction\小学期` 保留为 detached HEAD，保存原始日志，不执行清理。
- #43 worktree、Git 登记、临时分支及目录锁均未操作；#43 已集成但清理仍未完成。本轮未修改其状态，不宣称 `CLEANUP_COMPLETED`。
- 账本仅更新 #70；#33、#41、#45、#64 保持 `OPEN`。

## 成因及两条读取链路

| 读取链路 | 基线行为 | 本轮结果 |
| --- | --- | --- |
| `GET /api/logs/operations`、`GET /api/logs/operations/{id}` | `LogQueryService.search()/get()` 经 `mask()` 对 detail、errorMessage 调用 `auditDetail()` | 保留原逻辑，HTTP 对照通过 |
| `GET /api/statistics/logs` | `StatisticsMapper.selectRecentLogs()` 读取 `CAST(l.detail AS CHAR) AS detail`，`StatisticsService.getRecentLogs()` 只经 `safeRows()` 返回 | 在 Service 响应转换边界统一调用同一个 `auditDetail()` |

写入侧即使已经脱敏，也无法保证旧记录或其他写入来源没有原始敏感值。只通过权限检查和数据范围过滤，不能替代响应 detail 的固定脱敏。

生产改动仅限 `StatisticsService`：注入现有脱敏服务，复制每一行响应 Map 后转换 detail。SQL、Mapper、Controller、日志写入、审计事务与历史数据库内容均未修改。

## 先 RED、再 GREEN

全部合成历史记录使用直接 `JdbcTemplate` INSERT，故意绕过写入侧服务。只使用虚构测试值；测试禁止 MockMvc 输出完整请求/响应，断言和证据只输出安全布尔量及已经脱敏的结果。

### H2 方言差异及复现有效性

首轮直接使用原 SQL 的 H2 HTTP 测试没有返回完整原值。进一步通过 JDBC 动态检查确认：H2 2.3.232 的无长度 `CHAR` 默认为 1 个字符，CAST 会截断；这不是 Spring 或应用脱敏机制。MySQL 的无长度 `CAST(... AS CHAR)` 按表达式推导长度，语义不同。

依据：[H2 CHARACTER 文档](https://h2database.com/html/datatypes.html#character)、[MySQL 8.0 CAST 文档](https://dev.mysql.com/doc/refman/8.0/en/cast-functions.html)。

独立 Issue70 测试内嵌 `FullTextCastInterceptor`，仅在测试中将完整匹配的投影 `CAST(l.detail AS CHAR) AS detail` 改成 H2 的 `CAST(l.detail AS VARCHAR) AS detail`，使 detail 完整返回。其余 SQL（DataScope 条件、绑定参数、JOIN、排序、LIMIT）保持原样。生产 SQL 文件没有任何变化；Service、Mapper、JdbcTemplate、认证、鉴权均为真实 Spring Bean，无 Mock 的日志数据返回。该适配不等于真实 MySQL 验证。

### 实际执行记录

| 阶段 | 实际结果 |
| --- | --- |
| 首轮原样 H2 | 1 项、1 失败；文本被截为一个字符，不能作为完整敏感值泄露证据 |
| 有效 RED，2026-11-07 21:33:03 +08:00 | 生产目录与精确基线无差异；真实测试用户 HTTP 200、7 行，`rawReturned=true`、`redacted=false`、`databaseUnchanged=true`；1 项、1 预期失败，错误/跳过为 0 |
| 修复后首轮专项 | 40 项、3 个测试预期/夹具失败：共享 H2 schema 未种子化 `statistics:view`；空 REGION 在现有 `DataScopeCriteria.current()` 已拒绝 403。修正仅限独立测试夹具和预期，未改生产权限、范围逻辑 |
| 最终专项，2026-11-07 21:36:16 +08:00 | 40 项，失败/错误/跳过均 0，`BUILD SUCCESS` |
| 最终全量 | 2026-11-07 21:39:47 +08:00，810 项（原基线 797 + 本轮 13），失败/错误/跳过均 0，71 个测试报告，Maven 退出码 0，耗时 2 分 44 秒，`BUILD SUCCESS` |

有效 RED 的 HTTP 返回原值核对在脱敏断言前完成，且检查数据库未改写；该轮没有生产修改。后续测试夹具修正未改变历史敏感数据与读取侧断言。完整原始运行日志位于最终 worktree 的 `target/issue70-evidence/`，原始目录也保留首轮日志；提交中的 [脱敏证据](issue70-statistics-log-redaction-evidence.txt) 保存摘要与日志 SHA256。

## 最终规则与敏感内容证据

复用 `SensitiveDataMaskingService.auditDetail()`，不引入正则，不根据 `sensitive-data:view-full` 放行。普通日志用户和拥有完整敏感权限的真实管理员均通过相同 HTTP 断言。

| 测试类别 | HTTP 中的预期值或断言 |
| --- | --- |
| 模拟身份证号 | `id=1101**********0016` |
| 模拟手机号 | `phone=138****8000` |
| Bearer 测试凭据 | `Bearer ***` |
| password 测试字段 | `password=***` |
| 普通业务描述 | `合成历史记录：办理人口登记完成`，与原值相等 |
| SQL NULL | 响应 `detail: null` |
| 已正确脱敏的组合文本 | 响应与原脱敏字符串完全相等 |

上述仅展示脱敏结果，不记录原始身份号码、电话、凭据或口令。历史详情 HTTP 测试对全部非空行逐一比对预期值，对 NULL 行单独核对。

- 输入列表为 null 时返回空列表；跳过 null 行。
- Map 中 detail 缺失或 null 时响应明确为 null；SQL CAST 正常返回 String，非 String 异常值也安全返回 null。
- 普通、空字符串和已脱敏字符串保持正确。
- 不原地修改 Mapper 返回行，支持不可变 Map，其他所有键值复制保留。
- 基线 MyBatis 对 SQL NULL 的 Map 项会省略，本轮仅将 detail 明确补为 null，以满足接口空值约定。

## HTTP、DataScope、结构与数据库验证

独立类：`Issue70StatisticsLogRedactionIntegrationTest`，共 11 项实际执行用例（含 4 个参数化 scope），使用 `SpringBootTest + MockMvc + 真实 JWT/Spring Security + 真实 AuthService/JdbcTemplate/MyBatis + 隔离 H2`。JWT 由真实用户加载和 JwtService 签发，HTTP 过滤器再次从数据库加载身份，未使用伪造 SecurityContext 或 Mock 用户。每个用例事务回滚，仅在本类隔离数据库创建自己的用户、角色、部门与权限，不修改共享测试 schema。

| 验收项 | 证据 |
| --- | --- |
| 拥有 log:view | 普通真实用户与管理员均返回 200，固定脱敏成功 |
| 无 log:view，仍有完整敏感权限 | 返回 403；statistics:view 仍可读取 summary |
| 匿名 | 返回 401 |
| SELF | 仅当前用户 1 条，且脱敏 |
| DEPARTMENT | 当前部门 2 条，排除同区异部门和外区，且脱敏 |
| REGION | 当前授权区划内两个部门共 3 条，排除外区，且脱敏 |
| ALL | 全部 4 条，且脱敏 |
| 空 REGION 授权集合 | 保留既有 403，未越过范围校验 |
| 数据库不变 | 插入后与 HTTP 读取后的 log_id/detail 全量 Map 完全相等；普通用户和管理员均通过 |
| /api/logs 不退化 | 普通用户、管理员的 operations 搜索及逐条详情均按原规则脱敏；数据库原值未变 |
| summary/charts | 实际 HTTP 字段存在，日志读取前后 data 完全一致；既有统计范围集成测试亦通过 |
| 字段兼容 | HTTP 核对 id/operator/type/targetTable/result/time/detail 对应字段及原值；Service 单测核对非 detail 键值全部保留及 Mapper Map 未被改写 |

H2 的未加引号 `targetTable` 别名实际映射成 `targettable`，本轮保留该既有 H2 行为；HTTP 测试据实核对小写键，单测另外确认生产 Map 的 camelCase `targetTable` 键也原样保留。生产 SELECT/字段别名没有变化，前端 API 文件未改。本轮未运行真实前端联调或浏览器测试。

最终专项命令：

```powershell
.\mvnw.cmd '-Dtest=Issue70StatisticsLogRedactionIntegrationTest,StatisticsScopeIntegrationTest,StatisticsServiceTest,AuthRbacIntegrationTest,SensitiveDataMaskingServiceTest' test
```

最终全量命令：

```powershell
.\mvnw.cmd test
```

## 环境、限制与保留策略

- Windows，本机 JDK 21.0.7，编译 release 17，Spring Boot 3.5.3，H2 2.3.232。未在 JDK 17 运行时、真实 MySQL、生产网络和真实前端验证。
- 真实 HTTP 在此指 MockMvc 经真实过滤器/Controller/Service/数据库链路，未开启网络监听端口。
- 规则范围与现有审计读取服务完全相同：识别既有格式的连续身份证号码、手机号、Bearer 凭据及 password/密码赋值；分隔号码、带引号 JSON 密钥、非 Bearer 凭据或自定义业务敏感字段等格式未新增覆盖。
- 本轮未对全仓所有公开读取路径进行审计；其他字段与读取入口保持原有规则，不能据此声称所有日志格式/途径已完成治理。
- 历史数据库不清洗、不回填；原值仅留在数据库。响应副本脱敏，保留日志写入和事务行为。
- 本轮全量 JVM/Maven 正常完成，未发生异常退出；完整全量日志保留在 `target/issue70-evidence/full-maven.log`。JaCoCo 在 Windows 中文路径下报告缺少执行数据，本轮不声明新增覆盖率结论。
- 远端 main、S3、Snapshot 及个人 integration 均按指定 SHA 校验；本地 main 原有 .gitignore 内容 SHA256 为 `8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`，本轮保留。
- 不合并 main/integration，不 force push，不创建 PR，不删除任何本轮或 #43 分支/worktree。最终保留 feature 工作区等待独立复核。
