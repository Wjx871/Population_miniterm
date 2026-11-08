# #41 / #45 重点人口数据范围一致性候选

状态：**#41 CODE_CANDIDATE；#45 CODE_CANDIDATE**。等待独立复核；本分支不合并、不部署、不清理。

## 基线与施工范围

- 精确基线：`f5c3a959cf99f69fed8e8640d845a99b23428563`。
- 分支：`fix/20261108-issue41-45-keypopulation-scope`。
- worktree：`C:\Users\jxgm\.codex\worktrees\issue41-45-keypopulation-scope\小学期`。
- 主工作区既有 `.gitignore` 修改保留，SHA256 应为 `8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`。
- 已有源码修改限于 KeyPopulation Mapper/XML、Service、Workflow；新增 ReleasePolicy、专项测试、MySQL 隔离运行脚本与本说明/证据。经用户追加许可，Phase10 旧测试仅将 `cancelledPerson` 的预期 409 改为 404。
- 审计账本仅修改 #41/#45 两行。#43 测试未修改；V4 历史迁移未修改。

## 精确旧版 RED（生产代码未修改）

原始日志：`evidence/issue41-45-red-confirmed.txt`。3 次预期断言失败，0 errors，证明旧版不满足新契约。早期夹具构建失败日志也保留，不能当作漏洞 RED。

| #41 实际阶段 | 旧版响应/数据库结果 |
| --- | --- |
| A 区 REGION 用户真实 REGISTER、提交、批准、显式执行 | 成功，产生 COMPLETED 来源申请、专业明细和 REGISTERED 历史 |
| 真实 PERSON_CANCELLATION（SETTLED_ABROAD）草稿、材料、提交、批准、执行 | 成功，person=CANCELLED，residence 删除，residence_archive 追加 PERSON_CANCELLATION |
| A 查询原重点记录 | 200（旧 scope 的业务申请关联提供可见性） |
| A 创建 RELEASE 草稿 | 201（旧 createRelease 复用可见 scope） |
| 准备必需材料 | 两条 VERIFIED 必需材料 |
| 专用 RELEASE submit | 403，超出人员数据范围；仍 DRAFT，审批单 0 |
| 通用 application submit | 独立场景同样 403；仍 DRAFT，审批单 0 |

注销前后 person/residence/key/source/归档快照，以及各 HTTP 响应均在同一日志。没有预设“所有入口都失败”。

| #45：仅 A 区 INACTIVE 行、无其他 A 区合法 REGION 授权 | 旧版实际结果 | 修复后要求 |
| --- | --- | --- |
| 列表及 count | total=1 | total=0 |
| 人员重点人口列表 | total=1 | total=0 |
| 详情 / 历史 | 200 / 200 | 404 / 404 |
| 创建解除 | 201 | 404 |
| 新建建档 | 409（穿过 scope，命中已有 ACTIVE 重点记录冲突） | 404（先拒绝范围） |
| 既有建档草稿通用 / 专用提交 | 200 / 200 | 403 / 403 |
| ALL 用户显式 regionCode=A 筛选 | total=1 | total=0 |
| ALL 用户查询的 regionCode 展示 | A 历史区划 | null（无当前 ACTIVE 户籍） |

H2 与生产 MySQL DDL 均为 `UNIQUE(residence.person_id)`，没有 residence.current_flag。同一个人不能同时保留 A 历史行和 B ACTIVE 行；测试没有移除约束或插入非法组合。用单独的 INACTIVE 历史夹具复现，再用 A→B 当前户籍变更、B 有效期间的正反例和实际 B 注销流程验证边界。历史真实数据没有被改写。

## 归属证据与权限契约

`ApplicationService.createInternal` 从服务端当前用户写入 applicant_user_id / department_id / region_code，客户端不能指定这些字段；但 ALL 申请人能跨区办理，所以 applicant_region_code **单独不足以证明户籍管辖**。

REGION 记录读取和 RELEASE 管理共用以下规则：

1. 当前存在 ACTIVE residence，且其完整 region_code 在当前用户真实 regionCodes 集合中，具有当前范围。任何历史行、业务申请人的身份或字符串前缀都不能替代此检查。
2. 没有任何 ACTIVE residence，person 为 CANCELLED/DECEASED 时，才研究历史责任兜底，且必须同时满足：
   - `key_population.source_application_id` 精确关联 **该记录**的 COMPLETED KEY_POPULATION_REGISTER；target_person_id 与记录一致。
   - 该申请的专业明细为 COMPLETED REGISTER，person/type 与记录一致。
   - 该记录有相同 person/source 的 REGISTERED→ACTIVE 历史。
   - 来源申请区划在当前用户 regionCodes 中。
   - 最近一次人员注销归档来源为真正 COMPLETED PERSON_CANCELLATION，专业注销明细同人且 COMPLETED；归档原状态 ACTIVE、归档类型 PERSON_CANCELLATION/DEATH_CANCELLATION。
   - 原建档申请区划与此次执行捕获的 residence_archive.region_code_snapshot 必须同时位于当前用户真实 regionCodes 集合中。父级 REGION 可覆盖下级；两者不要求字符串相等，也不使用前缀推算权限。以归档序号选最近一次注销，不能拿更早的 A 区归档覆盖后来的 B 区注销。
