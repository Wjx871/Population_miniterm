# #34 居住证首次申领在途申请排他候选

状态：CODE_CANDIDATE，等待独立代码复核。基线 `836cf5d658ff4fd3e290de3087afca6d611185e8`。

## 规则与源码依据

按 `business_application.target_person_id` 查 `RESIDENCE_PERMIT_FIRST_ISSUE`，仅以下通用申请状态占位：DRAFT、SUBMITTED、UNDER_REVIEW、APPROVED、RETURNED、RESUBMITTED。REJECTED、WITHDRAWN、CANCELLED、COMPLETED 不占位；已有 ACTIVE/current_flag=1 居住证的既有拒绝仍先执行。

`ApplicationStatus` 还声明 RESUBMITTED；当前 `ApprovalService.submit` 直接 DRAFT/RETURNED → UNDER_REVIEW，没有正常持久化 RESUBMITTED 的路径，因此将它作为兼容历史数据的未结状态防守，测试明确使用合成状态夹具。SUBMITTED 也用状态夹具覆盖。其余 DRAFT/UNDER_REVIEW/APPROVED/RETURNED 和取消/驳回/撤回/办结有真实 HTTP 流程。

权威依据是通用表而非专业表：`ApplicationService.cancelDraft` 不通知专业监听器，通用 CANCELLED 时专业表仍可为 DRAFT；`returnApplication` 同样不通知监听器，通用 RETURNED 时专业表可仍 APPROVED。RETURNED 允许补正、复用审批单再次提交；APPROVED 不自动签发，`FloatingResidenceService.issue` 才创建证件并办结。因此两者均需占位。

仅限定业务类型，签注、注销不计入首次申领在途数。查询不要求专业详情存在，不因缺失/状态滞后的历史详情漏掉未结通用申请。正常新增时 `createPermit` 将通用 target_person_id、专业 person_id 统一取自所锁的有效登记，并填充相同 floating_id，HTTP 测试核对关联值。

## 事务、快照与锁

`createFirstIssue` 保留原事务、原有效登记锁和数据范围验证；新增查询位于权限验证和 ACTIVE 证件验证之后，创建任何申请之前。冲突仅返回统一 409 文案“该人员已有处理中首次申领申请”，不返回已有申请 ID、申请人或材料信息。

原 `lockFloating` 是按 floating_id 主键的 floating_population JOIN person FOR UPDATE。有效登记有 UNIQUE(person_id,current_flag)，当前为 1，关闭置 NULL；正式居住证有相同人员/current_flag 唯一约束。JOIN 锁住登记及人员父行，同人创建请求在父实体上串行化；历史登记关闭后新登记的首次申领仍按 personId 检查旧未结申请。没有 JVM 锁、全局锁、新的专业申请范围锁或正式数据清理。

**MySQL RR 前提：入口事务的第一条数据库查询必须继续是 requireActiveFloating → lockFloating。** 该入口由非事务 Controller 调用，当前仓库没有其他调用者。锁等待期间尚未建立一致性 Read View；scope 的来源申请查询、ACTIVE 证件查询、在途计数都在父锁取得后首次建快照，能看到前一创建事务的提交。不能把普通查询移动到锁前，也不能在已有旧快照的外层 RR 事务中复用该入口并据此声称安全。现有文档 `2026-11-02-round4-mysql-final-gate.md` 已有人工先建旧快照的 RR/RC 对照，本轮不将其误称为安全的调用方式。

在途 COUNT 使用已有 `idx_application_target_person(target_person_id)`，不增加锁定子表的 SELECT FOR UPDATE：空申请范围的 next-key 锁可能令不同人员插入互相等待。已有父锁足够覆盖当前真实 HTTP 入口。没有隔离级别变更或数据库迁移。没有改变 #32 的登记/人员 → 证件锁顺序，也没有修改签注、注销或关闭实现。

测试用 ApplicationService spy 在通用草稿和 CREATE 审批轨迹插入后暂停首事务，让第二个真实 HTTP 请求等待；第一请求提交后第二个 409，首事务故障回滚后第二个 201。不同人员并发必须都到达插入屏障并返回 201，验证没有全局或空范围锁串行化。MySQL 门禁额外要求 performance_schema 观察到不同 InnoDB 事务在登记/人员上的实际锁等待，并检查 1213 错误计数无增长。

## RED/GREEN 证据与复跑

RED 在未改生产代码的精确基线运行，使用 RANDOM_PORT Tomcat + TestRestTemplate 实际 TCP HTTP，不使用 MockMvc。6 个场景：DRAFT、UNDER_REVIEW、APPROVED、RETURNED、不同申请人、同人并发，全部复现双 201 及两张表各新增一条。RETURNED 快照同时证明专业表仍 APPROVED。保存于 [evidence/issue34-red.txt](evidence/issue34-red.txt)。

```powershell
# 仅在精确旧基线、携带新增 RED 测试文件的隔离工作区运行
.\mvnw.cmd -Dtest=Issue34FirstIssueRedTest -Dissue34.red=true test

# 候选代码的 H2 真实 HTTP 与原居住证回归
.\mvnw.cmd -Dtest=Issue34FirstIssueExclusivityTest,Phase05FloatingResidenceIntegrationTest test

# 显式隔离 MySQL 门禁，先配置 MYSQL84_GATE_*，禁止生产/3306
.\mvnw.cmd -Dtest=Issue34MySql84IT test

.\mvnw.cmd test
git diff --check
```

