# Antigravity 原始审计 86 项逐条对账

基线：`c6caa2d87038fd728772699a7af643931852c28a`。原始报告保留在 `D:\学习用\大二下\小学期\.agents\teamwork\orchestrator\SYSTEM_CORE_BUSINESS_AUDIT_REPORT.md`，本文件不覆盖该报告。

原审计状态沿用任务约定的六种值；2026-11-10 对 #3、#15、#19、#22、#34、#37 和 #78 完成独立复核状态核销，详见文末补充。`FIXED_VERIFIED` 不代表已部署；其余项目维持原状。除标明“本轮动态复现”的项目外，结论来自当前源码、既有集成测试、已审查提交或 API/前端契约；没有把未运行的场景写成动态通过。`FRONTEND_HANDOFF` 项本轮没有改动前端。

| # | 原级别 | 原问题摘要 | 最新状态 | 复核依据、复现与关联提交 | 后续责任方 |
|---:|---|---|---|---|---|
| 1 | CRITICAL | 普通编辑可能复活注销或死亡人员 | FIXED_VERIFIED | `PersonService` 不再把客户端 status 写回；`Phase08PersonIdentityIntegrationTest.ordinaryEditsPreserveCancelledAndDeceasedLifecycleStates` 与本轮 `Phase08PersonIdentityIntegrationTest` 覆盖显式 status 绕过；`6541a05`。 | 后端已完成 |
| 2 | CRITICAL | REGION 建档后人员无法由创建人读取 | FIXED_VERIFIED | 本轮旧实现复现：201 创建后同区详情 403、列表/导出为 0；修复后同区、异区、ALL 与导出断言通过；`PersonRegionOwnershipAuditIntegrationTest`，`aff3f8e`，迁移 `V4_014`。 | 后端已完成 |
| 3 | HIGH | OCR 远程请求发生在长事务内 | FIXED_VERIFIED | 历史修复 `a1cff942` 经 `c49e753` 的 H2/MySQL 8.4 慢 OCR、连接与锁观测及 UNKNOWN 文件保留验证；ChatGPT 独立复核通过。EXISTING_FIX_CONFIRMED / REVIEW_PASSED / INTEGRATED / CLEANUP_COMPLETED。同步请求仍占用线程，无持久化崩溃恢复协议。 [证据](../development/issue3-ocr-transaction-verification.md)。 | 已复核；未声明生产部署 |
| 4 | HIGH | 脱敏回显值可能被编辑表单写回 | FRONTEND_HANDOFF | 后端保留/忽略脱敏字段的兼容逻辑已在 `PersonService`；编辑表单回填与提交仍需前端验证。 | 前端组 |
| 5 | MEDIUM | 性别编码与前端字典不一致 | FRONTEND_HANDOFF | 关联前端表单/字典映射；本轮未改前端。 | 前端组 |
| 6 | MEDIUM | 影印本 SHA 去重可跨用户占用 | FIXED_VERIFIED | `PersonIdCardImageService` 按对象归属拒绝跨用户读取/覆盖；`Phase14PersonIdCardOcrIntegrationTest.duplicateFromAnotherUploaderCannotReadOrChangeOriginalDraftOrFileReference`；`dfc5f81`。 | 后端已完成 |
| 7 | LOW | OCR 敏感字段切片逻辑脆弱 | FIXED_VERIFIED | 本轮修复共享身份证短值掩码及历史影印本已存掩码输出；RED/GREEN、OCR/事务补偿回归和 Maven 全量通过，生产数据与部署未验证。 [证据](../development/issue7-50-64-batch-review-2026-11-11.md)。 | 后端已验证 |
| 8 | LOW | 前端适配器导入路径缺扩展名 | FRONTEND_HANDOFF | 前端模块解析/构建行为需前端组核验；本轮未改前端。 | 前端组 |
| 9 | CRITICAL | 多个 ACTIVE HEAD 导致户主变更 selectOne 崩溃 | FIXED_VERIFIED | 新增 HEAD 关系走户主变更入口；历史重复 HEAD 被安全拒绝；`Phase08HouseholdMasterDataIntegrationTest`；`45b5ac1`。 | 后端已完成 |
| 10 | HIGH | 成员离户日期早于入户日期仍可成功 | FIXED_VERIFIED | `HouseholdService.leave` 校验日期；`leaveBeforeJoinDateIsRejectedWithoutMutation` 与相等日期正常路径测试；`45b5ac1`。 | 后端已完成 |
| 11 | HIGH | 空户/无户主最后成员离户后不能销户 | FIXED_VERIFIED | 直接离户和迁出执行均核验有效成员、户籍及在途迁入后设置待销户；H2 与 MySQL 专项覆盖；`0750ce9`、`9368ac1`。 | 后端已完成 |
| 12 | HIGH | 家庭关系枚举文案及户主显示回退异常 | FRONTEND_HANDOFF | 属关系标签和展示回退；本轮未改前端。 | 前端组 |
| 13 | MEDIUM | 家庭户表单重复渲染控件 | FRONTEND_HANDOFF | 视图布局问题；本轮未改前端。 | 前端组 |
| 14 | MEDIUM | 前端拦截器吞掉 409 业务冲突提示 | FRONTEND_HANDOFF | API 错误提示呈现需前端核验；本轮未改前端。 | 前端组 |
| 15 | MEDIUM | 立户日期与成员入户日期边界未校验 | FIXED_VERIFIED | 家庭户建立、成员入户、迁入及历史恢复日期边界已修复；H2 RED/GREEN、MySQL 并发及全量回归经独立复核。提交 `3f273088`。 [证据](../development/issue15-household-date-boundary.md)。 | 已复核；未声明生产部署 |
| 16 | LOW | 详情刷新未 await 导致竞态 | FRONTEND_HANDOFF | 页面异步刷新行为；本轮未改前端。 | 前端组 |
| 17 | LOW | 家庭户适配器重复定义 regionCode | FRONTEND_HANDOFF | 前端适配器实现问题；本轮未改前端。 | 前端组 |
| 18 | CRITICAL | 户主迁出申请缺少继任者导致批准后无法执行 | FIXED_NOT_VERIFIED | 新申请提交门禁为历史修复；本轮合成旧单确认执行冲突及退回后的常规编辑缺口。材料逐轮快照和重审安全闭环未实现；`eabae6a`。 [证据](../development/issue7-18-55-batch-review-2026-11-11.md)。 | 后端后续处理历史单 |
| 19 | HIGH | 跨区迁移批次号全局唯一使取消草稿占号 | FIXED_VERIFIED | 取消草稿释放有效批次号占位；`d443f30b`、V4_019 条件唯一方案及 H2/MySQL 8.4 并发门禁经独立复核。历史数据升级需备份与受控回填；隔离验证不代表生产已执行 V4_019。 [证据](../development/issue19-migration-batch-reuse.md)。 | 已复核；未声明生产部署 |
| 20 | HIGH | 户主交接只改主表指针未更新成员 HEAD 关系 | FIXED_VERIFIED | `MigrationService.executeOut` 同事务更新新户主关系与家庭户指针；迁出交接测试覆盖；`eabae6a`。 | 后端已完成 |
| 21 | MEDIUM | 目标家庭户与迁入区划不一致 | FIXED_VERIFIED | `MigrationService.createIn/updateIn/executeIn` 均在服务端锁定/读取目标户后要求非空 `household.region_code` 与 `to_region_code` 精确一致；执行时再次验证。`Phase03MigrationIntegrationTest.targetRegionMismatchIsRejectedOnCreateUpdateAndExecute` 覆盖三处拒绝、草稿不被改写及执行失败不改人员/户籍。 | 后端已完成 |
| 22 | MEDIUM | 户籍归档写入未检查受影响行数 | FIXED_VERIFIED | `3268a831` 要求 executeOut 归档受影响行数为 1；零行旧版 RED、H2/MySQL GREEN、回滚与回归经独立复核；-1/2 为模拟返回。 [证据](../development/issue22-archive-affected-rows.md)。 | 已复核；未声明生产部署 |
| 23 | MEDIUM | 迁出表单缺少新户主选择与同意材料 | FRONTEND_HANDOFF | 前端申请表单/材料配置需核验；新后端提交门禁已要求继任者；本轮未改前端。 | 前端组 |
| 24 | LOW | 迁出区划与归档查询类型交互不规范 | FRONTEND_HANDOFF | 查询控件绑定问题；本轮未改前端。 | 前端组 |
| 25 | CRITICAL | 人员编辑接口隐式复活注销/死亡状态 | FIXED_VERIFIED | 与 #1 同根因；服务端状态白名单与 Person Mapper 不写 status；人口生命周期测试通过；`6541a05`。 | 后端已完成 |
| 26 | HIGH | 注销提交门禁调用 bad 但未 throw | FIXED_VERIFIED | `CancellationSubmissionValidator` 已实际抛出门禁异常；`Phase04CancellationIntegrationTest` 覆盖非法注销拒绝与正常申请；`6541a05`。 | 后端已完成 |
| 27 | HIGH | 注销草稿列表快照为空且范围过滤错误 | FIXED_VERIFIED | 创建/更新时生成可信快照，查询与 count 使用一致权限条件；`Phase04CancellationIntegrationTest` 覆盖；`be12635`。 | 后端已完成 |
| 28 | MEDIUM | 注销查询错误复用 SELF 归档范围规则 | FIXED_VERIFIED | 注销列表 SELF 按本人申请授权，归档仍按既有规则拒绝 SELF；列表/详情测试通过；`be12635`。 | 后端已完成 |
| 29 | MEDIUM | 空户销户原户主快照缺失 | OPEN | 真实链路表征证实最后户主注销后家庭户归档的户主 ID 和姓名为空；历史无户主与历史信息丢失无法可靠区分。其他路径仍需动态表征，未来快照方案待评审。 [证据](../development/issue29-64-real-validation-2026-11-12.md)。 | 后端/产品 |
| 30 | LOW | 注销状态监听器未映射 RETURNED | NOT_REPRODUCED | 实际人员与家庭户退回链路未向监听器广播 RETURNED，原异常未复现；修正另发现的注销列表和详情状态显示歧义。H2 流程、错误状态与全量回归通过；MySQL 仅验证隔离合成查询。 [本轮报告](../development/issue30-62-70-batch-review-2026-11-11.md)。 | 已验证当前链路；未声明生产部署 |
| 31 | CRITICAL | SELF 可绕过归属校验读取他人流动/居住证 | FIXED_VERIFIED | 服务层按来源申请人校验 SELF；`FloatingSelfScopeAuditIntegrationTest` 覆盖本人允许、他人拒绝及列表/详情；`6541a05`。 | 后端已完成 |
| 32 | HIGH | 流动关闭与居住证签注锁顺序形成 MySQL 死锁 | FIXED_VERIFIED | 本轮旧实现捕获 8.4.8 `1213/40001` 等待环；修复后 MySQL 10/10 轮关闭 200、签注 409、死锁计数不增；`MySql84CriticalAcceptanceIT.closeThenEndorseLocksFloatingBeforePermitWithoutDeadlock`，`2bfa038`。 | 后端已完成 |
| 33 | HIGH | 同市跨区户籍冲突只按区划精确相等判断 | OPEN | `countActiveResidenceInRegion` 与 `MigrationMapper.xml` 仍按 `region_code=#{region}` 精确匹配。仓库 admin_region 有层级表和合成种子，但没有权威完整区划数据或可证明“同市”归属规则；本轮不推断编码前缀/长度、不修改查询。需业务提供权威区划数据与边界规则后再修复。 | 后端/业务规则提供方 |
| 34 | HIGH | 居住证申请缺少在途排他检查 | FIXED_VERIFIED | `5b95f2be` 按人员、通用业务类型及未结状态排他；真实 HTTP/H2 与 MySQL RR 并发门禁经独立复核。RR 结论依赖当前入口先取得父记录锁再建立一致性快照，不涵盖已有旧快照的外层事务。 [证据](../development/issue34-permit-first-issue-exclusivity.md)。 | 已复核；未声明生产部署 |
| 35 | MEDIUM | 居住证到期预警页签复用停滞 | FRONTEND_HANDOFF | 前端 Tab/列表刷新问题；本轮未改前端。 | 前端组 |
| 36 | MEDIUM | 申领弹窗可选不合资格人员 | FRONTEND_HANDOFF | 候选项筛选属前端；后端执行仍校验有效登记与申领日期；本轮未改前端。 | 前端组 |
| 37 | MEDIUM | 居住证到期任务全量加载结果集 | FIXED_VERIFIED | `5d05109e` 使用有界 ID keyset 分页、冻结日期、锁后重验；MySQL 两类各 10000 候选及并发/锁顺序回归经独立复核。分页不消除数据库扫描成本。 [证据](../development/issue37-expiry-keyset-pagination.md)。 | 已复核；未声明生产部署 |
| 38 | LOW | 关闭流动登记与签注的级联锁不一致 | FIXED_VERIFIED | 与 #32 同一等待环；锁流动登记先于居住证行，`lockPermit` 不再锁 JOIN 表；同一 MySQL 十轮门禁通过；`2bfa038`。 | 后端已完成 |
| 39 | HIGH | 解除申请被错误要求提交建档依据 | NOT_REPRODUCED | 当前 API 文档明确解除材料规则同建档；前端建档/解除共用必需材料与核验 helper；`Phase10KeyPopulationIntegrationTest.releaseMissingMaterials` 验证缺材料拒绝。无业务代码改动。 | 后端按现有契约 |
| 40 | HIGH | 重点人口/统计状态值不匹配导致统计恒为零 | FIXED_VERIFIED | 统计 SQL 使用业务 ACTIVE 状态并传入范围；`StatisticsScopeIntegrationTest`、`DashboardMapperTest`；`8509d78`。 | 后端已完成 |
| 41 | HIGH | 注销人员的重点人口记录不能由 REGION 用户解除 | FIXED_VERIFIED | 已集成并经 ChatGPT 独立复核；当前 ACTIVE 户籍及严格来源链、最近注销归档的历史责任范围；批准与执行复核权限。历史 H2/MySQL 专项各 24 项通过；旧快照和并发授权/户籍变更的完整线性化未证明。 [独立复核](../development/issue41-43-45-independent-review-2026-11-11.md)。未验证生产部署。 | 已复核；未声明生产部署 |
| 42 | HIGH | 建档执行后未把生成记录 ID 写回申请详情 | NOT_REPRODUCED | `key_population` 保存 `source_application_id`，可由申请反查生成记录；现有设计把 `key_population_application.record_id` 用于解除目标。未找到接口契约要求反向回填，现有执行与历史测试可按 source app 关联。 | 后端/产品仅在契约变化时处理 |
| 43 | MEDIUM | 通用提交路由可能绕过重点人口专属 apply 权限 | FIXED_VERIFIED | 已集成并经 ChatGPT 独立复核；Workflow 在写入前统一校验 application:submit 与 key-population:apply，覆盖通用和专业入口、重提交与直接服务调用。历史 27 项专项通过；原独立验证主要为 H2，未完成专属 MySQL 门禁。 [独立复核](../development/issue41-43-45-independent-review-2026-11-11.md)。未验证生产部署。 | 已复核；未声明生产部署 |
| 44 | MEDIUM | 在途重点人口重复申请查询遗漏 RETURNED | FIXED_VERIFIED | 旧 H2 复现 RETURNED 建档/解除重复请求都返回 201，并复现并发双 201；现已计入 RETURNED、对人员/记录父行加锁；11 项 Phase10 测试通过；`8d78575`。 | 后端已完成 |
| 45 | MEDIUM | 重点人口人员区划关联未限定 ACTIVE 户籍 | FIXED_VERIFIED | 已集成并经 ChatGPT 独立复核；当前范围与区划显示只使用 ACTIVE 户籍，REGION 空集合拒绝；注销历史责任限于 #41 严格条件。历史 H2 和 MySQL 真实业务场景通过。 [独立复核](../development/issue41-43-45-independent-review-2026-11-11.md)。未验证生产部署。 | 已复核；未声明生产部署 |
| 46 | LOW | 重点人口建档日期没有禁用日期范围 | FRONTEND_HANDOFF | `KeyPopulationRegisterCreate.vue` 日期选择交互；本轮未改前端。 | 前端组 |
| 47 | LOW | 解除创建页直接显示英文枚举 | FRONTEND_HANDOFF | `KeyPopulationReleaseCreate.vue` 展示文案；本轮未改前端。 | 前端组 |
| 48 | LOW | 历史列表直接展示操作人数字 ID | FRONTEND_HANDOFF | `KeyPopulationHistory.vue` 文案/显示映射；本轮未改前端。 | 前端组 |
| 49 | CRITICAL | RETURNED 不能补传/删除材料导致二次审批卡死 | FIXED_VERIFIED | 材料服务允许 DRAFT/RETURNED 并校验归属；`Phase12ApplicationReturnIntegrationTest` 覆盖退回、补材、复核、复审；`9b5081b`。 | 后端已完成 |
| 50 | CRITICAL | RETURNED 申请栏没有重新提交与继续编辑入口 | FIXED_NOT_VERIFIED | 本轮修复专业路由导致 RETURNED 重新提交按钮隐藏，状态与权限矩阵通过；专业退回补正入口、浏览器交互及重复提交仍需验证，#18 历史单未解决。 [证据](../development/issue7-50-64-batch-review-2026-11-11.md)。 | 前后端继续核验 |
| 51 | HIGH | 材料核验缺少申请人/上传者防自审 | FIXED_VERIFIED | 服务端拒绝申请人及上传者自审；重点人口门禁与 Phase03 核验测试通过；`a1840f2`。 | 后端已完成 |
| 52 | MEDIUM | REGION 申请列表不能按授权下级区划查询 | FIXED_VERIFIED | 审批与申请 Mapper 使用授权区划集合；`Phase02ApprovalIntegrationTest.approverSeesChildRegionButOtherRegionDoesNot`；`3caaeca`。 | 后端已完成 |
| 53 | MEDIUM | 撤回/取消按钮未校验申请人归属 | FRONTEND_HANDOFF | 前端按钮可见条件与错误处理需核验；后端仍校验归属；本轮未改前端。 | 前端组 |
| 54 | LOW | 取消对话框 Promise rejection 未捕获 | FRONTEND_HANDOFF | 前端 Promise 异常处理；本轮未改前端。 | 前端组 |
| 55 | LOW | 草稿 DELETE 缺少客户端版本号 | FIXED_VERIFIED | 取消草稿要求客户端版本并以其执行乐观锁；缺失或无效返回 400、过期返回 409。HTTP、专业草稿事务、前端及全量回归通过。 [证据](../development/issue7-18-55-batch-review-2026-11-11.md)。 | 后端已完成；待独立复核 |
| 56 | CRITICAL | MainLayout 缺失 resolveLandingPath 导入 | FRONTEND_HANDOFF | 前端导航/导入问题；本轮未改前端，未运行前端构建。 | 前端组 |
| 57 | CRITICAL | 普通/敏感导出 SQL 漏 DEPARTMENT/SELF 过滤 | FIXED_VERIFIED | 导出 SQL 覆盖 SELF/部门/区划/ALL；`ExportDataScopeAuditIntegrationTest` 行范围断言；`6541a05`、`553f539`。 | 后端已完成 |
| 58 | HIGH | REGION 使用固定 substring 导致区县越权 | FIXED_VERIFIED | 授权区划树解析为完整代码集合并精确匹配；区域隔离集成测试通过；`3caaeca`。 | 后端已完成 |
| 59 | HIGH | 迁移归档 SELF 详情逻辑短路 | FIXED_VERIFIED | 归档明确拒绝 SELF，列表/详情语义一致；`Phase03MigrationIntegrationTest`；`3caaeca`。 | 后端已完成 |
| 60 | HIGH | 注销列表缺少部门过滤 | FIXED_VERIFIED | 列表与 count 按 SELF/部门/授权区划一致过滤；`Phase04CancellationIntegrationTest`；`be12635`。 | 后端已完成 |
| 61 | HIGH | 过期 Bearer Token 阻断匿名登录 | FIXED_VERIFIED | JWT filter 豁免精确公开登录路径；失效 Token 登录可继续，受保护 API 仍 401；`AuthRbacIntegrationTest`，`0d84620`。 | 后端已完成 |
| 62 | MEDIUM | 业务申请和审批区划判定曾分裂 | FIXED_VERIFIED | 独立 H2 真实认证、授权及 MyBatis 测试覆盖多子区划、同级和伪前缀排除、列表/审批对照及分页等；原 Mapper 未改。 [本轮报告](../development/issue30-62-70-batch-review-2026-11-11.md)。 | 已验证；未声明生产部署 |
| 63 | MEDIUM | 统计接口未使用调用者数据范围 | FIXED_VERIFIED | 统计 Service/Mapper 传递 `DataScopeCriteria`；部门/SELF/REGION/ALL 测试通过；`8509d78`。 | 后端已完成 |
| 64 | MEDIUM | 本地 Token 黑名单无主动清理/分布式同步 | OPEN | 2026-11-12 真实 Redis 双 JVM 跨实例注销与正常 RDB 恢复通过，空目录恢复旧令牌重新返回 200。2026-11-13 完成方案 A 架构设计、29 项验收矩阵、三层门禁合同及 **Step 1（`feat/20261113-issue64-durable-store-step1` @ `731189f`）与独立复核安全补丁（`fix/20261113-issue64-step1-cleanup-schema-guard`）**。2026-11-14 完成 **Step 2 持久认证与吊销主链路接入（`feat/20261114-issue64-durable-auth-step2` @ `8da6e547`）及独立复核安全补丁（`fix/20261114-issue64-step2-authority-preflight`）**：（1）在 `RevocationProperties`、`RevocationEvents`、`JwtService`、`TokenRevocationService`、`JwtAuthenticationFilter`、`AuthService`、`HealthController`、`application.properties` 接入 `DURABLE_DB_REQUIRED`（保持向后兼容、生产禁止 `READY + 0`、不自动建表/激活）；（2）新增只读 `DurableRevocationStore.probeAuthoritativeStore()` 复用 `selectAuthoritativeState` 同时校验 `jwt_revocation_checkpoint`（READY+非负水位）与 `jwt_revocation` 真实可读性，并接入启动校验、登录前校验、签发后二次复核、注销提交前预检及 `/api/health`（`jwt_revocation` 故障返回 `DB_REVOCATION_UNAVAILABLE`，绝不误报 `DB_PRIMARY_UP`）；（3）补丁后实测台账：**`EXECUTED_PASS`**：Step 1/2 核心与探针负例 H2（8 类）`51/51`、既有 JWT 回归 `77/77`、隔离 MySQL `8.4.8`（`127.0.0.1:10932` 非 root `issue64_app`，先导 `V4_020` 与激活脚本）`DurableAuthStep2MySql84IT`（含真实 `RENAME TABLE jwt_revocation` 故障与恢复）`6/6`、全量 `mvnw.cmd clean test` `1073`（`0` 失败、`0` 错误、`6` 项既有 opt-in 跳过：`Issue34FirstIssueRedTest`:3、`Issue37ExpiryBatchIntegrationTest`:1、`Issue64DurabilityRedGateTest`:1、`Issue64TargetPrerequisiteCheckTest`:1）、Python `test_issue64_red_gate.py` `15/15`；**`EXECUTED_RED` / `ERROR`（沿用 Step 2 原记录）**：基线 RED 门禁按预期失败；**`NOT_RUN`**：真实双 JVM + 共享 MySQL 8.4 + Redis 端到端 `TARGET_GREEN` 门禁未运行，#64 保持 `OPEN`。 [ADR](../development/issue64-durable-revocation-adr.md) / [矩阵](../development/issue64-durable-revocation-gate.md) / [证据](../development/issue64-durable-revocation-evidence.md) / [Step 1 交付](../development/issue64-step1-durable-store.md) / [Step 2 交付](../development/issue64-step2-durable-auth.md)。 | 待 Step 3 执行真实双 JVM `TARGET_GREEN` 门禁及独立复核 |
| 65 | LOW | 会话恢复初次挂载可能丢角色权限 | FIXED_NOT_VERIFIED | `user.js` 现会恢复持久化角色/权限并核验 `/auth/me`；未找到 UI 初次挂载闪烁专测。 | 前端补测 |
| 66 | CRITICAL | 统计 SQL 用中文状态与标准状态不匹配 | FIXED_VERIFIED | 统计查询使用 ACTIVE 等业务枚举；统计集成测试验证合成 ACTIVE 记录；`8509d78`。 | 后端已完成 |
| 67 | HIGH | 综合档案抽屉 prop 名与组件声明失配 | FRONTEND_HANDOFF | `ComprehensiveProfileDrawer.vue`/`DetailDrawer` 绑定需前端核验；本轮未改前端。 | 前端组 |
| 68 | HIGH | 综合查询未关联流动登记/居住证/迁移字段 | FIXED_VERIFIED | ChatGPT 独立复核通过，原实现 `c3fb7eb1` 已集成；当前流动登记、居住证及 COMPLETED 最近迁移字段和服务端脱敏已有历史 H2/MySQL 验证，同日取 OUT。详见 [独立复核与证据](../development/issue68-71-independent-review-2026-11-11.md)；生产规模性能未验证。 | 已复核；未声明生产部署 |
| 69 | HIGH | 迁移历史方向参数绑定到迁移类型 | FRONTEND_HANDOFF | 前端方向查询参数需与后端契约核对；本轮未改前端。 | 前端组 |
| 70 | MEDIUM | 统计日志明细可能返回未脱敏 detail | FIXED_VERIFIED | 历史实现与专项测试相对证据提交未变；本轮 11 项 HTTP 专项通过，响应副本固定脱敏及权限范围保留。真实 MySQL、前端人工回归、生产部署与其他非标准敏感日志格式未验证。 [本轮报告](../development/issue30-62-70-batch-review-2026-11-11.md)。 | 已验证；保留场景限制 |
| 71 | MEDIUM | 综合查询 REGION 仅按户籍过滤漏流动人口 | FIXED_VERIFIED | ChatGPT 独立复核通过，原实现 `c3fb7eb1` 已集成；两套查询统一以授权区划内 ACTIVE 户籍或 ACTIVE/current_flag=1 流动登记授予 REGION 范围，空集合 403，历史记录不单独授权。历史 H2/MySQL、列表详情及分页验证见 [独立复核与证据](../development/issue68-71-independent-review-2026-11-11.md)。 | 已复核；未声明生产部署 |
| 72 | MEDIUM | 零人口圆环图渲染异常 | FRONTEND_HANDOFF | 图表空数据展示；本轮未改前端。 | 前端组 |
| 73 | LOW | 迁徙趋势包含未办结草稿 | FIXED_VERIFIED | 趋势统计限定已办结迁入/迁出；`StatisticsScopeIntegrationTest`；`8509d78`。 | 后端已完成 |
| 74 | LOW | 统计服务/SQL 缺少专属测试 | FIXED_VERIFIED | 已有 `StatisticsServiceTest`、`StatisticsScopeIntegrationTest`、`DashboardMapperTest`；`8509d78`。 | 后端已完成 |
| 75 | CRITICAL | 敏感导出执行借用管理员执行人范围 | FIXED_VERIFIED | 执行时复核申请人当前权限并保留批准范围；`Phase06ExportAuditIntegrationTest` 范围测试；`553f539`、`274517b`。 | 后端已完成 |
| 76 | CRITICAL | Redis 健康探针连接未关闭 | FIXED_VERIFIED | `OptionalRedisService.ping` 使用 try-with-resources；mock 验证成功/异常关闭；`9156378`。 | 后端已完成 |
| 77 | HIGH | 导出 SQL 缺 DEPARTMENT/SELF 分支 | FIXED_VERIFIED | 与 #57 同修复；导出范围集成测试验证；`6541a05`、`553f539`。 | 后端已完成 |
| 78 | HIGH | XLSX 公式注入可由前导空白绕过 | NOT_REPRODUCED | `aec60c4f` 的真实普通及审批后敏感 XLSX 导出经 94 组输入、OOXML、POI 和 Excel 16.0 只读验证，未复现可执行公式注入；没有真实漏洞 RED 或生产修复。CSV 转换、手动编辑和其他 Excel 版本未测试。 [证据](../development/issue78-xlsx-formula-triage.md)。 | 已复核；未声明生产部署 |
| 79 | HIGH | 审计日志可能明文记录身份号/凭据 | FIXED_VERIFIED | 写日志前执行 `auditDetail` 脱敏；`Phase06ExportAuditIntegrationTest.auditMaskingRemovesCredentialsAndSensitiveNumbers`；`3e887f1`。 | 后端已完成 |
| 80 | HIGH | 自定义字典可能绕过居住证类型拦截 | NOT_REPRODUCED | `CertificateService.validate` 明确拒绝 `RESIDENCE_PERMIT` 并要求启用类型；`Phase09CertificateIntegrationTest` 覆盖。 | 后端按现有拦截 |
| 81 | HIGH | 行政区父节点停用状态未校验 | FIXED_VERIFIED | 2026-11-08 精确基线真实 HTTP RED：H2/MySQL 均复现停用父节点下新增 201、迁入 200。新增/更新/启用改为锁定并校验完整 parent_id 祖先链，MySQL 祖先 S / 操作节点 X，当前读重验父指针，保留 version/RBAC/审计事务。新增 H2 21 项、相关专项 78、MySQL 8.4.8 RR 22 项通过；36 组实际行锁等待，未形成非法层级，死锁/超时计数无增长；全量 869（866 通过、原 #34 RED 3 跳过），0 失败/错误。[实现、证据及限制](../development/issue81-83-admin-region-hierarchy.md)。原实现 `f5c3a959` 已进入个人 integration，ChatGPT 独立源码及测试证据复核通过；所有层级写入须遵守 Service 锁定协议，大型子树性能未验证，未自动修复历史异常数据，未验证生产部署。[独立复核报告](../development/issue81-83-independent-review-2026-11-10.md)。 | 后端已复核；未声明生产部署 |
| 82 | MEDIUM | 通用证件过期查询混入有效证件 | FIXED_VERIFIED | `CertificateService.search` 将 EXPIRED 映射到业务状态及日期过滤；`Phase09CertificateIntegrationTest.expiringAndExpiredComputed`。 | 后端已完成 |
| 83 | MEDIUM | 行政区停用未递归检查启用孙节点 | FIXED_VERIFIED | 2026-11-08 精确基线真实 HTTP RED：隔离 A ENABLED→B DISABLED→C ENABLED 夹具下，H2/MySQL 停用 A 均错误 200。停用持操作节点 X 后按 parent_id 队列遍历全部后代，穿过停用节点，当前读遇启用后代即 409；循环/未知状态拒绝，不自动清洗历史数据；后代 NOWAIT 占锁返回 409 并回滚。五级层级、全部停用/叶子、回滚/RBAC、36 组并发与同根分支并行验证通过；专项 H2 78、MySQL RR 22、全量 869（原 3 跳过），0 失败/错误。[实现、证据及限制](../development/issue81-83-admin-region-hierarchy.md)。原实现 `f5c3a959` 已进入个人 integration，ChatGPT 独立源码及测试证据复核通过；所有层级写入须遵守 Service 锁定协议，大型子树性能未验证，未自动修复历史异常数据，未验证生产部署。[独立复核报告](../development/issue81-83-independent-review-2026-11-10.md)。 | 后端已复核；未声明生产部署 |
| 84 | MEDIUM | 操作日志前后端字段名映射错位 | NOT_REPRODUCED | 前端 `operationType`/`operationResult` 与后端 `LogQueryView` 同名字段一致。 | 后端按现有契约 |
| 85 | MEDIUM | 政策问答多轮串行调用累积超时 | NOT_REPRODUCED | `PolicyAssistantService.query` 当前每个问题最多一次 `HttpClient.send`；未发现报告描述的多轮外部请求链。 | 后端按现有实现 |
| 86 | LOW | 数据字典未定义稳定物理排序 | NOT_REPRODUCED | `DataDictionaryMapper.xml` 明确按 `dict_type, sort_no, dict_code` 排序。 | 后端按现有实现 |

