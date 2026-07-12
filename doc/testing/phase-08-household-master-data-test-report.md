# Phase 08 测试报告

## 自动化范围

- 既有 147 项不删除、不跳过；ResidentMapperTest 的 5 个旧 CRUD 用例改为同数量的 legacy 退役守卫。
- 新增 `IdCardValidatorTest` 10 项：男女、大小写 X、校验位、非法/未来日期、生日和性别冲突、长度与地址码。
- 新增 `Phase08HouseholdMasterDataIntegrationTest` 15 项：分页/范围/脱敏、创建、重复户号、跨区写、更新/版本、成员加入/冲突/离户、无隐式户籍迁移、户主变更/回滚和审计。
- 新增 `Phase08PersonIdentityIntegrationTest` 5 项：自动提取生日、重复身份证、更新校验、具体 400 错误和禁止普通删除。
- 原迁移、注销、审批、材料、流动人口、居住证和导出测试保留，最终结果以 Maven 汇总为准。

最终 H2 结果：Tests run **177**，Failures **0**，Errors **0**，Skipped **0**，BUILD SUCCESS，50.600 s。

## MySQL 验证边界

当前环境未检测到 `mysql.exe` 或 `docker.exe`，也未获得外部临时 MySQL 的安全凭据，因此不能创建并清理 `population_miniterm_phase08_verify`。本次只完成脚本静态审查和 H2 MySQL 模式回归；真实 MySQL 初始化、V4_007 连续执行、API 烟测和清理状态均记为 **未执行**，不得标记 PASS。

待具备环境后执行：主脚本 → V4_001—V4_007 → demo_data 两份 → V4_007 再执行 → 启动应用 → household API/范围/冲突/审计/迁移注销烟测 → 一致性 SQL → 删除临时库。密码只通过环境变量或交互输入提供。