3. 缺失、损坏或互相矛盾的历史证据不提供 REGION 兜底。不会临时改成 ALL。

因此 A 建档后迁到 B，A 立即失去 REGION 当前范围；随后在 B 注销，也不会让 A 自动恢复权限。若 B 的当前权限集合不含该记录可证明的原建档来源区划，B 也不会被虚构为历史责任方；有明确 ALL 角色及业务权限的操作人仍可处理，这属于原有明确授权，并非程序兜底。

| 当前数据范围 | 保留的记录可见性 | RELEASE 管理依据 |
| --- | --- | --- |
| SELF | 既有按人员关联本人业务申请的读取契约 | 必须是该记录经完整来源链验证的原建档申请人，且当前仍具备相应角色/权限 |
| DEPARTMENT | 记录责任部门或该人员的部门业务申请（既有读取契约） | 该记录完整原建档来源的申请部门，或原专业明细与记录一致的审批后责任部门 |
| REGION | 当前 ACTIVE 户籍；或上述受限注销责任链 | 同一规则，提交还必须验证 record/person/type 一致 |
| ALL | 明确的当前 ALL 角色 | 明确的当前 ALL 角色；仍检查操作类型、record/person/type、材料、状态、权限 |

responsible_department_id 可在 REGISTER 请求指定，不能单凭一个数字就为 REGION 授权；DEPARTMENT 管理只接受与已批准执行的原专业明细一致的责任字段，或原建档申请部门。无关 GENERAL_SERVICE、其他人员业务、同一人员的其他 REGISTER/RELEASE 均不能替代记录 source。保留的 SELF/DEPARTMENT 读取语义不自动赋予管理能力，专项测试明确覆盖“可见但不能解除”。

## 调用链与改动

- 通用 `/api/applications/{id}/submit` 和两个专业提交入口最终均调用 `ApprovalService.submit`。它先锁业务申请、校验本人和状态，再执行统一 `KeyPopulationWorkflow.validate`，通过后才写状态/审批单/日志。
- Workflow 最先保留 #43 的 `key-population:apply` 与 `application:submit` 双权限校验。REGISTER 检人员范围、类型与人员关联，CANCELLED/DECEASED 不允许提交；RELEASE 改查精确 recordId 的管理 scope，拒绝错误 personId、recordId、operationType、businessType 或 populationType。
- createRelease 使用 `lockManagedRecord`，与提交的 `findManagedRecord` 共用管理 SQL。申请创建前即检查，拒绝时没有专业申请、业务申请、材料或成功日志副作用。
- 新增 ReleasePolicy 作为 ApplicationApprovalValidator，批准时重新加载申请人的 AuthService 当前身份及双权限、当前管理范围；执行时同时检查执行人和原申请人的当前管理范围及记录关联。
- executeRelease 保留 APPROVED/version、ACTIVE 记录、日期、乐观更新、历史追加、申请完成和事务日志；批准本身不会将记录 RELEASED。
- Mapper 的 scope/personScope、filter.region、cols.region_code、所有 scoped find/lock/search/count 均采用 ACTIVE 当前户籍。MySQL 实测发现动态 XML 产生 `?AND`，已显式补齐条件间分隔空白；H2 原先容忍此拼接，不能替代 MySQL 验证。空 REGION、无效 scope 分支 fail-closed。search/count 共享同一 filter，历史查询先走 scoped record；projection 只展示当前 ACTIVE 区划，注销责任区划不会冒充当前居住区划。
- `assertScope` 的 REGION 执行申请范围改用完整 regionCodes 集合，不再仅匹配根区划字符串。record policy 仍另行检查，申请人本人不能绕过当前 record 管理范围。

## 验收和原始证据

专项类 `Issue4145KeyPopulationScopeHttpIntegrationTest` 使用随机 Tomcat 端口的真实 TCP HTTP、Spring Security/JWT、数据库 AuthService、MyBatis 与隔离数据库。没有 Mock Mapper 或伪造安全上下文。材料使用数据库中合成的 VERIFIED 元数据（带真实测试核验用户外键），**未上传物理文件，未验证整个上传/核验 UI 链路**。部分既有草稿和故障关联通过隔离 SQL 夹具构造，日志明确标记。

