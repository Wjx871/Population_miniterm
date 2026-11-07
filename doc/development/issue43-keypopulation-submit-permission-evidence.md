# #43 验证证据

日期：2026-11-07（Asia/Shanghai）。状态：CODE_CANDIDATE。

实现/测试提交：`e96acbb2a9abf0abdd1e5b6bd57589efc9939123`，父提交为精确基线 `a1cff942f7f9d7370ad7e4858133435ca2b70115`。所有测试完成后才提交，后续提交仅补充 #43 审计状态及本 SHA 引用，生产/测试源码不变。

## RED：未修改生产代码

精确基线 `a1cff942f7f9d7370ad7e4858133435ca2b70115`；运行 RED 时 `git diff -- src/main/java` 为空，只新增回归测试和文档。

首次只运行 DRAFT 复现：

```powershell
.\mvnw.cmd "-Dtest=Issue43KeyPopulationSubmitPermissionIntegrationTest#revokedApplyDraftCannotUseGenericSubmit" test
```

随后运行完整旧版矩阵：

```powershell
.\mvnw.cmd "-Dtest=Issue43KeyPopulationSubmitPermissionIntegrationTest" test
```

下列为原始本地日志的脱敏摘录，仅保存场景、计数与断言，不收录登录响应或 JWT。原始日志保留在施工 worktree 根目录（Git 忽略的 *.log）。

### issue43-red-draft.log

SHA256：`0d73d956e68b6459af1b4e4bd3dfed4284d9ec5c1b7c21459d29ce049ee36535`；Maven exit=1（预期 RED）。

```text
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-draft-REGISTER app=1 HTTP=200 unchanged=false observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-draft-RELEASE app=2 HTTP=200 unchanged=false observedTables=11
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 33.14 s <<< FAILURE! -- in com.wjx871.population.keypopulation.Issue43KeyPopulationSubmitPermissionIntegrationTest
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.revokedApplyDraftCannotUseGenericSubmit:108->deniedWithoutEffects:275 [revoked-apply-draft-REGISTER HTTP]
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.revokedApplyDraftCannotUseGenericSubmit:108->deniedWithoutEffects:275 [revoked-apply-draft-RELEASE HTTP]
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

### issue43-red-matrix.log

SHA256：`52ad121204f11361fa78e8996156f3d17144ef5e8c5dfc4418227543b7d8ad76`；Maven exit=1（预期 RED）。

```text
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=before-restore-REGISTER app=1 HTTP=200 unchanged=false observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=before-restore-RELEASE app=2 HTTP=200 unchanged=false observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_AUTHORIZED operation=REGISTER generic=true HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_AUTHORIZED operation=RELEASE generic=true HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_AUTHORIZED operation=REGISTER generic=false HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_AUTHORIZED operation=RELEASE generic=false HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=specialized-apply-rbac-REGISTER app=7 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=specialized-apply-rbac-RELEASE app=8 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-returned-REGISTER app=9 HTTP=200 unchanged=false observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-returned-RELEASE app=10 HTTP=200 unchanged=false observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_DENIAL scenario=person-scope-REGISTER-true app=12 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_DENIAL scenario=person-scope-RELEASE-true app=13 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_DENIAL scenario=person-scope-REGISTER-false app=14 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_DENIAL scenario=person-scope-RELEASE-false app=15 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=false sameJWT=true
ISSUE43_DENIAL scenario=missing-submit-rbac-REGISTER-true app=18 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=false sameJWT=true
ISSUE43_DENIAL scenario=missing-submit-rbac-RELEASE-true app=19 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=false sameJWT=true
ISSUE43_DENIAL scenario=missing-submit-rbac-REGISTER-false app=20 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=false sameJWT=true
ISSUE43_DENIAL scenario=missing-submit-rbac-RELEASE-false app=21 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-draft-REGISTER app=22 HTTP=200 unchanged=false observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-draft-RELEASE app=23 HTTP=200 unchanged=false observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
[ERROR] Tests run: 27, Failures: 10, Errors: 0, Skipped: 0, Time elapsed: 37.97 s <<< FAILURE! -- in com.wjx871.population.keypopulation.Issue43KeyPopulationSubmitPermissionIntegrationTest
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.approvalServiceRequiresBothPermissionsFromRealLoadedUser:168
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.approvalServiceRequiresBothPermissionsFromRealLoadedUser:168
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.approvalServiceRequiresBothPermissionsFromRealLoadedUser:168
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.approvalServiceRequiresBothPermissionsFromRealLoadedUser:168
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.restoringPermissionAllowsSameJwtAndDraftToSubmit:202->deniedWithoutEffects:275 [before-restore-REGISTER HTTP]
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.restoringPermissionAllowsSameJwtAndDraftToSubmit:202->deniedWithoutEffects:275 [before-restore-RELEASE HTTP]
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.revokedApplyDraftCannotUseGenericSubmit:108->deniedWithoutEffects:275 [revoked-apply-draft-REGISTER HTTP]
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.revokedApplyDraftCannotUseGenericSubmit:108->deniedWithoutEffects:275 [revoked-apply-draft-RELEASE HTTP]
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.revokedApplyReturnedCannotResubmit:126->deniedWithoutEffects:275 [revoked-apply-returned-REGISTER HTTP]
[ERROR]   Issue43KeyPopulationSubmitPermissionIntegrationTest.revokedApplyReturnedCannotResubmit:126->deniedWithoutEffects:275 [revoked-apply-returned-RELEASE HTTP]
[ERROR] Tests run: 27, Failures: 10, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

