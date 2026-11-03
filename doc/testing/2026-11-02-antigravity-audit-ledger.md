# Antigravity 原始审计 86 项逐条对账

基线：`c6caa2d87038fd728772699a7af643931852c28a`。原始报告保留在 `D:\学习用\大二下\小学期\.agents\teamwork\orchestrator\SYSTEM_CORE_BUSINESS_AUDIT_REPORT.md`，本文件不覆盖该报告。

状态仅使用任务约定的六种值。除标明“本轮动态复现”的项目外，结论来自当前源码、既有集成测试、已审查提交或 API/前端契约；没有把未运行的场景写成动态通过。`FRONTEND_HANDOFF` 项本轮没有改动前端。

| # | 原级别 | 原问题摘要 | 最新状态 | 复核依据、复现与关联提交 | 后续责任方 |
|---:|---|---|---|---|---|
| 1 | CRITICAL | 普通编辑可能复活注销或死亡人员 | FIXED_VERIFIED | `PersonService` 不再把客户端 status 写回；`Phase08PersonIdentityIntegrationTest.ordinaryEditsPreserveCancelledAndDeceasedLifecycleStates` 与本轮 `Phase08PersonIdentityIntegrationTest` 覆盖显式 status 绕过；`6541a05`。 | 后端已完成 |
| 2 | CRITICAL | REGION 建档后人员无法由创建人读取 | FIXED_VERIFIED | 本轮旧实现复现：201 创建后同区详情 403、列表/导出为 0；修复后同区、异区、ALL 与导出断言通过；`PersonRegionOwnershipAuditIntegrationTest`，`aff3f8e`，迁移 `V4_014`。 | 后端已完成 |
| 3 | HIGH | OCR 远程请求发生在长事务内 | OPEN | `PersonIdCardImageService` 上传流程仍同步调用 OCR；本轮未改、未对外部 OCR 做时序压测。 | 后端 |
| 4 | HIGH | 脱敏回显值可能被编辑表单写回 | FRONTEND_HANDOFF | 后端保留/忽略脱敏字段的兼容逻辑已在 `PersonService`；编辑表单回填与提交仍需前端验证。 | 前端组 |
| 5 | MEDIUM | 性别编码与前端字典不一致 | FRONTEND_HANDOFF | 关联前端表单/字典映射；本轮未改前端。 | 前端组 |
| 6 | MEDIUM | 影印本 SHA 去重可跨用户占用 | FIXED_VERIFIED | `PersonIdCardImageService` 按对象归属拒绝跨用户读取/覆盖；`Phase14PersonIdCardOcrIntegrationTest.duplicateFromAnotherUploaderCannotReadOrChangeOriginalDraftOrFileReference`；`dfc5f81`。 | 后端已完成 |
| 7 | LOW | OCR 敏感字段切片逻辑脆弱 | OPEN | OCR 解析仍依赖当前切片/格式假设；本轮未改，也未覆盖更多供应商返回格式。 | 后端 |
| 8 | LOW | 前端适配器导入路径缺扩展名 | FRONTEND_HANDOFF | 前端模块解析/构建行为需前端组核验；本轮未改前端。 | 前端组 |
| 9 | CRITICAL | 多个 ACTIVE HEAD 导致户主变更 selectOne 崩溃 | FIXED_VERIFIED | 新增 HEAD 关系走户主变更入口；历史重复 HEAD 被安全拒绝；`Phase08HouseholdMasterDataIntegrationTest`；`45b5ac1`。 | 后端已完成 |
| 10 | HIGH | 成员离户日期早于入户日期仍可成功 | FIXED_VERIFIED | `HouseholdService.leave` 校验日期；`leaveBeforeJoinDateIsRejectedWithoutMutation` 与相等日期正常路径测试；`45b5ac1`。 | 后端已完成 |
| 11 | HIGH | 空户/无户主最后成员离户后不能销户 | FIXED_VERIFIED | 直接离户和迁出执行均核验有效成员、户籍及在途迁入后设置待销户；H2 与 MySQL 专项覆盖；`0750ce9`、`9368ac1`。 | 后端已完成 |
| 12 | HIGH | 家庭关系枚举文案及户主显示回退异常 | FRONTEND_HANDOFF | 属关系标签和展示回退；本轮未改前端。 | 前端组 |
| 13 | MEDIUM | 家庭户表单重复渲染控件 | FRONTEND_HANDOFF | 视图布局问题；本轮未改前端。 | 前端组 |
| 14 | MEDIUM | 前端拦截器吞掉 409 业务冲突提示 | FRONTEND_HANDOFF | API 错误提示呈现需前端核验；本轮未改前端。 | 前端组 |
| 15 | MEDIUM | 立户日期与成员入户日期边界未校验 | OPEN | 当前创建/更新路径未见统一的立户日期与初始成员日期约束；本轮未动态复现。 | 后端 |
| 16 | LOW | 详情刷新未 await 导致竞态 | FRONTEND_HANDOFF | 页面异步刷新行为；本轮未改前端。 | 前端组 |
| 17 | LOW | 家庭户适配器重复定义 regionCode | FRONTEND_HANDOFF | 前端适配器实现问题；本轮未改前端。 | 前端组 |
| 18 | CRITICAL | 户主迁出申请缺少继任者导致批准后无法执行 | FIXED_NOT_VERIFIED | 新申请在提交时校验继任者，`Phase03MigrationIntegrationTest` 验证新流；已批准但缺少继任者的历史单仍需补正/重新审批方案；`eabae6a`。 | 后端后续处理历史单 |
| 19 | HIGH | 跨区迁移批次号全局唯一使取消草稿占号 | OPEN | 批次号生成与表唯一约束仍未按已取消草稿释放；本轮未改。 | 后端 |
| 20 | HIGH | 户主交接只改主表指针未更新成员 HEAD 关系 | FIXED_VERIFIED | `MigrationService.executeOut` 同事务更新新户主关系与家庭户指针；迁出交接测试覆盖；`eabae6a`。 | 后端已完成 |
| 21 | MEDIUM | 目标家庭户与迁入区划不一致 | OPEN | 目标家庭户现在通过 `lockActiveHousehold` 加锁；但 `executeIn` 仍未比较 `household.region_code` 与 `migration_in.to_region_code`。本轮静态核对，未改该项。 | 后端 |
| 22 | MEDIUM | 户籍归档写入未检查受影响行数 | OPEN | `MigrationService` 调用归档写入后未检查插入影响行数；本轮未改。 | 后端 |
| 23 | MEDIUM | 迁出表单缺少新户主选择与同意材料 | FRONTEND_HANDOFF | 前端申请表单/材料配置需核验；新后端提交门禁已要求继任者；本轮未改前端。 | 前端组 |
| 24 | LOW | 迁出区划与归档查询类型交互不规范 | FRONTEND_HANDOFF | 查询控件绑定问题；本轮未改前端。 | 前端组 |
| 25 | CRITICAL | 人员编辑接口隐式复活注销/死亡状态 | FIXED_VERIFIED | 与 #1 同根因；服务端状态白名单与 Person Mapper 不写 status；人口生命周期测试通过；`6541a05`。 | 后端已完成 |
| 26 | HIGH | 注销提交门禁调用 bad 但未 throw | FIXED_VERIFIED | `CancellationSubmissionValidator` 已实际抛出门禁异常；`Phase04CancellationIntegrationTest` 覆盖非法注销拒绝与正常申请；`6541a05`。 | 后端已完成 |
| 27 | HIGH | 注销草稿列表快照为空且范围过滤错误 | FIXED_VERIFIED | 创建/更新时生成可信快照，查询与 count 使用一致权限条件；`Phase04CancellationIntegrationTest` 覆盖；`be12635`。 | 后端已完成 |
| 28 | MEDIUM | 注销查询错误复用 SELF 归档范围规则 | FIXED_VERIFIED | 注销列表 SELF 按本人申请授权，归档仍按既有规则拒绝 SELF；列表/详情测试通过；`be12635`。 | 后端已完成 |
| 29 | MEDIUM | 空户销户原户主快照缺失 | OPEN | 无户主家庭户本来就没有户主；当前销户快照未形成可靠的历史原户主快照字段/来源；本轮未改。 | 后端/产品 |
| 30 | LOW | 注销状态监听器未映射 RETURNED | OPEN | `CancellationStatusListener` 将状态直接 `CancellationStatus.valueOf`，而枚举无 `RETURNED`；退回该业务可能抛异常；本轮未改。 | 后端 |
| 31 | CRITICAL | SELF 可绕过归属校验读取他人流动/居住证 | FIXED_VERIFIED | 服务层按来源申请人校验 SELF；`FloatingSelfScopeAuditIntegrationTest` 覆盖本人允许、他人拒绝及列表/详情；`6541a05`。 | 后端已完成 |
| 32 | HIGH | 流动关闭与居住证签注锁顺序形成 MySQL 死锁 | FIXED_VERIFIED | 本轮旧实现捕获 8.4.8 `1213/40001` 等待环；修复后 MySQL 10/10 轮关闭 200、签注 409、死锁计数不增；`MySql84CriticalAcceptanceIT.closeThenEndorseLocksFloatingBeforePermitWithoutDeadlock`，`2bfa038`。 | 后端已完成 |
| 33 | HIGH | 同市跨区户籍冲突只按区划精确相等判断 | OPEN | `countActiveResidenceInRegion` 仍按 `region_code=#{region}` 精确匹配；同城下级区划冲突未覆盖；本轮未改。 | 后端 |
| 34 | HIGH | 居住证申请缺少在途排他检查 | OPEN | 首次申领路径检查当前 ACTIVE 证件，但未按人员/流动登记拒绝已有同类未结申请；本轮未改。 | 后端 |
| 35 | MEDIUM | 居住证到期预警页签复用停滞 | FRONTEND_HANDOFF | 前端 Tab/列表刷新问题；本轮未改前端。 | 前端组 |
| 36 | MEDIUM | 申领弹窗可选不合资格人员 | FRONTEND_HANDOFF | 候选项筛选属前端；后端执行仍校验有效登记与申领日期；本轮未改前端。 | 前端组 |
| 37 | MEDIUM | 居住证到期任务全量加载结果集 | OPEN | `ResidencePermitExpiryService` 仍批量读取待处理 ID 后逐条处理，无分页；本轮未改。 | 后端 |
| 38 | LOW | 关闭流动登记与签注的级联锁不一致 | FIXED_VERIFIED | 与 #32 同一等待环；锁流动登记先于居住证行，`lockPermit` 不再锁 JOIN 表；同一 MySQL 十轮门禁通过；`2bfa038`。 | 后端已完成 |
| 39 | HIGH | 解除申请被错误要求提交建档依据 | NOT_REPRODUCED | 当前 API 文档明确解除材料规则同建档；前端建档/解除共用必需材料与核验 helper；`Phase10KeyPopulationIntegrationTest.releaseMissingMaterials` 验证缺材料拒绝。无业务代码改动。 | 后端按现有契约 |
| 40 | HIGH | 重点人口/统计状态值不匹配导致统计恒为零 | FIXED_VERIFIED | 统计 SQL 使用业务 ACTIVE 状态并传入范围；`StatisticsScopeIntegrationTest`、`DashboardMapperTest`；`8509d78`。 | 后端已完成 |
| 41 | HIGH | 注销人员的重点人口记录不能由 REGION 用户解除 | OPEN | `KeyPopulationWorkflow.validate` 使用 `personScopedStatus`；`KeyPopulationMapper.xml` 的 REGION `personScope` 仅查 residence，而注销流程会移除当前户籍。当前为静态复核，未做端到端动态复现或修复。 | 后端 |
| 42 | HIGH | 建档执行后未把生成记录 ID 写回申请详情 | NOT_REPRODUCED | `key_population` 保存 `source_application_id`，可由申请反查生成记录；现有设计把 `key_population_application.record_id` 用于解除目标。未找到接口契约要求反向回填，现有执行与历史测试可按 source app 关联。 | 后端/产品仅在契约变化时处理 |
| 43 | MEDIUM | 通用提交路由可能绕过重点人口专属 apply 权限 | OPEN | 专业提交路由同时检查 `key-population:apply`；通用 `/api/applications/{id}/submit` 仅检查 `application:submit`，需核实自有旧草稿在权限撤销后的提交策略；本轮静态复核，未改。 | 后端/权限产品 |
| 44 | MEDIUM | 在途重点人口重复申请查询遗漏 RETURNED | FIXED_VERIFIED | 旧 H2 复现 RETURNED 建档/解除重复请求都返回 201，并复现并发双 201；现已计入 RETURNED、对人员/记录父行加锁；11 项 Phase10 测试通过；`8d78575`。 | 后端已完成 |
| 45 | MEDIUM | 重点人口人员区划关联未限定 ACTIVE 户籍 | OPEN | `KeyPopulationMapper.xml` 范围/区划子查询仍可读取非 ACTIVE residence；本轮静态核对，未改。 | 后端 |
| 46 | LOW | 重点人口建档日期没有禁用日期范围 | FRONTEND_HANDOFF | `KeyPopulationRegisterCreate.vue` 日期选择交互；本轮未改前端。 | 前端组 |
| 47 | LOW | 解除创建页直接显示英文枚举 | FRONTEND_HANDOFF | `KeyPopulationReleaseCreate.vue` 展示文案；本轮未改前端。 | 前端组 |
| 48 | LOW | 历史列表直接展示操作人数字 ID | FRONTEND_HANDOFF | `KeyPopulationHistory.vue` 文案/显示映射；本轮未改前端。 | 前端组 |
| 49 | CRITICAL | RETURNED 不能补传/删除材料导致二次审批卡死 | FIXED_VERIFIED | 材料服务允许 DRAFT/RETURNED 并校验归属；`Phase12ApplicationReturnIntegrationTest` 覆盖退回、补材、复核、复审；`9b5081b`。 | 后端已完成 |
| 50 | CRITICAL | RETURNED 申请栏没有重新提交与继续编辑入口 | FIXED_NOT_VERIFIED | `ApplicationActionBar.vue` 已有 RETURNED 提交动作；未找到前端交互测试，且迁移 Handler 路由组合仍需前端确认；本轮未改前端。 | 前端补测/核验 |
| 51 | HIGH | 材料核验缺少申请人/上传者防自审 | FIXED_VERIFIED | 服务端拒绝申请人及上传者自审；重点人口门禁与 Phase03 核验测试通过；`a1840f2`。 | 后端已完成 |
| 52 | MEDIUM | REGION 申请列表不能按授权下级区划查询 | FIXED_VERIFIED | 审批与申请 Mapper 使用授权区划集合；`Phase02ApprovalIntegrationTest.approverSeesChildRegionButOtherRegionDoesNot`；`3caaeca`。 | 后端已完成 |
| 53 | MEDIUM | 撤回/取消按钮未校验申请人归属 | FRONTEND_HANDOFF | 前端按钮可见条件与错误处理需核验；后端仍校验归属；本轮未改前端。 | 前端组 |
| 54 | LOW | 取消对话框 Promise rejection 未捕获 | FRONTEND_HANDOFF | 前端 Promise 异常处理；本轮未改前端。 | 前端组 |
| 55 | LOW | 草稿 DELETE 缺少客户端版本号 | OPEN | `ApplicationController.delete` 仍不接收版本；本轮未改。 | 后端 |
| 56 | CRITICAL | MainLayout 缺失 resolveLandingPath 导入 | FRONTEND_HANDOFF | 前端导航/导入问题；本轮未改前端，未运行前端构建。 | 前端组 |
| 57 | CRITICAL | 普通/敏感导出 SQL 漏 DEPARTMENT/SELF 过滤 | FIXED_VERIFIED | 导出 SQL 覆盖 SELF/部门/区划/ALL；`ExportDataScopeAuditIntegrationTest` 行范围断言；`6541a05`、`553f539`。 | 后端已完成 |
| 58 | HIGH | REGION 使用固定 substring 导致区县越权 | FIXED_VERIFIED | 授权区划树解析为完整代码集合并精确匹配；区域隔离集成测试通过；`3caaeca`。 | 后端已完成 |
| 59 | HIGH | 迁移归档 SELF 详情逻辑短路 | FIXED_VERIFIED | 归档明确拒绝 SELF，列表/详情语义一致；`Phase03MigrationIntegrationTest`；`3caaeca`。 | 后端已完成 |
| 60 | HIGH | 注销列表缺少部门过滤 | FIXED_VERIFIED | 列表与 count 按 SELF/部门/授权区划一致过滤；`Phase04CancellationIntegrationTest`；`be12635`。 | 后端已完成 |
| 61 | HIGH | 过期 Bearer Token 阻断匿名登录 | FIXED_VERIFIED | JWT filter 豁免精确公开登录路径；失效 Token 登录可继续，受保护 API 仍 401；`AuthRbacIntegrationTest`，`0d84620`。 | 后端已完成 |
| 62 | MEDIUM | 业务申请和审批区划判定曾分裂 | FIXED_NOT_VERIFIED | 两 Mapper 当前均使用授权 `regionCodes`；无直接逐行比较边界的独立测试。`3caaeca`。 | 后端补充一致性测试 |
| 63 | MEDIUM | 统计接口未使用调用者数据范围 | FIXED_VERIFIED | 统计 Service/Mapper 传递 `DataScopeCriteria`；部门/SELF/REGION/ALL 测试通过；`8509d78`。 | 后端已完成 |
| 64 | MEDIUM | 本地 Token 黑名单无主动清理/分布式同步 | OPEN | Redis 不可用时本地 Map 仍依赖重复 JTI 查询才清理；本轮未改。 | 后端/运维 |
| 65 | LOW | 会话恢复初次挂载可能丢角色权限 | FIXED_NOT_VERIFIED | `user.js` 现会恢复持久化角色/权限并核验 `/auth/me`；未找到 UI 初次挂载闪烁专测。 | 前端补测 |
| 66 | CRITICAL | 统计 SQL 用中文状态与标准状态不匹配 | FIXED_VERIFIED | 统计查询使用 ACTIVE 等业务枚举；统计集成测试验证合成 ACTIVE 记录；`8509d78`。 | 后端已完成 |
| 67 | HIGH | 综合档案抽屉 prop 名与组件声明失配 | FRONTEND_HANDOFF | `ComprehensiveProfileDrawer.vue`/`DetailDrawer` 绑定需前端核验；本轮未改前端。 | 前端组 |
| 68 | HIGH | 综合查询未关联流动登记/居住证/迁移字段 | OPEN | `Phase11QueryMapper` 当前结果列仍缺少对应联表字段；本轮未改。 | 后端 |
| 69 | HIGH | 迁移历史方向参数绑定到迁移类型 | FRONTEND_HANDOFF | 前端方向查询参数需与后端契约核对；本轮未改前端。 | 前端组 |
| 70 | MEDIUM | 统计日志明细可能返回未脱敏 detail | OPEN | `StatisticsMapper.selectRecentLogs` 直接选出 `l.detail`，Service 未二次脱敏；本轮未改。 | 后端 |
| 71 | MEDIUM | 综合查询 REGION 仅按户籍过滤漏流动人口 | OPEN | `Phase11QueryMapper` REGION 分支仍需确认并纳入流动人口授权区划；本轮未改。 | 后端 |
| 72 | MEDIUM | 零人口圆环图渲染异常 | FRONTEND_HANDOFF | 图表空数据展示；本轮未改前端。 | 前端组 |
| 73 | LOW | 迁徙趋势包含未办结草稿 | FIXED_VERIFIED | 趋势统计限定已办结迁入/迁出；`StatisticsScopeIntegrationTest`；`8509d78`。 | 后端已完成 |
| 74 | LOW | 统计服务/SQL 缺少专属测试 | FIXED_VERIFIED | 已有 `StatisticsServiceTest`、`StatisticsScopeIntegrationTest`、`DashboardMapperTest`；`8509d78`。 | 后端已完成 |
| 75 | CRITICAL | 敏感导出执行借用管理员执行人范围 | FIXED_VERIFIED | 执行时复核申请人当前权限并保留批准范围；`Phase06ExportAuditIntegrationTest` 范围测试；`553f539`、`274517b`。 | 后端已完成 |
| 76 | CRITICAL | Redis 健康探针连接未关闭 | FIXED_VERIFIED | `OptionalRedisService.ping` 使用 try-with-resources；mock 验证成功/异常关闭；`9156378`。 | 后端已完成 |
| 77 | HIGH | 导出 SQL 缺 DEPARTMENT/SELF 分支 | FIXED_VERIFIED | 与 #57 同修复；导出范围集成测试验证；`6541a05`、`553f539`。 | 后端已完成 |
| 78 | HIGH | XLSX 公式注入可由前导空白绕过 | OPEN | `ExportFileService.cell` 仍需检查去空白后的公式前缀；本轮未改。 | 后端 |
| 79 | HIGH | 审计日志可能明文记录身份号/凭据 | FIXED_VERIFIED | 写日志前执行 `auditDetail` 脱敏；`Phase06ExportAuditIntegrationTest.auditMaskingRemovesCredentialsAndSensitiveNumbers`；`3e887f1`。 | 后端已完成 |
| 80 | HIGH | 自定义字典可能绕过居住证类型拦截 | NOT_REPRODUCED | `CertificateService.validate` 明确拒绝 `RESIDENCE_PERMIT` 并要求启用类型；`Phase09CertificateIntegrationTest` 覆盖。 | 后端按现有拦截 |
| 81 | HIGH | 行政区父节点停用状态未校验 | OPEN | `AdminRegionService.parent` 仍未检查父节点启用状态；本轮未改。 | 后端 |
| 82 | MEDIUM | 通用证件过期查询混入有效证件 | FIXED_VERIFIED | `CertificateService.search` 将 EXPIRED 映射到业务状态及日期过滤；`Phase09CertificateIntegrationTest.expiringAndExpiredComputed`。 | 后端已完成 |
| 83 | MEDIUM | 行政区停用未递归检查启用孙节点 | OPEN | `AdminRegionService.disable` 仍只检查直接子节点；本轮未改。 | 后端 |
| 84 | MEDIUM | 操作日志前后端字段名映射错位 | NOT_REPRODUCED | 前端 `operationType`/`operationResult` 与后端 `LogQueryView` 同名字段一致。 | 后端按现有契约 |
| 85 | MEDIUM | 政策问答多轮串行调用累积超时 | NOT_REPRODUCED | `PolicyAssistantService.query` 当前每个问题最多一次 `HttpClient.send`；未发现报告描述的多轮外部请求链。 | 后端按现有实现 |
| 86 | LOW | 数据字典未定义稳定物理排序 | NOT_REPRODUCED | `DataDictionaryMapper.xml` 明确按 `dict_type, sort_no, dict_code` 排序。 | 后端按现有实现 |