新增矩阵覆盖：正常 REGISTER/RELEASE；CANCELLED 和 DECEASED 后可证明责任方的解除；父级 REGION 的真实下级集合；两种提交入口；注销后 REGISTER 禁止；历史非 ACTIVE 越权；B 当前权限；迁移后再注销不复活 A；ALL 跨区建档不把申请人区划变成管辖；SELF/DEPARTMENT/REGION/ALL 正反例；可见与可管理区分；无关申请；空区划；六种源损坏；错 person/record/类型；提交后范围变动阻止批准/执行；拒绝前后 11 张业务/材料/审批/成功日志表逐值一致；敏感字段掩码；分页/list/count/detail/history/person-list 一致。

验证命令（在本 worktree）：

```powershell
.\mvnw.cmd '-Dtest=Issue4145KeyPopulationScopeHttpIntegrationTest#cancelledOriginalManagerCanCreateSubmitAndExplicitlyExecuteRelease+inactiveResidenceDoesNotGrantAnyCurrentScope' test
.\mvnw.cmd test
.\scripts\windows\tests\issue41-45-mysql-http.ps1
git diff --check
```

运行结果和 MySQL 计划补充见下方最终验证记录。RED 的第一个命令必须在精确旧基线生产代码上运行；新版运行应为 GREEN。

## SQL、事务与性能边界

- residence 唯一 person 索引意味着每次人员相关 EXISTS 最多检查一条当前表记录，ACTIVE 为该行上的状态筛选；不需要为此改历史 V4 脚本。
- source 使用 business_application 主键、专业明细 application_id 唯一索引、建档历史 source/event 及 record 索引；EXISTS 不连接展开结果，多个历史事件不会令分页重复。
- 注销归档的最新序号和 person 关联、其执行申请及专业明细均需校验。实际 MySQL EXPLAIN 保留用于观察选用索引，不能据小型合成测试宣称生产容量或大表性能。
- SELECT FOR UPDATE 保留 key/person 及专业申请相关行锁、乐观版本和事务回滚约束。外层 FOR UPDATE **不等于所有 EXISTS 子查询/RBAC/归档行都被锁定**；MySQL C2 实测更早建立快照的 RR 事务等待后普通 count 仍是0、另一个事务已提交1；RC 对照等待后读到1。授权集合与户籍同时变化的完整线性化，不在此次验证证明范围内。原 C2 RR/RC 门禁另行保留其原有结论，不能推导为所有子查询的锁证明。
- 明确保留限制：旧导入记录没有完整 source/history/cancellation 链时不能获得新的 REGION 历史授权；未实现责任移交业务；未回填真实历史数据；未验证 JDK17 运行时、生产库、团队共享库、部署链路或生产负载。

## 证据文件说明

- `issue41-45-red.txt`：第一次夹具初始化失败（部门 sort_no），不是漏洞证据。
- `issue41-45-red-http.txt`：第二次夹具失败（归档字段/申请号长度），不是漏洞证据。
- `issue41-45-red-baseline.txt`：#45 已复现，#41 当时缺少注销创建权限，不能据此判定 #41。
- `issue41-45-red-confirmed.txt`：正式精确旧版 RED，#41 两种入口和 #45 全部动态观测完成。
- `issue41-45-green-attempt1.txt` / `attempt2.txt`：专项15/16项已通过，故障夹具和 Tomcat/MyBatis 资源缓存污染已定位，未冒称完整通过。
- `issue41-45-green-attempt3.txt`：新增20项、#43 27项、Phase10 Gate 11项通过；Phase10 仅旧注销状态断言不符。经用户许可改为404。此日志尚未包含最终更严格的注销归档关联方案。
- `issue41-45-mysql-attempt1.txt`：新建 MySQL8.4.8、34145、独立库及 DML 账户验证成功；材料夹具硬编码核验用户7违反生产FK，20项均未通过。已改为按 username 查询测试核验人，未改生产约束。
- `issue41-45-mysql-location.txt`：一次预初始化但未启动/使用的数据目录，保留未清理。
- `issue41-45-full.txt`：加入父级 REGION 专项前的全量 H2 Maven 输出，892项、889通过、3跳过、0失败/错误；采用更保守的源区划=归档区划规则。最终父级集合规则另见 full-final 日志。

所有失败尝试原始输出均保留。日志使用合成数据；green-attempt3 的旧测试失败转储中有一个合成测试 JWT，交付副本已脱敏，原始副本及 SHA256 私有保存，路径见 `issue41-45-redaction-note.txt`。正式 RED、最终全量原日志未修改。未输出生产凭据或测试库随机密码。非正式早期控制台存在中文编码损失，正式 RED 及后续日志统一 UTF-8。


## 最终 MySQL 8.4 验证

