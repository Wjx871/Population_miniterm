# 邑循 #33-A：同市跨区迁移现行行为审计

状态：`AUDIT_CANDIDATE`。本报告只记录 104f 基线代码和合成 H2 结果；#33 继续保持 `OPEN`，不把候选规则当成课程业务规则。

## 范围与隔离

- 基线：`104f76b3c4e93698f87bf994f87bdd243169a53c`。
- 分支：`feat/20261106-issue33-city-boundary-contract`，独立 worktree。
- 本阶段只新增本报告、业务规则契约和 H2 表征测试；未修改生产代码、Schema、旧审计台账、S4-B 流程或部署文件。
- 动态验证通过真实 Spring MVC、`MigrationService`、MyBatis Mapper 与 SQL，在独立 H2 内存库中完成。测试区划、人员、家庭户及材料均为合成数据。没有连接团队 MySQL、Redis 或 RabbitMQ。
- 环境：Windows 11、JDK 21.0.7、Maven Wrapper 3.9.16；项目以 Java 17 release 编译；H2 2.3.232。测试 URL 显式为独立 `jdbc:h2:mem:issue33_city_boundary`。

## 结论摘要

旧台账 #33 的具体代码描述已不再准确：当前迁移链路不再按 `region_code` 精确查询人员户籍。迁入创建和更新按人员全局检查户籍，执行迁入再次锁定该人员的户籍；H2 `residence.person_id UNIQUE` 与 MySQL V4_003 的 `uk_residence_person(person_id)` 防止同一人保存两行当前户籍。`countActiveResidenceInRegion` 仍存在，但定义和调用在流动人口模块，不在迁移模块。

这不等于 #33 已解决。迁移服务没有用 `admin_region.parent_id` 判断来源与目标的城市归属，也没有根据 `migration_type` 拒绝同市目标或验证同市跨区类型。H2 动态复现显示，跨市关系和批次、客户提供的来源区划可在规则确认前进入草稿；更重要的是，一个类型为 `IN_CITY_CROSS_DISTRICT`、来源与目标实际属于不同合成市的迁出批次，能够完成迁出及迁入。是否构成业务漏洞，仍取决于课程业务方对“同市”、直辖市、县级市等口径的确认。

## 代码核查

### 迁入与当前户籍

- `MigrationMapper.xml` 的 `findResidence` / `lockResidence` 查询条件只有 `person_id=?`，没有 `region_code` 或 `status='ACTIVE'` 过滤。因此它按人全局找记录；任何仍留在 `residence` 表中的该人员记录都会触发迁入重复检查。
- `MigrationService.createIn`、`updateIn` 调用 `findResidence(personId)` 拒绝已登记人员；`executeIn` 再锁定该人员的户籍并拒绝重复登记。创建、更新、执行均对目标家庭户执行 `household.region_code == migration_in.to_region_code` 的精确一致校验（#21 已有覆盖）。
- `src/test/resources/schema.sql` 定义 `residence.person_id UNIQUE`；`V4_003_household_migration_archive.sql` 增加 MySQL 唯一索引 `uk_residence_person(person_id)`。它保证一人最多一行，不判断城市或区县。

### 迁移类型与批次

- `MigrationInRequest.fromRegionCode` 和 `toRegionCode` 都来自客户端。`inValue` 将 `fromRegionCode` 原样存入迁入记录；它不是从当前户籍或迁出批次派生的可信字段。
- `createIn` / `updateIn` 对 `IN_CITY_CROSS_DISTRICT` 只要求 `transferBatchNo` 非空；创建草稿时不查询迁出批次，也不查行政区划树。
- `executeIn.validateBatch` 只核对：迁出批次存在且已完成、迁出人员相同、迁出类型也是 `IN_CITY_CROSS_DISTRICT`、迁出目标代码与迁入目标代码相同、该批次尚无已完成迁入。它不比较迁出来源与迁入来源，不解析来源或目标的父链，不检查区县是否属于同一市。
- `MigrationOutRequest` 没有来源区划字段。`outValue` 的来源区划、家庭户和地址快照来自当前户籍；目标区划来自客户端。`createOut`、`updateOut`、`executeOut` 没有核验迁出类型与来源/目标城市关系。`executeOut` 仅在类型是 `IN_CITY_CROSS_DISTRICT` 时生成迁移批次号。
- `migration_in.transfer_batch_no` 在测试 Schema 中唯一。重复使用已有迁入批次会触发数据库重复键，当前全局异常处理返回 409 并回滚新申请；这阻止了重复行，但错误信息是通用重复提交提示。

