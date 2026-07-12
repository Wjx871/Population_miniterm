# Phase 08 实施任务书：人口主模型与家庭户主数据收口

> 本文件是下一次开发的执行输入。本次审计不实现任何条目。

## 1. 明确范围

1. 冻结 `person` 为唯一 V1 人口主模型；盘点 `residents` 数据和调用方，制定兼容读取、数据迁移、API 退役方案。
2. 实现严格身份证校验和统一手机号校验。
3. 实现家庭户分页、详情、创建、修改（不物理删除）。
4. 实现成员列表、加入、移出和与户主关系维护。
5. 实现带乐观版本和事务保护的户主变更。
6. 提供当前户籍查询，复用已有 `residence`，不重写迁入迁出。

## 2. 不做什么

- 不改前端；不做前端联调。
- 不实现重点人口、证件、区划、字典、综合查询或新统计。
- 不重写审批、迁入迁出、注销、流动人口、居住证、导出。
- 不引入 MyBatis-Plus、第二 JWT、Redis、`Result<T>`、`ApprovalGate`、第二状态机。
- 不自动执行已批准业务；不物理删除 person/household；不直接删除 residents 表或其数据。

## 3. 先决数据审计

- 统计 person/residents 行数、身份证交集/冲突、字段缺失、被其他表引用情况。
- 统计一个人多个 ACTIVE household_member、户主不是 ACTIVE 成员、一个户多个有效户主、region 不一致等脏数据。
- 先输出修复清单，再添加约束。发现无法自动判定的冲突时停止迁移并人工确认，禁止“最后写入覆盖”。

## 4. 数据库变更

- 新建 `doc/database/migrations/V4_007_person_household_master_data.sql`。
- 同步 `doc/database/population_miniterm.sql`、`src/test/resources/schema.sql` 和演示数据。
- 预期字段/约束：household 乐观锁 `version`（若缺）；household_member 关系状态/有效期/版本；person 当前有效家庭归属唯一性采用 MySQL 可落地的生成列唯一索引或事务+锁方案，先验证兼容性。
- 索引：household_no 唯一；region_code/status；member(household_id,status)、member(person_id,status)；不得创建无查询依据的索引。
- residents 退役采用两阶段：本阶段禁止新写并迁移可判定数据，保留只读兼容或明确 410/废弃策略；物理删表另行审批。

## 5. Java 文件规划

- 新增 `household` 包：`HouseholdController`, `HouseholdService`, `HouseholdMapper`, Entity/View/Request DTO、成员与户主变更 DTO。
- 新增 `validation` 或 person 包内单一 `ChineseIdentityNumberValidator`，包含权重/校验码、出生日期、顺序码与性别解析；提供 Bean Validation 注解或服务方法，但只能有一套算法。
- 修改 Person 请求/服务以调用统一校验；Resident 旧入口不得继续形成第二写路径。
- 复用 `CurrentUserContext`、`DataScopeCriteria`、`SensitiveDataMaskingService`、`OperationLogService`、`BusinessException`、`ApiResponse<T>`。
- 不修改应用主类。

## 6. Mapper XML

- 新增 `src/main/resources/mapper/HouseholdMapper.xml`，所有列显式映射。
- 分页须稳定排序；列表/详情 SQL 在数据库层应用 scope。
- 写操作使用版本和状态条件，更新行数 0 转换为冲突；成员/户主变更使用 `SELECT ... FOR UPDATE` 锁住户和相关成员。
- 不在 Java 内查全量后过滤；不拼接未经白名单处理的 ORDER BY。

## 7. API 列表

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| GET | `/api/households` | 按户号、户主、地址、region、status 分页 | `household:view` |
| GET | `/api/households/{id}` | 户档案、户主、当前户籍摘要 | `household:view` |
| POST | `/api/households` | 立户 | `household:create` |
| PUT | `/api/households/{id}` | 修改非状态主数据，携带 version | `household:update` |
| GET | `/api/households/{id}/members` | 当前/历史成员分页或列表 | `household:view` |
| POST | `/api/households/{id}/members` | 加入成员 | `household:member:manage` |
| POST | `/api/households/{id}/members/{personId}/remove` | 移出成员，含原因/version | `household:member:manage` |
| PUT | `/api/households/{id}/head` | 原户主、新户主、version 原子变更 | `household:head:change` |
| GET | `/api/persons/{id}/residence` | 当前户籍及所属户 | `population:view` |

不提供 DELETE 家庭户或人口 API；销户继续使用既有注销流程。

## 8. 权限码与种子