## 本轮新增审计结论

- #2：旧实现动态复现并修复；迁移新增人员初始归属，当前申请/户籍数据仍优先决定后续范围。
- #32/#38：MySQL 8.4.8 `REPEATABLE-READ` 实际捕获锁环：签注事务持有居住证主键锁等待 `person`，关闭事务持有 `person` 等待居住证；修复后 10 轮无 1213。
- #39：当前材料契约明确建档与解除相同；未作无依据的业务改动。
- #44：旧实现 RETURNED 状态重复申请返回 201，并发竞态两边均 201；修复后 H2 11 项门禁通过。
- #21、#41、#43：本轮发现原报告之外仍有未闭环风险，均保留 OPEN，没有顺手修改。


## 第四批 MySQL 最终门禁补充

- #2 保持 FIXED_VERIFIED：在 MySQL 8.4.8 上从 c6 的 V4_013 旧结构执行 V4_014，旧记录、NULL 新归属和 API/普通导出授权结果已实测；审批代建保留申请人快照。升级后 Verify 曾通过。隔离回滚已核对字段、索引及行数，之后重放迁移成功；重放后的 Verify 未复跑，详见最终验收报告。
- #44 保持 FIXED_VERIFIED：真实 MySQL REPEATABLE-READ 下建档和解除并发分别 10/10 轮为 201/409，未出现双成功；RETURNED 重复申请均 409。显式早期一致性读对照显示 RR 旧快照、RC 可见提交行；该人为序列未在服务入口复现，生产实现未改。
- 独立回归：H2 484/484 通过；MySQL 专项 16/16 通过，0 失败、0 错误、0 跳过。两类数字分别统计，具体测试构成见 2026-11-02-round4-mysql-final-gate.md。
- 本补充只扩充 #2、#44 的 MySQL 证据，不改变 86 项账本编号，也不改变其他事项状态。