## 本轮新增审计结论

- #2：旧实现动态复现并修复；迁移新增人员初始归属，当前申请/户籍数据仍优先决定后续范围。
- #32/#38：MySQL 8.4.8 `REPEATABLE-READ` 实际捕获锁环：签注事务持有居住证主键锁等待 `person`，关闭事务持有 `person` 等待居住证；修复后 10 轮无 1213。
- #39：当前材料契约明确建档与解除相同；未作无依据的业务改动。
- #44：旧实现 RETURNED 状态重复申请返回 201，并发竞态两边均 201；修复后 H2 11 项门禁通过。
- #21：S4-A 已修复并以 H2 集成测试验证；当时 #41、#43 保留 OPEN。2026-11-07 仅 #43 更新为 CODE_CANDIDATE（见第 43 行证据），#41 仍 OPEN。


## 第四批 MySQL 最终门禁补充

- #2 保持 FIXED_VERIFIED：在 MySQL 8.4.8 上从 c6 的 V4_013 旧结构执行 V4_014，旧记录、NULL 新归属和 API/普通导出授权结果已实测；审批代建保留申请人快照。升级后 Verify 曾通过。隔离回滚已核对字段、索引及行数，之后重放迁移成功；重放后的 Verify 未复跑，详见最终验收报告。
- #44 保持 FIXED_VERIFIED：真实 MySQL REPEATABLE-READ 下建档和解除并发分别 10/10 轮为 201/409，未出现双成功；RETURNED 重复申请均 409。显式早期一致性读对照显示 RR 旧快照、RC 可见提交行；该人为序列未在服务入口复现，生产实现未改。
- 独立回归：H2 484/484 通过；MySQL 专项 16/16 通过，0 失败、0 错误、0 跳过。两类数字分别统计，具体测试构成见 2026-11-02-round4-mysql-final-gate.md。
- 本补充只扩充 #2、#44 的 MySQL 证据，不改变 86 项账本编号，也不改变其他事项状态。


