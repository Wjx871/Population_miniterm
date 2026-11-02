# Population Miniterm 第三批核心审计修复报告

## 基线与提交

- 基线：个人集成分支 `integration/20261101-backend-p0-security`，`9368ac195a892063e144a612b2d43f539bd9fbef`。
- 已核验共享 `origin/main`：`b54579b6b7f8b1feeda60f43906da05fb2ffda99`；本轮没有向 main 推送或合并。
- 施工分支：`fix/20261102-core-audit-round3`，独立 worktree，未设置 upstream。
- A：`3caaeca6c164ed1a2dea4eeb7667b0b96a218d03` — `fix: enforce hierarchical region data scopes`
- B：`be12635adae45dee266610b0144d9c7ee07f092f` — `fix: scope cancellation queries and snapshots`
- C：`8509d78e8d952f40fff26ab2bc1a54c9fd7277bc` — `fix: correct scoped statistics and real data`
- D：`915637828756e25d8ad6d0720330a6979b0e3f54` — `fix: close Redis health probe connections`
- 四组代码最终提交 SHA：`915637828756e25d8ad6d0720330a6979b0e3f54`。本报告单独作为交付文档提交。

## A：REGION 与 SELF 数据权限（#58、#59）

**结论：确认并修复。** 旧 REGION 条件以固定区划前缀放大区县范围；归档详情还存在 SELF 短路，列表却拒绝 SELF，权限不一致。

- `AuthMapper.xml` 使用 `admin_region` 的启用节点及父子关系解析可见区划树；`AuthService` 将解析结果放入 `AuthenticatedUser.regionCodes`。REGION 查询以完整允许代码集合精确匹配，缺失/未解析区划在 `DataScopeCriteria.current()` 中拒绝。
- 各相关 Mapper 的列表、详情及导出统一采用区划集合过滤。`ResidenceArchiveService` 保持 SELF 不能查看迁移归档，详情与列表一致；DEPARTMENT 按可信 `business_application.applicant_department_id` 隔离，REGION 按归档快照区划集合过滤。
- 旧实现失败复现：区县账号的统计查询包含同市兄弟区县；SELF 用户能通过归档 ID 读取列表不允许查询的记录。修复后定向回归 56 项通过。
- 关键文件：`AuthMapper.xml`、`AuthService.java`、`AuthenticatedUser.java`、`DataScopeCriteria.java`、`ResidenceArchiveService.java`，及申请、人口、户籍、迁移、流动人口、证件、综合查询、日志和导出 Mapper。

## B：注销查询范围与快照（#27、#28、#60）

**结论：确认并修复。** 草稿快照为空、部门查询仅按区划过滤，以及注销申请 SELF 查询语义与详情不一致均能复现。

- 创建及更新草稿时，服务端读取可信人员/家庭户数据并保存姓名、户号、区划等快照；执行时不再回写已办结快照。客户端不能提供可信快照值。
- 列表与 count 使用同一范围条件：SELF 仅本人发起的申请，DEPARTMENT 按申请部门，REGION 按允许区划；详情按相同申请归属授权。历史注销归档继续按现有契约拒绝 SELF，列表与详情一致。
- 旧实现失败复现：新建草稿快照为空；同区不同部门的列表计数包含其他部门申请；SELF 查询本人申请被拒绝。修复后 `Phase04CancellationIntegrationTest` 33 项通过。
- 修改文件：`CancellationService.java`、`CancellationMapper.java`、`CancellationMapper.xml`、`Phase04CancellationIntegrationTest.java`。

## C：统计准确性、权限和真实性（#40、#63、#66）

**结论：确认并修复。** 旧统计使用与业务表不符的 `有效`、`正常` 状态值，部分统计未按登录用户范围查询，空库会返回模拟人数、图表和日志；QUERY_VIEWER 角色代码还会把 DEPARTMENT 范围提升为 ALL。

- 人口统计仅计 `REGISTERED` 且有 ACTIVE 户籍的人口；家庭户、重点人口使用实际 `ACTIVE` 状态；流动人口只计 `ACTIVE/current_flag=1`。列表图表与汇总都带入当前 `DataScopeCriteria`，未知或缺失范围在 SQL 中 `1=0` 拒绝。
- 人口/重点人口/证件按人员业务归属和居住区划范围过滤；家庭户遵循户籍查询契约，SELF 返回 0，DEPARTMENT 按申请部门，REGION 按允许区划；迁移图表按已办结申请的申请人范围过滤；日志按本人、部门或区划过滤。
- 移除隐式演示数据。空汇总返回真实 0，图表和日志返回真实空集；增长率字段保留但因没有可信增长率来源返回 `null`，`isMock=false`。`statistics:view` 本身不扩展数据权限；实际配置为 ALL 的账号仍保留 ALL。
- 统计缓存键现在包含 userId、部门和完整区划集合，避免 SELF 用户或不同区域复用同一统计结果。
- 旧实现失败复现：部门范围被 QUERY_VIEWER 角色扩为 ALL；空库摘要/空图表/空日志出现模拟数据；旧状态 SQL 对合成 ACTIVE 数据返回 0；旧 `DATE_FORMAT` 在 H2 不可执行。修复后 C 定向 10 项通过，含区域、部门、SELF、ALL、失效记录、迁移趋势、日志和缓存隔离。
- 修改文件：`StatisticsMapper.java`、`StatisticsMapper.xml`、`StatisticsService.java`、`UnifiedStatisticsService.java`、`DashboardService.java` 及其统计测试。

## D：Redis 健康探针连接（#76）

**结论：确认并修复。** 解析项目实际依赖为 Spring Data Redis 3.5.1；其 `RedisConnection` 实现 `AutoCloseable`。旧 `ping()` 成功、非 PONG、异常及重复调用均未关闭连接。

- `OptionalRedisService.ping()` 使用 try-with-resources；成功取得的连接无论 PONG、非 PONG 或 ping 抛错都会关闭。禁用 Redis 或连接工厂不可用时安全返回 `false`。
- Redis 故障日志改为固定通用信息，不输出异常消息，避免日志包含连接凭据。Mockito 测试先在旧实现上运行，4 项连接关闭断言失败；修复后 8 项全部通过，覆盖重复调用、禁用、空/不可用工厂及日志脱敏。
- 修改文件：`OptionalRedisService.java`、`OptionalRedisServiceTest.java`。

## 回归与变更边界

- 施工前基线：`clean test` 442 项通过，0 失败、0 错误、0 跳过。
- 最终：`.\mvnw.cmd clean test` 469 项通过，0 失败、0 错误、0 跳过。
- H2 隔离内存库与合成数据；本轮未连接真实人口数据库、MySQL 或 Redis。
- 未修改 API DTO、数据库 Schema、前端或运维配置；原有统计接口字段保持。统计增长率值从模拟字符串改为 `null`，前端需能显示“暂无增长数据”。
- 全量回归覆盖此前批次的生命周期、导出、SELF、户主交接、注销、退回补正及 OCR 测试；均通过。
- MySQL 尚未做运行级验证；H2 与 MySQL 在 CTE、日期及 CLOB 转换等方言细节上可能不同。Redis 只验证框架接口生命周期及模拟异常处理，未连真实 Redis 连接池。
- 没有发现需要扩大 Schema 或权限架构的阻断项；不在本轮处理的审计问题仍待后续批次。