RED 测试默认禁用，防止将旧漏洞期望混入 GREEN；MySQL 类以 IT 结尾，只能显式选择。H2 测试只允许随机 mem 数据库。MySQL 专用测试库先通过 `scripts/windows/tests/mysql84-prepare-schema.ps1` Fresh/Verify，再对本轮库名替换后的临时副本执行仓库既有 V4_015～V4_018，未改原迁移。准备时发现旧门禁脚本仅补至 V4_013/基础 V4_014，缺少 V4_018 的材料存储列，补齐后才获得本文最终通过结果。专用库的合成 API 账号密码统一为测试值；夹具按用户名查 ID，避免依赖 H2/MySQL 不同种子编号。

MySQL 测试先严格核验 URL 为 127.0.0.1、显式非3306端口、population_miniterm_mysql_gate_issue34_*、createDatabaseIfNotExist=false、非 root 应用账户、实际版本/端口/绑定地址/库名/账户以及 REPEATABLE-READ，然后才重置该专用合成测试库。

GREEN 验证矩阵包括：六类未结状态、四类终态（刻意保留专业表旧 DRAFT）、真实取消/驳回/撤回/退回重提/显式签发、原 ACTIVE 证件拒绝、不同人员、不同有权来源、旧 floatingId 未结申请、其他两类业务不占位、缺失详情、历史重复保留、REGION/SELF/DEPARTMENT 权限、无敏感信息泄露、10轮同人并发、首事务回滚、不同人员同时创建。重复拒绝前后比较 business_application、residence_permit_application、sys_approval_request、sys_approval_log、operation_log、application_material、residence_permit_log 七表行数。继续办理流程包含真实 multipart 材料上传及核验；核心必需材料由隔离 SQL 夹具置为 VERIFIED。原 Phase05 覆盖签注/注销/关闭/到期和各执行回滚。

## 历史数据只读检测与兼容

没有连接真实业务库，无法断言生产是否已有重复或损坏关联。历史重复不删除、不自动改状态，存在至少一份未结首次申领即拒绝继续新建；历史重复测试保留两份已有草稿并验证第三次 409。需业务逐单处理已有重复，候选代码不追溯改变历史单的上传、审批或签发资格。

仅供有权维护者在授权数据库只读检查的 SQL：

```sql
SELECT target_person_id, COUNT(*) open_count
FROM business_application
WHERE business_type='RESIDENCE_PERMIT_FIRST_ISSUE'
  AND status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','RETURNED','RESUBMITTED')
GROUP BY target_person_id HAVING COUNT(*) > 1;

SELECT a.application_id, a.target_person_id, r.person_id, r.floating_id,
       f.person_id AS floating_person_id, a.status, r.business_status
FROM business_application a
LEFT JOIN residence_permit_application r ON r.application_id=a.application_id
LEFT JOIN floating_population f ON f.floating_id=r.floating_id
WHERE a.business_type='RESIDENCE_PERMIT_FIRST_ISSUE'
  AND (r.application_id IS NULL OR a.target_person_id IS NULL
       OR a.target_person_id<>r.person_id OR r.person_id<>f.person_id
       OR r.apply_type<>'FIRST_ISSUE');
```

通用 target_person_id 为 NULL 或与专业/登记不符的损坏历史资料不能由本修复推断正确人员，须人工核验；正常专业入口生成的关联有 HTTP 和数据库断言。

## 验证结果与交付边界

- MySQL #34：35 passed / 0 failed / 0 errors / 0 skipped；[逐轮锁等待、状态和数据库快照](evidence/issue34-mysql.txt)。10轮同人竞争各为201/409，实际观察到不同 InnoDB 事务在父行上的等待；回滚场景500/201，各轮拒绝无额外业务行。专用实例1213累计为0。
- MySQL #32回归：1项门禁内10轮，全部关闭200/签注409，锁等待图和1213增量0；[证据](evidence/issue34-mysql-lockorder.txt)。该既有门禁使用MockMvc连接真实MySQL，区别于本轮#34的真实TCP HTTP。
- 索引：真实MySQL EXPLAIN选择既有idx_application_target_person；[索引/查询计划/1213计数](evidence/issue34-mysql-index.txt)。本轮无新迁移，也没有改历史迁移。
- 本轮MySQL：8.4.8，127.0.0.1:13394，population_miniterm_mysql_gate_issue34_3cccd9ef，REPEATABLE-READ，非root应用账户issue34_app。测试后关闭本轮实例，数据/临时迁移副本保留在C:\Users\jxgm\AppData\Local\Temp\population_issue34_mysql_20261107_di2vpx9s；没有连接默认3306。该目录的私有设置不入Git。

- 最终全量 `.\mvnw.cmd test`：848 total / 845 passed / 0 failed / 0 errors / 3 skipped（默认禁用的 RED 类3个测试方法）。耗时2分55秒，BUILD SUCCESS；[全量及H2逐轮证据](evidence/issue34-full.txt)。包含新增35项真实HTTP H2测试与原Phase05的40项回归。没有把先前开发期测试夹具/初始化失败日志当作最终通过证据。
- 四条远端保护引用施工前、施工后均与任务给定SHA相同，原主工作区只保留既有.gitignore修改且SHA256未变；[核验](evidence/issue34-protected-refs.txt)。未操作#43工作区或分支；#33/#41/#45/#64及#43账本原行保持不变。
- 交付分支：fix/20261107-issue34-permit-first-issue-exclusivity；worktree：C:\Users\jxgm\.codex\worktrees\issue34-permit-exclusivity\小学期。仅推送该feature分支，保留worktree和分支供独立复核，不合并、不部署。
- 变更范围：3个生产源码/Mapper文件、4个新增测试文件、本说明及evidence证据；旧审计账本仅#34行更新为CODE_CANDIDATE。无新数据库迁移、无生产数据修改。

所有结果仅适用于本轮合成数据，不代表团队共享环境或生产部署验证。当前未验证 JDK 17（本机使用 JDK 21）、真实上传材料内容真实性、生产数据分布/大表性能以及额外外层旧快照事务的复用。
