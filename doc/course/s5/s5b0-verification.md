# S5-B0 验证与交付记录（冻结候选）

日期：2026-11-06（Asia/Shanghai）。文档/工具：**CODE_CANDIDATE**。此记录是本轮实际执行结果，不是部署或成员确认记录。未确认字段/接口均为 **PENDING_CONFIRMATION**。

## 1. 基线、隔离和变更范围

- 仓库：Wjx871/Population_miniterm。
- 基线：`9f9c13727c552f031b97862dea1595db7e643453`。
- worktree：`C:\Users\jxgm\.codex\worktrees\s5b0-integration-readiness\小学期`。
- feature：`feat/20261106-s5b0-integration-readiness`。
- 最终完整commit SHA由交付报告和Git feature HEAD记录，避免在提交内容中自引用该提交SHA。测试执行时为该基线加本轮八个新增文件的工作树。
- Java生产源码、pom、配置、数据库迁移、Nginx、Docker、前端均无修改。
- 已按用户授权fetch，精确基线创建managed worktree后在其中建指定分支；没有复制主工作区未提交修改。
- 主工作区.gitignore施工前SHA-256：`8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`，最终复核见第4节。

八文件：
1. [环境契约](s5b0-environment-contract.md)：14组件、A/B分类、版本和GAP-01—08。
2. [Runbook](s5b0-integration-runbook.md)：Phase A—F、A1九场景、A2十三类及原39行矩阵、FS九场景。
3. [团队交接](s5b0-team-handoff.md)：赵书亚/王家兴/张蕊对接文本及B1-01—10。
4. [证据模板](s5b0-evidence-template.md)：完整Test ID、物理节点、恢复和问题台账。
5. [本验证记录](s5b0-verification.md)。
6. [预检指南](s5b0-preflight-guide.md)。
7. [只读脚本](../../../scripts/windows/s5b0-preflight.ps1)。
8. [独立测试](../../../scripts/windows/tests/s5b0-preflight.tests.ps1)。

## 2. 本轮实际测试

运行环境：Windows、PowerShell7.6.9、Oracle JDK21.0.7，Maven3.9.16；Java编译目标release17。未声称验证JDK17运行时。

```powershell
pwsh -NoProfile -File ./scripts/windows/tests/s5b0-preflight.tests.ps1

./mvnw.cmd '-Dtest=S5A1StrictRevocationHttpIntegrationTest,CoreTransactionBoundaryIntegrationTest,ReferenceCacheIsolationIntegrationTest,ReferenceCacheTransactionIntegrationTest,StatisticsScopeCacheIntegrationTest' test

./mvnw.cmd test
```

| 检查 | 本轮实际结果 | 范围与限制 |
|---|---|---|
| 预检自身测试 | PASS：17组，0失败，退出0 | 内存文件夹具/模拟HTTP；无真实网络、服务启动或夹具文件清理 |
| Java专项 | PASS：76 tests，0 failures/errors/skipped | strict撤销、事务边界、引用缓存隔离/失效、统计DataScope；H2和Mock/Stub |
| 完整Maven回归 | PASS：730 tests，0 failures/errors/skipped；65 suites；退出0 | 本轮确实重跑；耗时2分27秒，完成于2026-11-06 21:03:16 +08:00；不等于真实MySQL/Redis/Broker验收 |
| JaCoCo覆盖率 | NOT_VERIFIED | Maven输出“Skipping JaCoCo execution due to missing execution data file”；不能据测试成功报告覆盖率 |
| PowerShell语法 | PASS：两份ps1解析错误0 | 未执行真实HTTP |
| Markdown引用/围栏 | PASS：79个相对文件链接，失效0，围栏平衡 | 只证明引用存在，非部署证据 |
| 必需文件/变更范围 | PASS：34/34清单文件可读；仅新增批准八文件 | 未修改生产文件 |
| Offline实际执行 | BLOCKED/2：5 PASS、1 BLOCKED、1 NOT_VERIFIED、0 FAIL | STATIC_ONLY；不等于READY |

原始本地Maven日志：`target/s5b0-full-maven.log`；JUnit结果：`target/surefire-reports/TEST-*.xml`。这些是本轮构建产物，未加入提交、未清理。专项结果来自本轮命令输出，完整回归之后Surefire文件已反映完整套件，不能把当前文件计数再当专项原始计数。

