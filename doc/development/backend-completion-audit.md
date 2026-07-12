# 后端完成度审计

> 审计基线：`develop@8e6136d891538078af63a8f29bea7fb2d11096bd`（2026-07-12）。本文件只评价当前主后端；`origin/household-migration` 未合并、未作为实现证据。状态必须结合代码、SQL、测试和 API 文档理解。

## 1. 基线

- Java 17 / Spring Boot 3.5.3 / 普通 MyBatis Mapper+XML / Spring Security / `@PreAuthorize` / `ApiResponse<T>`。
- 生产 Java：157；Controller：15；Mapper XML：13。
- MySQL 主脚本表：29；H2 `schema.sql` 表：28。MySQL 有 `key_population`、`data_dictionary`，H2 没有；H2 有早期 `residents`，两者并非同构。
- 增量迁移：6 个（`V4_001` 至 `V4_006`）；另有 1 个主初始化脚本和 3 个演示/检查脚本。
- 基线测试：147，Failures 0，Errors 0，Skipped 0，BUILD SUCCESS，45.147 s。
- 已有 MySQL 报告声称阶段 01—07 验证通过，但本次环境未重新连接真实 MySQL；因此未被现有报告逐项证明的模块不得标为 COMPLETE。

## 2. 模块证据矩阵