### `countActiveResidenceInRegion` 历史与现状

- 全仓 `rg` 显示该符号仅用于 `FloatingResidenceService.executeFloating`，定义位于 `FloatingResidenceMapper.xml`，SQL 为 `person_id=? AND status='ACTIVE' AND region_code=?`。
- `git log --all -S'countActiveResidenceInRegion'` 将其引入定位到 `74418e9`（流动人口/居住证生命周期提交）。当前迁移 Service、Mapper/XML 不调用该符号。
- 审计台账 `doc/testing/2026-11-02-antigravity-audit-ledger.md` 第 41 行仍为 OPEN，但其中把该区划精确查询归到迁移 Mapper 的描述与当前代码不符。该行按要求未改。

### 区划树的数据能力

- `admin_region` 有唯一 `region_code`、可空 `parent_id` 自引用外键、普通整数 `region_level`、`status`、`version`；没有约束把某个层级定义为“市”，也没有约束相邻层级必须差 1。父 ID 外键只能保证父记录存在，不能证明完整、权威或启用的祖先链。
- V4_008 与 H2 Schema 只有示例省、市、区、街道、社区和孤立示例省，没有权威完整区划数据。测试种子里的层级标签只描述合成父子结构，不能外推为业务规则。
- `AdminRegionService` 的启停和父级校验覆盖部分直接父子约束、循环和层级递增；它不生成稳定的城市归属结果，也不确保所有既有后代的完整链均有效。权限树的递归规则用于 DataScope，不等价于迁移“同市”规则。

## 合成 H2 模型

独立测试 `Issue33CrossDistrictCharacterizationIntegrationTest` 构造两省、三市、多个区县，以及街道、社区。节点关系只由 `parent_id` 表达；代码是互不依赖前缀的合成值。还准备了缺失来源代码、停用区县、停用市下的启用子区县、跨层级父子关系，以及目标区划重挂/停用变更。测试使用的 C1/C2/C3 仅是测试夹具标签，不规定真实系统的 `region_level=2` 语义。

## 动态复现

`RED` 临时断言要求跨市 `IN_CITY_CROSS_DISTRICT` 草稿返回 409。真实 HTTP/Service/Mapper/H2 调用实际返回 201，失败信息为 `Status expected:<409> but was:<201>`。随后删除临时失败预期，恢复为验证现行行为的通过断言；未保留失败、禁用或忽略的测试。

| 场景 | 输入与执行 | 实际结果 | SQL / 证据 | 结论类型 |
|---|---|---|---|---|
| 跨市迁入草稿与无效批次 | C1 下 D1 来源代码、C2 下 D3 目标代码；目标家庭代码等于 D3；批次号非空但没有完成的迁出批次 | `createIn` 返回 201；提交/审批后 `executeIn` 返回 409，申请保持 APPROVED，未生成户籍 | 创建只要求批次号非空；执行时 `findCompletedBatch` 找不到 COMPLETED 迁出记录 | B：草稿不验证城市或批次；执行批次存在性校验有效 |
| 同区与缺失来源代码 | 同区 D1→D1，以及不存在于 `admin_region` 的 `991234` 来源代码 | 两个 DRAFT 均可创建 | `MigrationInRequest` 允许来源代码为空或任意长度内字符串；`inValue` 原样存储 | B：缺区划数据不会在创建时被发现 |
| 已有跨区户籍 | 人员当前户籍在 D1，尝试迁入 D2 | `createIn` 返回 409；未创建申请 | `SELECT ... FROM residence WHERE person_id=?`；`residence.person_id UNIQUE`；重复直接 INSERT 抛 H2 `DataIntegrityViolationException` | A：旧的“按区精确查询导致重复落户”已由全局查找与唯一约束消除 |
| `OUTSIDE_CITY` 同市跨区迁出 | D1 与 D2 的合成父链同为 C1；创建 OUTSIDE_CITY，更新草稿、补齐材料、提交、审批并执行 | 创建/更新成功；执行返回 200；迁出完成、归档产生、户籍删除、无 transfer batch | `createOut` 来源从 residence 读取；目标来自请求；`executeOut` 无城市归属校验 | B：类型可完成与“同市不同区”组合；是否违规待规则确认 |
| 完成批次跨市迁入 | D1(C1)→D3(C2) 创建并执行 IN_CITY_CROSS_DISTRICT 迁出；迁入来源由客户端改为 D4(C3)，目标仍 D3 | 迁出批次完成；迁入 DRAFT、更新、提交、审批和执行均成功 | `findCompletedBatch` 只核人员、类型、目标代码与 COMPLETED；不核来源区划/城市祖先。完成后 `countCompletedInBatch=1` | B：不同市迁移实际端到端通过；业务是否允许待确认 |
| 区划变化后继续审批/执行 | 迁入 DRAFT 创建后把 D3 的 parent 从 C2 改到 C3、status 改 DISABLED；随后更新草稿、提交、审批、执行 | 更新和执行仍成功，户籍登记到 D3 | 迁移链路不读取 `admin_region`；仅校验户籍与目标家庭的区划代码相等 | B：草稿、更新、审批和执行未重新核验城市关系或停用状态 |
| 错目标 / 错人员批次 | 完成迁出后分别用不同目标代码、不同人员创建迁入并审批执行 | 创建草稿成功；`executeIn` 返回 409；申请和迁移仍 APPROVED，无新户籍/已完成迁入 | `validateBatch` 的目标代码、人员检查生效，失败后事务无部分写入 | A：现有批次基本匹配与执行原子性仍有效 |
| 已消费批次再提交 | 迁入完成后以另一个人员再次提交相同 batch | 数据库唯一约束映射成 409；新申请行回滚，已完成迁入仍仅一条 | `migration_in.transfer_batch_no UNIQUE`；`findCompletedBatch` / `countCompletedInBatch` SQL | A：重复批次被数据库阻止；错误文案未说明具体批次冲突 |