- 新增上述 5 个 household 权限码，通过 V4_007 幂等插入并分配给合理角色。
- `household:view` 与写权限分离；管理员不因角色名绕过数据范围。
- 所有端点增加未认证、缺权限和有权限测试。

## 9. 状态值

- household：沿用数据库已用值并冻结为 `ACTIVE`, `PENDING_CANCELLATION`, `CANCELLED`（如当前实际值不同，先兼容迁移，禁止无迁移改名）。
- household_member：`ACTIVE`, `MOVED_OUT`, `CANCELLED`；历史记录不可覆盖删除。
- person：沿用 `PersonStatus`；不得由普通家庭户修改随意改成迁入/迁出/注销。

## 10. 业务校验

- 身份证 18 位、前 17 位数字、末位校验码正确；出生日期真实且与 DTO 一致；顺序码性别与 DTO 一致；身份证唯一。
- 手机号统一规则，空值策略明确，不允许 Person/Resident 两套规则。
- 户号唯一；户地址/类型/region 必填和值域受控。
- 新户主必须是该户 ACTIVE 成员且 person 未注销；旧户主必须匹配当前值。
- 一个 person 同时最多一个 ACTIVE household_member；加入前锁定 person 当前关系。
- 移出户主前必须先在同一事务指定新户主，或拒绝操作；最后成员移出应走销户/注销规则。
- 与未完成迁移/注销申请冲突时拒绝直接成员变更。

## 11. 数据范围、脱敏、审计

- ALL 可跨区；DEPARTMENT/REGION 仅本部门/区域；SELF 不允许家庭户批量检索，除非明确能映射本人。
- 创建与修改对象 region 必须在用户范围；详情不能只靠 ID 绕过范围。
- 身份证、手机、详细地址默认脱敏；完整查看仅 `sensitive-data:view-full`。
- 审计类型至少：`HOUSEHOLD_CREATE/UPDATE`, `HOUSEHOLD_MEMBER_ADD/REMOVE`, `HOUSEHOLD_HEAD_CHANGE`, `PERSON_MODEL_MIGRATION`；详情禁止记录完整身份证/Token。

## 12. 自动化测试

1. 身份证：合法 X/数字尾号、错校验位、非法日期、生日不一致、性别不一致、15/17/19 位、空值。
2. 家庭户：创建/重复户号/分页/详情/修改/版本冲突/无物理删除端点。
3. 成员：加入、重复加入、跨户唯一、已注销人口、移出、户主移出保护、事务回滚。
4. 户主：合法变更、非成员、失效成员、并发版本、旧户主不匹配。
5. 安全：未认证 401、缺权限 403、跨 region 403、列表不泄露、完整值权限。
6. 回归：Phase03 迁移、Phase04 注销仍能更新成员/户主；既有 147 个测试全部通过。
7. 静态检查：不得出现新的 residents 写调用；Controller 路由唯一且均有 `@PreAuthorize`。

## 13. MySQL 验证

- MySQL 8 空库执行主脚本和 demo；从当前 V4_006 数据执行 V4_007；V4_007 连续执行两次。
- 用冲突数据验证唯一约束/事务错误；用 EXPLAIN 验证 household/member 查询索引。
- 运行立户→加成员→改户主→迁出/注销回归；强制中途异常验证全事务回滚。
- 对比 MySQL 表/列/关键约束与 H2 schema，差异必须列入报告而非静默接受。

## 14. 提交拆分

1. `docs: define person and household data migration contract`
2. `db: add household master data constraints and permissions`
3. `feat: enforce strict identity number validation`
4. `feat: add scoped household query APIs`
5. `feat: add household member and head workflows`
6. `test: cover household security consistency and rollback`
7. `docs: document phase 08 APIs and mysql verification`

每个提交均应可编译；数据库提交不得与大量业务代码混为一个不可回滚提交。

## 15. 验收清单

- [ ] person 是唯一新写人口模型，residents 处置有数据报告且无数据丢失。
- [ ] 身份证校验位/日期/性别/生日全部验证。
- [ ] 家庭户分页、详情、创建、修改可用，无 DELETE。
- [ ] 成员加入/移出和户主变更满足唯一归属与事务规则。
- [ ] 权限、范围、脱敏、审计测试齐全。
- [ ] 迁移、注销、审批和显式执行既有流程未回退。
- [ ] MySQL 初始化、V4_007 升级、重复执行和回滚验证通过。
- [ ] H2 与生产表结构差异已消除或逐项解释。
- [ ] API、权限矩阵、状态值、错误码、部署/演示文档更新。
- [ ] `mvnw clean test`、`git diff --check` 通过，工作区无前端或未授权修改。