| 模块 | 状态 | Controller | Service | Mapper/XML | 数据库 | 权限 | 测试 | MySQL验证 | 缺口 | 证据路径 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 登录认证 | COMPLETE | AuthController | AuthService | AuthMapper/AuthMapper.xml | sys_user/RBAC 表 | 登录放行，其余认证 | 15 用例 | 已有报告 | 无阻塞缺口 | `src/main/java/com/wjx871/population/auth/`; `src/test/java/com/wjx871/population/auth/AuthRbacIntegrationTest.java`; `doc/testing/mysql-verification-report.md` |
| 2 当前用户 | COMPLETE | AuthController `/me` | AuthService | AuthMapper/XML | sys_user | `isAuthenticated()` | 已覆盖 | 已有报告 | 无 | 同上 |
| 3 用户管理 | PARTIAL | 不存在 | AuthService 仅认证读取 | AuthMapper/XML 有查询 | sys_user | 权限码/角色种子存在 | 仅认证测试 | 表已验证 | 无用户 CRUD、禁用、重置密码 API | `src/main/java/com/wjx871/population/auth/`; `doc/database/population_miniterm.sql` |
| 4 角色管理 | PARTIAL | 不存在 | 不存在 | AuthMapper/XML 仅加载角色 | sys_role | 角色种子存在 | 认证间接覆盖 | 表已验证 | 无角色 CRUD/分配 API | 同上 |
| 5 权限管理 | PARTIAL | 不存在 | 不存在 | AuthMapper/XML 只读权限 | sys_permission/sys_role_permission | `@PreAuthorize` 已使用 | 权限矩阵测试 | 已有报告 | 无权限维护 API | `doc/testing/final-permission-matrix.md`; `src/main/java/com/wjx871/population/auth/` |
| 6 部门管理 | PARTIAL | 不存在 | 不存在 | AuthMapper/XML 读取部门 | sys_department | 数据范围依赖部门 | 认证间接覆盖 | 表已验证 | 无部门查询/维护 API | `src/main/java/com/wjx871/population/auth/AuthMapper.java`; `doc/database/population_miniterm.sql` |
| 7 数据范围 | PARTIAL | 各业务 Controller 受权 | 各服务自行判断 | 多个 XML 带 scope | 无独立表 | `DataScopeCriteria` | 主要阶段测试覆盖 | 已有报告 | 实现分散；Resident 早期模型及部分统计/日志需统一复核 | `src/main/java/com/wjx871/population/security/DataScopeCriteria.java`; `src/main/resources/mapper/*.xml` |
| 8 人口基础信息 | DUPLICATE | PersonController、ResidentController | 两套 Service | 两套 Mapper/XML | person、residents | 均有权限 | 两套 Mapper 测试 | person 已验证 | 双模型、重复 API；residents 有物理删除 | `src/main/java/com/wjx871/population/person/`; `src/main/java/com/wjx871/population/resident/`; `src/main/resources/mapper/PersonMapper.xml`; `src/main/resources/mapper/ResidentMapper.xml` |
| 9 身份证严格校验 | MISSING | 不存在专用接口 | 不存在校验服务 | 不适用 | 仅唯一约束 | 不适用 | 不存在 | 未验证 | 只有 `^[0-9Xx]{18}$`，无校验位、日期、性别一致性算法 | `src/main/java/com/wjx871/population/person/PersonRequest.java`; `src/main/java/com/wjx871/population/resident/ResidentRequest.java` |
| 10 家庭户管理 | MISSING | 不存在 | 不存在 | 仅被迁移/注销 Mapper 局部访问 | household | 不存在专用权限入口 | 间接业务测试 | 表已验证 | 无分页、详情、新增、修改 | `doc/database/population_miniterm.sql`; `src/main/java/com/wjx871/population/migration/MigrationMapper.java` |
| 11 家庭成员管理 | MISSING | 不存在 | 不存在 | 仅迁移/注销内部 SQL | household_member | 不存在 | 间接覆盖 | 表已验证 | 无成员查询、加入、移出、关系维护 API | `src/main/resources/mapper/MigrationMapper.xml`; `src/main/resources/mapper/CancellationMapper.xml` |
| 12 户主变更 | MISSING | 不存在 | 注销执行中仅内部变更 | CancellationMapper/XML 局部 SQL | household.head_person_id | 仅注销执行权限 | 注销场景间接覆盖 | 已有报告 | 无独立安全接口、版本控制与完整校验 | `src/main/java/com/wjx871/population/cancellation/CancellationService.java` |
| 13 当前户籍登记 | PARTIAL | 无独立 Controller | 迁移执行时维护 | MigrationMapper/XML | residence | migration 权限 | Phase03 覆盖 | 已有报告 | 缺独立查询/登记维护 API | `src/main/java/com/wjx871/population/migration/`; `doc/api/household-migration-api.md` |
| 14 户籍历史归档 | COMPLETE | ResidenceArchiveController | ResidenceArchiveService | MigrationMapper/XML | residence_archive | archive:view | Phase03 覆盖 | 已有报告 | 无阻塞缺口 | `src/main/java/com/wjx871/population/migration/ResidenceArchiveController.java` |
| 15 迁入申请 | COMPLETE | MigrationController | MigrationService | MigrationMapper/XML | migration_in + application | migration:* | Phase03 | 已有报告 | 无 | `src/main/java/com/wjx871/population/migration/`; `doc/api/household-migration-api.md` |
| 16 迁出申请 | COMPLETE | MigrationController | MigrationService | MigrationMapper/XML | migration_out + application | migration:* | Phase03 | 已有报告 | 无 | 同上 |
| 17 同市跨区迁移 | PARTIAL | 通用迁移接口 | 通用执行逻辑 | MigrationMapper/XML | 迁入/迁出表 | migration:* | 主流程覆盖 | 已有报告 | 无明确原子化“同市跨区”契约与专项测试 | 同上 |
| 18 人口注销 | COMPLETE | CancellationController | CancellationService | CancellationMapper/XML | cancellation_record/archive | cancellation:* | Phase04 | 已有报告 | 无 | `src/main/java/com/wjx871/population/cancellation/`; `doc/api/cancellation-management-api.md` |
| 19 家庭户销户 | COMPLETE | CancellationController/HouseholdArchiveController | CancellationService | CancellationMapper/XML | cancellation_record/household_archive | cancellation:* | Phase04 | 已有报告 | 无 | 同上 |
| 20 流动人口登记 | COMPLETE | FloatingRegistrationController | FloatingResidenceService | FloatingResidenceMapper/XML | floating_* | floating:* | Phase05 | 已有报告 | 无 | `src/main/java/com/wjx871/population/floating/`; `doc/api/floating-residence-permit-api.md` |
| 21 流动人口关闭 | COMPLETE | FloatingRegistrationController | FloatingResidenceService | FloatingResidenceMapper/XML | floating_population | floating:* | Phase05 | 已有报告 | 无 | 同上 |
| 22 居住证申请 | COMPLETE | ResidencePermitController | FloatingResidenceService | FloatingResidenceMapper/XML | residence_permit_application | permit:* | Phase05 | 已有报告 | 无 | 同上 |
| 23 居住证签发 | COMPLETE | ResidencePermitController | FloatingResidenceService | FloatingResidenceMapper/XML | residence_permit | permit:* | Phase05 | 已有报告 | 无 | 同上 |
| 24 居住证签注 | COMPLETE | ResidencePermitController | FloatingResidenceService | FloatingResidenceMapper/XML | residence_permit/log | permit:* | Phase05 | 已有报告 | 无 | 同上 |
| 25 居住证注销 | COMPLETE | ResidencePermitController | FloatingResidenceService | FloatingResidenceMapper/XML | residence_permit/log | permit:* | Phase05 | 已有报告 | 无 | 同上 |
| 26 通用证件管理 | MISSING | 不存在 | 不存在 | 不存在 | certificate 表存在 | 不存在 | 不存在 | 仅表 | 表存在但无 CRUD、数据范围、脱敏、审计与 API 文档 | `doc/database/population_miniterm.sql`; `src/test/resources/schema.sql` |
| 27 重点人口登记 | MISSING | 不存在 | 不存在 | 不存在 | MySQL 有 key_population | 不存在 | 不存在 | 未验证业务 | 无后端实现 | `doc/database/population_miniterm.sql` |
| 28 重点人口解除 | MISSING | 不存在 | 不存在 | 不存在 | 仅 key_population 状态字段 | 不存在 | 不存在 | 未验证 | 无解除工作流 | 同上 |
| 29 重点人口历史 | MISSING | 不存在 | 不存在 | 不存在 | 无 followup/history 表 | 不存在 | 不存在 | 未验证 | 设计文档要求但主脚本缺历史表 | `doc/数据库设计.docx`; `doc/database/population_miniterm.sql` |
| 30 业务申请 | COMPLETE | ApplicationController | ApplicationService/StateMachine | BusinessApplicationMapper/XML | business_application | application:* | Phase02 | 已有报告 | 无 | `src/main/java/com/wjx871/population/application/`; `doc/api/business-application-approval-api.md` |
| 31 申请材料 | COMPLETE | MaterialController | MaterialService | ApplicationMaterialMapper/XML | application_material | material:* | Phase02/业务阶段 | 已有报告 | 无 | `src/main/java/com/wjx871/population/material/` |
| 32 材料核验 | COMPLETE | MaterialController | MaterialService | ApplicationMaterialMapper/XML | application_material | material:verify | Phase02 | 已有报告 | 无 | 同上 |
| 33 审批通过 | COMPLETE | ApprovalController | ApprovalService | ApprovalRequest/Log Mapper+XML | sys_approval_* | approval:handle | Phase02 | 已有报告 | 无 | `src/main/java/com/wjx871/population/approval/` |
| 34 审批驳回 | COMPLETE | ApprovalController | ApprovalService | 同上 | 同上 | approval:handle | Phase02 | 已有报告 | 无 | 同上 |
| 35 显式业务执行 | COMPLETE | 各专业 Controller | 各专业 Service | 各专业 Mapper/XML | 各业务表 | 专项 execute 权限 | Phase03—06 | 已有报告 | 保持“批准后显式 execute” | `doc/development/specialized-business-entry-rules.md` |
| 36 普通导出 | COMPLETE | ExportController | ExportService | ExportMapper/XML | data_export_log | data:export:normal | Phase06 | 已有报告 | 无 | `src/main/java/com/wjx871/population/export/`; `doc/api/export-audit-api.md` |
| 37 敏感导出申请 | COMPLETE | ExportController | ExportService/Workflow | ExportMapper/XML | data_export_request | sensitive:apply | Phase06 | 已有报告 | 无 | 同上 |
| 38 敏感导出审批和执行 | COMPLETE | Export+Approval Controller | ExportService+ApprovalService | 两组 Mapper/XML | request/log/approval | 分离权限 | Phase06 | 已有报告 | 无 | 同上 |
| 39 下载审计 | COMPLETE | ExportController | ExportService | ExportMapper/XML | data_export_log | download 权限 | Phase06 | 已有报告 | 无 | 同上 |
| 40 操作日志 | PARTIAL | StatisticsController `/logs` | OperationLogService/StatisticsService | OperationLogMapper/XML | operation_log | log:view | 多阶段间接覆盖 | 已有报告 | 重大写操作覆盖需静态矩阵证明；日志查询能力有限 | `src/main/java/com/wjx871/population/audit/`; `src/main/java/com/wjx871/population/stats/StatisticsController.java` |
| 41 登录日志 | MISSING | 不存在 | 登录写入通用操作日志 | OperationLogMapper/XML | 无独立登录日志表 | 不适用 | 登录审计间接覆盖 | 未独立验证 | 无登录日志查询/失败原因模型 | `src/main/java/com/wjx871/population/auth/AuthService.java`; `src/main/java/com/wjx871/population/audit/` |
| 42 综合人口查询 | MISSING | 不存在 | 不存在 | 不存在 | 可关联但无查询 | 不存在 | 不存在 | 未验证 | develop 中不存在跨人/户/证/迁移综合查询 | `src/main/java/com/wjx871/population/`（不存在对应包/类） |
| 43 家庭户综合查询 | MISSING | 不存在 | 不存在 | 不存在 | household/member 存在 | 不存在 | 不存在 | 未验证 | 无户档案详情聚合 | 同上 |
| 44 迁移历史查询 | COMPLETE | MigrationController/ResidenceArchiveController | MigrationService/ArchiveService | MigrationMapper/XML | migration/archive | migration/archive 权限 | Phase03 | 已有报告 | 无 | `src/main/java/com/wjx871/population/migration/` |
| 45 首页统计 | PARTIAL | StatisticsController | StatisticsService | StatisticsMapper/XML | 聚合现有表 | statistics:view | 无专门测试 | 未逐口径验证 | 仅 summary/charts，口径与双人口模型需冻结 | `src/main/java/com/wjx871/population/stats/` |
| 46 数据大屏统计 | PARTIAL | 与首页共用 StatisticsController | StatisticsService | StatisticsMapper/XML | 聚合现有表 | statistics:view | 无专门测试 | 未逐口径验证 | 无独立重复 Controller，但统计口径不完整（重点人口/证件缺失） | 同上 |
| 47 行政区划 | MISSING | 不存在 | 不存在 | 不存在 | 无行政区划表，仅 region_code 字段 | 不存在 | 不存在 | 未验证 | 无表、查询 API、级联关系 | `doc/database/population_miniterm.sql`（不存在行政区划表） |
| 48 数据字典 | PARTIAL | 不存在 | 不存在 | 不存在 | MySQL 有 data_dictionary，H2 无 | 不存在 | 不存在 | 未验证 | 无 API；H2 缺表 | `doc/database/population_miniterm.sql`; `src/test/resources/schema.sql` |
| 49 系统健康检查 | COMPLETE | SystemController | 不需要独立 Service | 不适用 | 不适用 | authenticated | 上下文测试间接 | 不适用 | 无 | `src/main/java/com/wjx871/population/system/SystemController.java` |
| 50 数据库初始化、增量升级和演示数据 | PARTIAL | 不适用 | 不适用 | 不适用 | 主脚本+6迁移+演示数据 | 不适用 | H2 启动成功 | 已有报告 | H2/MySQL 不同构；迁移重复执行未由自动化证明；新增模块无迁移 | `doc/database/`; `src/test/resources/schema.sql`; `doc/testing/mysql-verification-report.md` |