`evidence/issue41-45-mysql-final.txt`：MySQL **8.4.8**，`127.0.0.1:34145`，REPEATABLE-READ，随机命名 `population_miniterm_mysql_gate_i4145_*` 独立库。每次运行均新建私有数据目录、随机密码和 schema-scoped DML 账户 `i4145_app`，不访问默认3306、生产库或团队共享库。脚本仅停止自己启动的进程，保留所有数据目录，不删除文件，退出时恢复进程环境变量。

按生产 fresh DDL + 演示测试种子 + 既有 V4_011–013 / V4_015–018 初始化；base DDL 已包含 V4_014 初始范围字段/索引，未重复应用该迁移。所有历史迁移仅在新测试库执行，没有修改原脚本。

| 验证 | 结果 |
| --- | --- |
| 新专项真实 HTTP | 24 / 24 通过 |
| 既有 MySQL C2 门禁 | 4 / 4 通过 |
| 并发创建 REGISTER / RELEASE | 各10轮，每轮201+409、仅1份申请/专业明细/成功创建日志 |
| 实际 Mapper SQL 的 EXPLAIN | ACTIVE / CANCELLED 两组，8种语句，共16份 |
| SQL兼容性、注销后解除、损坏证据、四范围/无副作用/掩码/迁移边界 | 新专项均在真实MySQL8.4.8通过 |

计划实际使用：residence=`uk_residence_person`；来源申请/注销申请=`PRIMARY`；专业明细=`uk_key_population_application`；建档历史=`uk_key_history_application_event`；归档最新记录=`idx_archive_person_time`，归档本行=`PRIMARY`，注销明细=`uk_cancellation_application`。所有计划及绑定参数在日志，样本很小，不作容量结论。未测生产数据量、同时变更RBAC/户籍/归档的完整串行化。

MySQL第二次尝试 `issue41-45-mysql-attempt2.txt`：C2四项通过；建库未包含当前S3/S4迁移，缺 application_material.storage_kind 导致专项22项失败，另2项通过。第三次 `issue41-45-mysql-attempt3.txt`：迁移补齐后18项专项通过；5项暴露动态SQL无分隔空白，1项暴露错误人员夹具不满足生产FK。后续已修正XML空白、使用合法存在的合成人员；最终28项全部通过。16份计划的双场景补充在最终日志中。


## 最终全量验证与交付清单

2026-11-08（Asia/Shanghai），Java **21.0.7**、javac release17；未实际运行JDK17。

| 最终检查 | 实际结果 | 证据 |
| --- | --- | --- |
| `mvnw.cmd test` | **893 total / 890 passed / 3 skipped / 0 failures / 0 errors**，BUILD SUCCESS，3:33 | `evidence/issue41-45-full-final.txt` |
| 本轮H2专项 | 24 passed | 同上 |
| #43 未改动的完整回归 | 27 passed，含撤权后同JWT、两个专业入口与通用入口、直接ApprovalService | 同上 |
| Phase10主流程 / Gate | 56 / 11 passed，含并发和事务回滚 | 同上 |
| MySQL8.4.8 | 24 HTTP + 4 C2 = **28 passed**，零失败/错误/跳过 | `evidence/issue41-45-mysql-final.txt` |
| 3项跳过 | 既有 Issue34FirstIssueRedTest，旧版RED由单独方式运行，不是本轮新增跳过 | 全量日志 |
| JaCoCo | 本次日志提示缺少执行数据而跳过报告；不宣称覆盖率结果 | 全量日志 |
| 源码与测试指纹 | 7个已测试文件SHA256 | `evidence/issue41-45-tested-source-sha256.txt` |
| 四条保护远端引用 / 本地main / .gitignore | 与任务给定完整值一致 | `evidence/issue41-45-protected-refs.txt` |

Git变更范围：4个已有重点人口源码/XML文件，新增ReleasePolicy；新增24例专项类；Phase10仅一行409→404；新增独立MySQL运行脚本；本报告、日志证据和审计账本两行。原 #43 测试、其他审计问题、历史迁移、主工作区与旧feature/worktree均未改动。

证据目录的新增 `.gitattributes` 只匹配本轮 `issue41-45-*.txt`，保留原始日志换行/尾部空白并关闭日志的空白告警，不改变源码的 whitespace 检查。原始输出不为通过 diff-check 而裁剪或重新整理。

保护引用：integration=`f5c3a959cf99f69fed8e8640d845a99b23428563`；远端main=`b54579b6b7f8b1feeda60f43906da05fb2ffda99`；S3=`758014cbcccdfe820efb2a6fae73bc3168030484`；Snapshot=`d6b0b87b74af7a413214d7da9dd30b075cec882b`。本地main=`d7fc1192967a13c7d80d2b60fdc12749fd5218df`，主工作区`.gitignore`指纹见上表。#81/#83目录/占用/登记/分支与#43保留分支未做清理。

交付后保留本feature和worktree，等待ChatGPT独立复核。**仅 CODE_CANDIDATE，不代表已批准合并或完成生产验收。**
