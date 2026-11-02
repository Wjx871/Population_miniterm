# 第三批最终定点补丁：Dashboard SELF 归属

## 基线与提交

- 审查基线：`6a608e408f032ed1594cb1566bcdc7476e9078b4`。
- 施工分支：`fix/20261102-round3-dashboard-self-scope`，独立 worktree；未跟踪 `origin/main`。
- 代码提交：`1cb07cd2f329031ceccc4d026bd41fad7e6bfa89`。
- 本轮未修改第三批旧 worktree、个人集成分支或 main。

## 契约与失败复现

流动人口和居住证详情的 SELF 规则均通过记录的来源 `business_application.applicant_user_id` 判断归属：`FloatingResidenceMapper.xml` 对流动登记关联 `f.source_application_id`，对居住证关联 `r.source_application_id`。`operator_id` 记录经办人，`issuing_department_id` 是部门范围字段；二者都不能代表 SELF 申请人。

在未修改生产代码时运行新增合成数据测试，旧实现观察到：

- A、B 各自的活动流动人口计数为 0（各应为 1），因为记录由 C 执行。
- A、B 各自的活动居住证和到期居住证计数均为 2（各应为 1），因此同部门用户互相看到对方记录。
- C 的 SELF 流动人口计数为 3、居住证计数为 2（均应为 0），错误继承执行记录和同部门证件。
- `populationScaleTrend` 对 A/B 返回流动 0、居住证 2；对 C 返回流动 3、居住证 2。真实 `DashboardService.overview()` 中 A 的活动流动人口也错误为 0。

## 修复与验证

- 仅修改 `DashboardMapper.xml` 的两个 SELF 条件：以 `EXISTS` 关联对应来源申请并匹配当前申请人 ID。来源申请缺失时不会命中；使用 EXISTS 不会因关联行重复而放大计数。
- `countActiveFloating`、`countActivePermits`、`countExpiringPermits` 及 `populationScaleTrend` 共用这两个范围片段；其 ACTIVE/current_flag、到期日期和趋势日期条件保持原样。
- DEPARTMENT、REGION、ALL 条件未变。合成数据验证 A/B 各自为 1、C 为 0；部门为 2、授权区域为 2、ALL 为 3；取消、过期和非当前历史证件仍不会计入活动/到期数。趋势分别对应 A/B 的本人数据，C 不包含他人数据。
- 新增 `DashboardService` 实际调用测试，核对 overview 活动流动人口、活动居住证、到期数及 charts 当日趋势的数值。
- 定向 `DashboardMapperTest`：10 项通过，0 失败、0 错误、0 跳过。
- 全量 `.\mvnw.cmd clean test`：472 项通过，0 失败、0 错误、0 跳过（第三批原有 469 项全部保留）。

## 修改范围与限制

- 修改文件：`src/main/resources/mapper/DashboardMapper.xml`、`src/test/java/com/wjx871/population/dashboard/DashboardMapperTest.java`、本报告。
- 未修改 API DTO 或数据库 Schema；未连接真实人口库。测试使用隔离 H2 内存库和合成数据。
- 未对生产 MySQL 执行验证；虽然本次使用标准相关 `EXISTS`，仍需在部署使用的 MySQL 版本及数据上运行级复核。