## 3. 状态汇总与估算

- COMPLETE：25；PARTIAL：12；MISSING：12；BROKEN：0；DUPLICATE：1；OUT_OF_SCOPE：0。
- 估算完成度：约 **63%**。算法：COMPLETE=1、PARTIAL=0.5、DUPLICATE=0.25、MISSING/BROKEN=0，除以 50；这是规划权重，不是测试覆盖率。
- 未发现“代码存在但当前基线无法运行”的 BROKEN 模块。高级能力在范围冻结文件中整体列为 V1 外，不占上述 50 项。

## 4. 已知疑点核实结论

1. 不存在 `HouseholdController`；家庭户分页、详情、新增、修改、成员维护、独立户主变更均不存在。
2. 重点人口仅 MySQL 主脚本有 `key_population`；Controller、Service、Mapper、XML、H2 表、测试和 API 文档均不存在。
3. `certificate` 只有表，无通用 CRUD。
4. Person/Resident 身份证只做 18 位正则和唯一性，没有 GB 11643 校验位、日期合法性及性别/生日一致性校验。
5. 无行政区划表/API；数据字典只有 MySQL 表，无接口且 H2 缺表。
6. develop 无综合查询。
7. Dashboard/Statistics 未发现两套 Controller；只有 `StatisticsController`，但首页与大屏共用且口径不完整。
8. `person` 与 `residents` 双模型确实存在，两个 Controller、Service、Mapper/XML 和测试并行；`ResidentMapper.deleteById` 为物理删除，违背核心人口不物理删除原则。
9. README 对已完成阶段 01—07 大体准确，但不能证明家庭户、证件、重点人口、区划、字典、综合查询已经完成。
10. H2 与 MySQL 主脚本不一致：H2 有 `residents`，缺 `key_population` 和 `data_dictionary`；约束/索引也不是逐项同构。
11. 6 个迁移使用大量 `IF NOT EXISTS`，但重复执行安全性没有自动化测试证据；DML/约束变更仍需真实 MySQL 连续两次执行验证。
12. 15 个 Controller 的显式路由均有认证或权限保护；登录端点按设计匿名。是否所有查询正确执行数据范围仍需 Phase 11 静态矩阵和越权测试收口。
13. 审批后显式执行规则已实现，不应引入第二套审批门或状态机。

## 5. 证据边界

- Word 需求与数据库设计将家庭户、人口、迁入迁出、证件列为核心/必做，将流动人口、重点人口、组合查询列为重要范围；因此这些缺口不能因 README 未强调而移出 V1。
- 设计中的真实制卡、政务联网、短信、邮件、多级审批、消息队列、微服务、云存储、分布式缓存/事务不属于课程 V1。
- `origin/household-migration` 或其他独立后端即使含相似类，也属于 E 类证据：存在但不得吸收，当前 develop 是唯一事实来源。
