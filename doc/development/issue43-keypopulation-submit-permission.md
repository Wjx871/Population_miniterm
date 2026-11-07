# 邑循 #43：重点人口提交权限统一门禁

状态：`CODE_CANDIDATE`，等待独立复核；本轮只处理 #43，不自动结项。

## 基线与范围

- 精确基线：`a1cff942f7f9d7370ad7e4858133435ca2b70115`。
- 实现、测试及验证文档提交：`e96acbb2a9abf0abdd1e5b6bd57589efc9939123`（父提交即上述精确基线）；随后仅补充审计 #43 与本提交引用。
- 施工分支：`fix/20261107-issue43-keypopulation-submit-permission`。
- 独立 worktree：`C:\Users\jxgm\.codex\worktrees\issue43-keypopulation-submit\小学期`。
- 生产变更仅限 `src/main/java/com/wjx871/population/keypopulation/KeyPopulationWorkflow.java`。
- 不修改外部 API 的路径、请求或成功响应；缺权提交由原先可能成功变为 HTTP 403。
- 不修改 Controller 注解、DataScope SQL、材料规则、状态机、审批和执行规则；其他业务 validator 不变。
- #33、#41、#45、#64 保持 `OPEN`。

## 漏洞触发条件与入口差异

专用 REGISTER/RELEASE 提交路由同时以 `@PreAuthorize` 要求 `key-population:apply` 和 `application:submit`。通用 `POST /api/applications/{id}/submit` 只要求 `application:submit`，随后进入 `ApprovalService.submit()`。

旧版重点人口 validator 仅检查专业详情、人员 DataScope、`KEY_POPULATION_BASIS` 和 `SITUATION_DESCRIPTION` 必需材料。因此，合法申请人先有专项权限创建草稿、备齐材料，之后失去 `key-population:apply` 但保留 `application:submit`，仍可从通用路由提交 DRAFT 或重新提交 RETURNED。

## 服务端统一规则

`KeyPopulationWorkflow.supports()` 仅匹配 `KEY_POPULATION_REGISTER` 和 `KEY_POPULATION_RELEASE`。其 `validate()` 首先从 `CurrentUserContext.requireUser()` 取得真实认证主体，并同时检查两项权限；任一缺失即抛 `BusinessException(FORBIDDEN)`。

两个 HTTP 入口最终均调用 `ApprovalService.submit()`；直接 Service 调用同样经过 validator。授权检查位于人员/材料验证及申请状态更新、专业状态监听、审批单插入/重置、提交审批日志和成功操作日志之前。专用入口的原有两项权限注解仍保留，通用入口仍保留 `application:submit` 注解。

不读取请求体中的权限或角色，不按用户名授权。`JwtAuthenticationFilter` 每次请求调用真实 `AuthService.loadUser()` 从数据库重载权限；测试撤销权限后复用原 JWT，并用 `/api/auth/me` 证明实际加载结果已变化。恢复权限后，同一 JWT 和同一草稿能够正常提交。

## 动态验证设计

测试类：`Issue43KeyPopulationSubmitPermissionIntegrationTest`。使用真实 Spring Boot、Spring Security filter chain、MockMvc、AuthService/MyBatis 和随机独立 H2 数据库，无身份、权限加载或业务 Service 的 Mock。

独立普通角色 `ISSUE43_APPLICANT`（非 SYSTEM_ADMIN）和独立用户 `issue43-applicant` 在每个用例前建立，初始拥有两项提交权限及普通申请创建权限。草稿通过真实专项 HTTP 入口创建，材料以合成数据库行准备；材料提交规则不要求实际读取文件。初始 DataScope=ALL，申请人不变，避免以数据范围/owner/缺材料代替权限缺口证明。

RETURNED 用例经真实 submit → approver approve → household return 流程建立，不手工伪造申请状态。现有 return 仅将通用申请置 RETURNED，专业详情仍 APPROVED；本轮保留此既有行为，比较操作前后快照。

Service 用例通过真实 `AuthService.loadUser()` 取得数据库主体后绑定 SecurityContext，再直调 `ApprovalService.submit()`；此处没有 HTTP Controller 可代替业务授权。

