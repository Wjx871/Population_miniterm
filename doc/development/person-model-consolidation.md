# 人口主模型统一说明

- 唯一正式模型：`person`、`PersonController`、`PersonService`、`PersonMapper` 和 `PersonMapper.xml`。
- 原重复模型：`residents` 表以及 `resident` 包、`ResidentMapper.xml`、`/api/residents`。Phase 08 已移除全部生产 Java/XML 依赖和对外 API，不做 person/residents 双写。
- 既有数据库：不 DROP `residents`，历史数据原样保留。升级前应对 person/residents 身份证交集和冲突做人工数据报告；无法确定的记录不得覆盖迁移。
- 新建数据库：主脚本暂保留 legacy 表以兼容已有课程数据脚本，但应用不会读取或写入；后续物理移除必须单独审批。H2 新建测试结构不再创建该表，用于防止回归依赖。
- 删除规则：PersonController 不提供 DELETE；普通编辑不能设置注销业务状态。人员注销继续使用 cancellation 的材料、审批和显式 execute，核心 person 记录保留。
- 严格校验：新增/更新 person 都经过 `IdCardValidator`，身份证规范化为大写 X，并校验日期、校验位、出生日期和性别。
- 影响：迁入迁出、注销、流动人口、居住证、导出和统计原本均连接 `person`，本次未复制或重写其流程；相关回归测试必须持续通过。
