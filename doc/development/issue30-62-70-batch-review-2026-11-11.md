# #30 / #62 / #70 批量审计与验证（2026-11-11）

## 基线与范围

从 `integration/20261101-backend-p0-security` 精确 `1463cad69cefb4f19d4751ecb2916e00b9db0b7a` 创建独立 feature `fix/20261111-issue30-62-70-batch`。未推送 integration/main，未部署。原始日志和 SHA-256 索引见 [运行证据](issue30-62-70-batch-evidence-2026-11-11.md)。所有测试数据库均隔离；测试数据为合成数据。H2 在 JDK 21 上执行（编译目标 17）。

## #30：异常未复现，另修正显示歧义

`CancellationStatusListener` 的 `valueOf` 仍不能处理 RETURNED；但当前 `ApplicationService.returnApplication()` 不通知该监听器。独立 H2 MockMvc 测试对人员注销和家庭户注销分别实际执行了草稿、提交、批准、退回、重新提交和再次批准，以及退回后撤回；均没有出现所述未处理枚举异常。当前调用链上的原异常描述记为 `NOT_REPRODUCED`；这不证明未来新增的 RETURNED 广播安全。

| 阶段 | business_application | cancellation_record | approval request |
| --- | --- | --- | --- |
| 草稿 | DRAFT | DRAFT | 无 |
| 提交 | UNDER_REVIEW | UNDER_REVIEW | PENDING |
| 批准 | APPROVED | APPROVED | APPROVED |
| 退回 | RETURNED | APPROVED | APPROVED，保留原结论 |
| 重新提交 | UNDER_REVIEW | UNDER_REVIEW | PENDING，复用并重置 |
| 再次批准 | APPROVED | APPROVED | APPROVED |
| 退回后撤回 | WITHDRAWN | WITHDRAWN | APPROVED，保留原结论 |

两类完整流均检查 SUBMIT、APPROVE、RETURN、SUBMIT、APPROVE 日志顺序；草稿退回、错误版本及重复退回返回 409；退回时详情 `executable=false` 且执行返回 409，再次提交时不可执行，再次批准后才可执行。相关既有 `Phase04CancellationIntegrationTest` 的执行故障注入回滚用例亦通过；没有声称本轮验证了真实并发竞争或新增退回事务故障注入。

表征发现退回后列表仅返回并显示专业表 APPROVED，容易误读。修复在列表读取 SQL 增加已关联申请状态，在详情复用已读取的申请状态，响应新增 `applicationStatus`；前端分别显示“注销记录状态”和“申请状态”，筛选仍明确针对前者。专业状态、数据库约束、监听器和审批事务未改。RED 是缺少当前申请状态的响应/显示证据，不是枚举异常。历史专业记录及审批结论未回写。

## #62：独立权限与范围验证

`Issue62ApplicationApprovalRegionScopeIntegrationTest` 使用合成用户、角色、授权区划与申请，独立 H2、真实 AuthService/JWT、Security、MockMvc 和 MyBatis。真实 REGION 集合包括本区与两个子区；同区、两个子区可见，同级外区及看似前缀匹配但不在授权集合的伪区划不可见。申请 UNDER_REVIEW 和审批 PENDING 在同权限、同记录集合对照相同；检查了分页和总数、处理列表、空 REGION 拒绝、DEPARTMENT/SELF/ALL、无 approval:view 申请人仅看自己，以及跨区详情拒绝前后业务申请/审批行不变。拒绝请求的安全审计日志不视为业务数据写入。原 Mapper 无需修改，状态 `FIXED_VERIFIED`。

## #70：历史实现与当前复核

实现 `4a188f0b7231279a7d7cf453eb16a13af007b434` 和证据提交 `836cf5d658ff4fd3e290de3087afca6d611185e8` 均为本基线祖先；`StatisticsService.java` 与 Issue70 专项测试相对证据提交无差异。本轮专项 11 项通过；历史专项 40 项和全量 810 项为历史证据。响应副本固定脱敏，包括有完整敏感权限的管理员；普通、NULL、已脱敏文本、`log:view`、四类范围和 `/api/logs` 回归由该测试及历史单测覆盖。测试专用 H2 CAST 适配不代表真实 MySQL。状态 `FIXED_VERIFIED`；真实 MySQL、生产部署、前端人工回归和非标准敏感日志格式未验证。

## 回归和限制

- 基线 #30 表征 5 项通过；显示缺失 RED 2 项；最终 #30/#62 共 7 项通过。首轮 #62 夹具及草稿日志断言失败不是有效 RED。
- 申请、审批、注销、统计相关 H2 联合测试 88 项通过；最终 Maven 全量 979 项，0 失败、0 错误、4 既有跳过。跳过分别为 Issue34 旧 RED 3 项、Issue37 1 项；未通过跳过消除失败。
- 前端全量单测 217 项通过，构建通过；首次未装依赖的 4 个加载失败保留于证据。未做浏览器人工回归。
- 新私有 MySQL 8.4.8 使用 127.0.0.1:33317 和独立数据目录，仅在合成最小表验证新增投影与原专业状态筛选，未使用系统 3306；未验证真实 MyBatis、完整审批/注销流程或生产数据。实例已关闭，数据保留。
- 本轮未更改申请/审批 Mapper、专业状态枚举、数据库约束或审批事务状态机；修复涉及注销列表 SELECT 投影、响应模型和前端展示。业务生产部署、真实网络 HTTP、JDK 17 运行时未验证。
- 原始 worktree、#43 等历史现场、所有 TEMP 数据保留；不清理本轮 worktree。账本仅目标三行可变，状态以证据为准，等待独立复核。