## 本轮验证结果

- 未修改代码基线：`Phase03MigrationIntegrationTest` 36 项通过，0 失败/错误/跳过。
- 新增表征类：6 项通过，0 失败/错误/跳过。
- 迁移、区划、审批、权限、S4-B 与 S5-A2 组合回归：103 项通过，0 失败/错误/跳过。
- 全量 `mvnw.cmd test`：736 项通过，0 失败/错误/跳过。该结果包括最终的无效批次执行拒绝断言。
- H2 2.3.232 只在本地内存库运行；JDK 21.0.7、Maven Wrapper 3.9.16。未连接团队 MySQL/Redis/RabbitMQ，没有执行真实数据库迁移或部署。

## A/B/C/D 分类

- **A — 旧问题已由后续实现消除：** 迁入按区划精确查 residence 从而允许同一人跨区重复登记的旧描述不适用于当前迁移链。当前实现按人全局查并有唯一约束。现有 `countActiveResidenceInRegion` 仍用于流动人口执行，不代表迁移查询。
- **B — 当前行为已动态复现：** 迁出类型允许与实际同市/跨市目标组合；迁入客户端来源代码不绑定迁出批次来源；草稿/审批/执行不读取行政区划树；跨市的 IN_CITY 批次可以成功迁入。它们是已证实的行为，是否违反业务规则须先确认。
- **C — 需要业务确认：** “同市”的定义、直辖市及县级市归属、允许的行政区粒度、同区/同市不同区的类型、OUTSIDE_CITY 边界、变更后的审批处理、历史数据兼容。
- **D — 潜在风险，未用权威数据复现：** 当前 admin_region 数据不完整，停用祖先、错误层级和真实行政区例外是否会造成课程场景错判，不能从合成树推断。真实历史数据兼容也未验证。

## 事务与边界

本轮复用的 `Phase03MigrationIntegrationTest` 已有真实 H2 回滚点，覆盖迁出归档后失败、删户籍后失败、户主交接后失败、迁入插户籍后失败和版本冲突等情况。`CoreTransactionBoundaryIntegrationTest` 对应 S5-A2 迁入迁出主库事务/连接矩阵。此次新增的批次人员、目标代码负向执行也确认状态、户籍及归档无部分提交。本轮未改写事务边界。

上述均为 H2 证据，不代表 MySQL 锁竞争、真实并发调度、Redis/RabbitMQ 或部署环境验证。

## 结论与下一阶段

审计台账中的旧代码归因应由独立复核者确认是否更新；本轮不改台账。当前不能关闭 #33：存在可重复的跨市批次完成行为，但缺少课程权威规则，无法判断是否应拒绝，也无法安全选定完整的父链解析规则。待业务方确认 `issue33-business-rule-contract.md` 后，再单独实施区划归属解析、来源快照绑定、各阶段复核、迁移脚本（若规则要求持久化）和回归测试。