覆盖矩阵包括两类申请的 DRAFT/RETURNED 撤权、双权限的通用/专用成功、任一权限缺失的 HTTP/Service 拒绝、两条路由的人员范围拒绝、原材料规则、权限恢复，以及不持专项权限的 GENERAL_SERVICE 通用提交。

| 场景 | 用例数 |
|---|---:|
| REGISTER/RELEASE DRAFT 撤销 apply，通用路由拒绝 | 2 |
| REGISTER/RELEASE 真实 RETURNED 撤销 apply，通用路由拒绝 | 2 |
| 双权限，两业务 × 两路由正常提交 | 4 |
| 撤销 apply，专用路由原有 RBAC 拒绝 | 2 |
| 缺 submit，两业务 × 两路由拒绝 | 4 |
| 直调 ApprovalService，两业务 × 分别缺失两权限拒绝 | 4 |
| 双权限仍受人员 DataScope 限制，两业务 × 两路由 | 4 |
| 两业务仍要求原必需材料 | 2 |
| 恢复 apply 后同 JWT、同草稿正常提交 | 2 |
| 无 apply 的 GENERAL_SERVICE 通用提交正常 | 1 |
| 合计 | 27 |

每个拒绝用例比较 11 张表的完整行（含状态、版本、时间戳、审批决策字段）：`business_application`、`key_population_application`、`sys_approval_request`、`sys_approval_log`、`application_material`、`key_population`、`key_population_history`、`person`、`residence`、`household` 及 `operation_log` 的成功记录。HTTP RBAC 拒绝正常产生的 `ACCESS_DENIED/FAILED` 日志保留，不视为成功业务副作用。RETURNED 审批单也必须整行不变，不能被 reset。

测试使用独立 Spring context；`@AfterEach` 只清理本类用户/人员所属业务数据、授权和本隔离数据库的本次日志，不修改共享测试角色，不删除磁盘文件；类结束关闭该 context。

## 证据与限制

RED/GREEN 和完整回归的命令、数量、输出节选及 Git 保护核验见 [issue43-keypopulation-submit-permission-evidence.md](issue43-keypopulation-submit-permission-evidence.md)。

| 运行 | 实际数量 | 失败 / 错误 / 跳过 | 结论 |
|---|---:|---|---|
| 旧版 DRAFT 复现 | 2 | 2 / 0 / 0 | 预期 RED：两类申请都实际返回 200 并产生业务变更 |
| 旧版完整权限矩阵 | 27 | 10 / 0 / 0 | 预期 RED：包含 RETURNED 和直调 Service 缺口 |
| 修复后专项 | 148 | 0 / 0 / 0 | GREEN，包含新增 27 项 |
| 首次完整回归 | 已完成 466 | 0 / 0 / 0 | JVM 异常退出，运行未完成，不计作通过 |
| 原命令完整重跑 | 797 | 0 / 0 / 0 | GREEN：历史 770 + 新增 27 |

完整通过时间：2026-11-07 12:04（Asia/Shanghai）。首次运行的异常退出和日志均保留，没有跳过、放宽或移除任何测试；本轮未声称解释首次运行的全部耗时原因。JaCoCo 报告因执行数据缺失而跳过，本轮无覆盖率结论。

实际变更文件：上述生产 workflow、本测试类、本文、配套 evidence 文档，以及 `doc/testing/2026-11-02-antigravity-audit-ledger.md` 的 #43 与相关状态说明。其他审计条目不变。

H2 MySQL 模式验证真实 Java/Spring 事务、SQL 映射、HTTP RBAC 和数据库权限变更；不等价于真实 MySQL 并发/锁/生产隔离级别验证。测试 JVM 为 JDK 21.0.7，编译目标 release 17，未实际在 JDK 17 运行。本轮未验证真实 Redis/Nginx 多实例、RabbitMQ、PaddleOCR、前端或生产部署。合成材料不证明文件上传或外部存储链路。授权以当前请求加载的认证主体为准，不声称保证请求执行期间并发撤权的即时中断。