首次 RED：2 项、失败 2、错误 0、跳过 0。完整 RED：27 项、失败 10、错误 0、跳过 0（17 项通过）。10 个失败为 DRAFT 撤权 2、RETURNED 撤权 2、权限恢复前的拒绝 2，以及直接 Service 缺失任一权限 4。HTTP 缺 application:submit、专用路由缺 apply、正常授权、材料和 DataScope 既有检查通过。

## GREEN 专项

```powershell
.\mvnw.cmd "-Dtest=Issue43KeyPopulationSubmitPermissionIntegrationTest,Phase10KeyPopulationIntegrationTest,Phase10KeyPopulationGateIntegrationTest,Phase02ApprovalIntegrationTest,Phase12ApplicationReturnIntegrationTest,AuthRbacIntegrationTest,ApplicationMaterialSubmitConcurrencyIntegrationTest" test
```

Maven exit=0；148 项、失败 0、错误 0、跳过 0。新增矩阵 27 项全通过，其余专项 121 项全通过。

原始日志 `issue43-green-targeted.log` SHA256：`75230766f429dc96cd6f828cf3aeffe7fe52327d8b13541f34f4414a23ef5408`。

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 39.35 s -- in com.wjx871.population.application.Phase12ApplicationReturnIntegrationTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 12.57 s -- in com.wjx871.population.approval.Phase02ApprovalIntegrationTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.389 s -- in com.wjx871.population.auth.AuthRbacIntegrationTest
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=before-restore-REGISTER app=1 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_RESTORED operation=REGISTER sameJWT=true HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=before-restore-RELEASE app=2 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_RESTORED operation=RELEASE sameJWT=true HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_AUTHORIZED operation=REGISTER generic=true HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_AUTHORIZED operation=RELEASE generic=true HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_AUTHORIZED operation=REGISTER generic=false HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_AUTHORIZED operation=RELEASE generic=false HTTP=200
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=specialized-apply-rbac-REGISTER app=7 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=specialized-apply-rbac-RELEASE app=8 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-returned-REGISTER app=9 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-returned-RELEASE app=10 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_DENIAL scenario=person-scope-REGISTER-true app=12 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_DENIAL scenario=person-scope-RELEASE-true app=13 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_DENIAL scenario=person-scope-REGISTER-false app=14 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_DENIAL scenario=person-scope-RELEASE-false app=15 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=false sameJWT=true
ISSUE43_DENIAL scenario=missing-submit-rbac-REGISTER-true app=18 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=false sameJWT=true
ISSUE43_DENIAL scenario=missing-submit-rbac-RELEASE-true app=19 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=false sameJWT=true
ISSUE43_DENIAL scenario=missing-submit-rbac-REGISTER-false app=20 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=true submit=false sameJWT=true
ISSUE43_DENIAL scenario=missing-submit-rbac-RELEASE-false app=21 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-draft-REGISTER app=22 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_CURRENT_PERMISSIONS apply=false submit=true sameJWT=true
ISSUE43_DENIAL scenario=revoked-apply-draft-RELEASE app=23 HTTP=403 unchanged=true observedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_SERVICE_DENIED operation=REGISTER missing=key-population:apply unchangedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_SERVICE_DENIED operation=RELEASE missing=key-population:apply unchangedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_SERVICE_DENIED operation=REGISTER missing=application:submit unchangedTables=11
ISSUE43_CURRENT_PERMISSIONS apply=true submit=true sameJWT=true
ISSUE43_SERVICE_DENIED operation=RELEASE missing=application:submit unchangedTables=11
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 10.11 s -- in com.wjx871.population.keypopulation.Issue43KeyPopulationSubmitPermissionIntegrationTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 20.94 s -- in com.wjx871.population.keypopulation.Phase10KeyPopulationGateIntegrationTest
[INFO] Tests run: 56, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.874 s -- in com.wjx871.population.keypopulation.Phase10KeyPopulationIntegrationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.331 s -- in com.wjx871.population.material.ApplicationMaterialSubmitConcurrencyIntegrationTest
[INFO] Tests run: 148, Failures: 0, Errors: 0, Skipped: 0
[INFO] Skipping JaCoCo execution due to missing execution data file.
[INFO] BUILD SUCCESS
```

## 首次完整回归：未完成，保留失败运行记录

命令 `.\mvnw.cmd test`，日志 `issue43-full-regression.log`；SHA256：`7567b0654d0fb7640507bc37516f2fd39ad3b8c6870fd747c3361fdecc5c67b1`。

已完成 466 项、失败 0、错误 0、跳过 0，随后 forked VM 异常退出，退出码 `1073807364`，Maven BUILD FAILURE（30:12）。此运行不能计作全量通过。日志把退出时正在执行的类标为 `Phase03MigrationIntegrationTest`，没有对应测试断言失败；本轮不据此断言该类存在业务缺陷。此前 `PolicyPythonHttpStubIntegrationTest` 耗时 1558 秒（7 项全通过），耗时原因未确认。保留首次运行，不更改或跳过测试，以同一原命令重新完整执行。

## Git 保护边界

施工前及回归期间两次直接查询远端，四个保护引用完全一致：

| 引用 | SHA |
|---|---|
| `origin/main` | `b54579b6b7f8b1feeda60f43906da05fb2ffda99` |
| `integration/20261101-backend-p0-security` | `a1cff942f7f9d7370ad7e4858133435ca2b70115` |
| `feat/20261103-s3-rabbitmq-async-precheck` | `758014cbcccdfe820efb2a6fae73bc3168030484` |
| `snapshot/20261103-pre-course-upgrade-backend` | `d6b0b87b74af7a413214d7da9dd30b075cec882b` |

本地旧 main 实际为 `d7fc1192967a13c7d80d2b60fdc12749fd5218df`，施工前即与远端 main 不同，本轮原样保留；没有从它开始施工。原 main worktree 始终只有 `.gitignore` 的既有未提交变更，其 SHA256 前后一致：`8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`。integration/S3 的本地分支及 Snapshot 的远端跟踪引用也保持上述值。没有操作其他 worktree 的业务文件。

## 完整回归重跑：GREEN

```powershell
.\mvnw.cmd test
```

Maven exit=0；797 项（历史 770 + 本轮新增 27），失败 0、错误 0、跳过 0。原始日志 `issue43-full-regression-retry.log` SHA256：`2f02b8c45701ab07fe98821a6ad70e3b4efa2b7151e723bea2e5d80f4fa04239`。Surefire XML 聚合 70 个 suite，计数同为 797/0/0/0。

```text
[INFO] Tests run: 797, Failures: 0, Errors: 0, Skipped: 0
[INFO] Skipping JaCoCo execution due to missing execution data file.
[INFO] BUILD SUCCESS
[INFO] Total time:  02:57 min
[INFO] Finished at: 2026-11-07T12:04:11+08:00
```

JaCoCo 因执行数据文件缺失跳过覆盖率报告；不报告覆盖率。测试以 JDK 21.0.7 执行、release 17 编译，H2 的限制见主文档。

新增测试源码 SHA256：`089c0d2b7549e76ee63c8b88ed99e1e8a7560b3af614364d8b128c3a3d85c125`；首次 RED、完整 RED、GREEN 专项及完整回归使用同一份测试源码，修复后未放宽断言。

完整回归结束、实现提交完成后再次直接核验：上述远端四引用、本地 integration/S3/main 与 Snapshot 跟踪引用均未变化；原 main 的工作区仍仅 ` M .gitignore`，字节哈希仍一致。实现提交之后生产及测试源码 diff 为空。`git diff --check` 通过。