## 2026-11-10 独立复核状态核销补充

基线为个人 integration `c49e7531d9aa58d37510a784b02e74101c3f44d5`。历史施工文档中的 CODE_CANDIDATE、尚未集成等为当时阶段性快照；当前权威状态以本账本为准。独立复核不等于部署。本轮仅作文档对账，未重跑历史测试。

| 编号 | 原状态 | 核销状态 | 提交与证据、边界 |
|---|---|---|---|
| 3 | CODE_CANDIDATE | FIXED_VERIFIED | 历史修复 `a1cff942` 经 `c49e753` 的 H2/MySQL 8.4 慢 OCR、连接与锁观测及 UNKNOWN 文件保留验证；ChatGPT 独立复核通过。EXISTING_FIX_CONFIRMED / REVIEW_PASSED / INTEGRATED / CLEANUP_COMPLETED。同步请求仍占用线程，无持久化崩溃恢复协议。 [证据](../development/issue3-ocr-transaction-verification.md) |
| 15 | CODE_CANDIDATE | FIXED_VERIFIED | 家庭户建立、成员入户、迁入及历史恢复日期边界已修复；H2 RED/GREEN、MySQL 并发及全量回归经独立复核。提交 `3f273088`。 [证据](../development/issue15-household-date-boundary.md) |
| 19 | CODE_CANDIDATE | FIXED_VERIFIED | 取消草稿释放有效批次号占位；`d443f30b`、V4_019 条件唯一方案及 H2/MySQL 8.4 并发门禁经独立复核。历史数据升级需备份与受控回填；隔离验证不代表生产已执行 V4_019。 [证据](../development/issue19-migration-batch-reuse.md) |
| 22 | CODE_CANDIDATE | FIXED_VERIFIED | `3268a831` 要求 executeOut 归档受影响行数为 1；零行旧版 RED、H2/MySQL GREEN、回滚与回归经独立复核；-1/2 为模拟返回。 [证据](../development/issue22-archive-affected-rows.md) |
| 34 | CODE_CANDIDATE | FIXED_VERIFIED | `5b95f2be` 按人员、通用业务类型及未结状态排他；真实 HTTP/H2 与 MySQL RR 并发门禁经独立复核。RR 结论依赖当前入口先取得父记录锁再建立一致性快照，不涵盖已有旧快照的外层事务。 [证据](../development/issue34-permit-first-issue-exclusivity.md) |
| 37 | CODE_CANDIDATE | FIXED_VERIFIED | `5d05109e` 使用有界 ID keyset 分页、冻结日期、锁后重验；MySQL 两类各 10000 候选及并发/锁顺序回归经独立复核。分页不消除数据库扫描成本。 [证据](../development/issue37-expiry-keyset-pagination.md) |
| 78 | OPEN | NOT_REPRODUCED | `aec60c4f` 的真实普通及审批后敏感 XLSX 导出经 94 组输入、OOXML、POI 和 Excel 16.0 只读验证，未复现可执行公式注入；没有真实漏洞 RED 或生产修复。CSV 转换、手动编辑和其他 Excel 版本未测试。 [证据](../development/issue78-xlsx-formula-triage.md) |

详见 [独立对账报告](../development/audit-status-reconciliation-2026-11-10.md)。其他 79 项记录不变。