自身测试覆盖：默认Offline及省略Mode零transport、内存夹具不变/不创建root、关键文件缺失、UNC预拒绝、严格IPv4/IPv6/mapped分类、非本机授权、编码/userinfo/路径/查询绕过、非整数/越界超时、错误Mode、8目标上限、重定向/非200、畸形JSON/字段类型/DB DOWN、额外敏感字段不回显、16KiB上限、超时及异常安全映射。全部HTTP为MOCK_TRANSPORT/SIMULATED_HTTP；超时测试不能冒充实际DNS/TCP/TLS或慢正文测试。

Offline源码操作清单：只允许已知本地文件读取、字符串/JSON/IPAddress解析；无数据库客户端、写SQL、进程启动/终止、文件写入/删除、环境赋值。可选HTTP函数只在显式模式+合法地址通过整组验证后调用，handler保持TLS校验并禁止代理/自动凭据/Cookie/重定向。以上静态核对与Mock测试不能证明真实网络路径已运行。

## 3. Offline及真实环境状态

本文件创建后已实际运行默认Offline，原生退出码2；输出STATIC_ONLY，总体BLOCKED，5项静态PASS、ENV-CONFIRMATION为BLOCKED、REAL-INTEGRATION为NOT_VERIFIED、0 FAIL。没有触网，也没有读取真实部署凭据。这个结果表示本地准备检查通过而外部输入仍需确认，不是工具异常或真实环境READY。ReadOnlyProbe本轮未执行。

| 项目 | 本轮状态 |
|---|---|
| 14组件环境契约、接口与部署输入 | 冻结候选；PENDING_CONFIRMATION |
| 真实Redis/Nginx双实例 | NOT_VERIFIED |
| 真实MySQL主从及物理节点路由 | NOT_VERIFIED |
| 真实RabbitMQ | NOT_VERIFIED |
| 真实Python Document Analysis | NOT_VERIFIED；缺真实HTTP适配/读取契约时BLOCKED |
| 真实Python Policy Query | NOT_VERIFIED |
| 实际共享上传/AI暂存/正式材料/导出 | NOT_VERIFIED |
| 真实Web/JavaFX联调 | NOT_VERIFIED |
| #64 / #3 / #33 | OPEN |
| #21 | FIXED_VERIFIED |

真实环境可用性没有本轮观测；组员完成进度没有确认，不宣称已部署或不可达。路由器、文档分析适配、Nginx双实例拓扑、共享卷、ops接口等缺口见环境契约。旧迁移/Redis文档只作为差异和阻断风险记录，未执行或修改数据库升级流程。

## 4. Git保护、推送与停止

```text
保护远端refs（施工前已核实；推送前后必须再次核实）：
integration/20261101-backend-p0-security
9f9c13727c552f031b97862dea1595db7e643453

main
b54579b6b7f8b1feeda60f43906da05fb2ffda99

feat/20261103-s3-rabbitmq-async-precheck
758014cbcccdfe820efb2a6fae73bc3168030484

snapshot/20261103-pre-course-upgrade-backend
d6b0b87b74af7a413214d7da9dd30b075cec882b

本地main保持：
d7fc1192967a13c7d80d2b60fdc12749fd5218df

唯一允许push目标：
git push origin HEAD:refs/heads/feat/20261106-s5b0-integration-readiness
```

提交前已通过git ls-remote再次核对四条保护远端refs，均与上述精确SHA一致；本地main及主工作区.gitignore摘要也与施工前一致，主工作区仍仅显示既有.gitignore修改。施工区只新增批准八文件，未修改既有tracked文件。最终提交SHA及推送后复核在最终交付报告给出。不force、不rebase/reset、不合并integration/main，不创建PR，不修改其他组员分支，不删除或清理worktree。只有本feature得到本轮commit/push。

## 5. S5-B1交接门槛

按[对接清单的B1-01—10](s5b0-team-handoff.md#5-s5-b1逐项准入条件)逐项收集主责确认：隔离环境/授权与恢复、同版本A/B及安全配置、数据库结构与备份授权、两个Python及真实分析适配、Broker、三类共享存储、Redis完整性与旧JWT失效策略、物理主从路由、Nginx与前端/看板、Runbook与证据/故障窗口。当前均PENDING_CONFIRMATION。

各门槛须有确认人、时间、范围、版本和证据；未满足的相应子阶段BLOCKED。静态检查及本轮730回归不替代这些条件，不自动部署、不执行故障注入、不进入S5-B1/S6。保留feature与施工worktree，交付后停止等待独立复核。
