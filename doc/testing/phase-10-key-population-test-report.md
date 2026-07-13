# Phase 10 测试报告

## 自动化

- 基线：241 项，Failures 0，Errors 0，Skipped 0。
- 新增 `Phase10KeyPopulationIntegrationTest`：56 项，覆盖查询/范围/脱敏、专业入口、建档材料与审批、审批不落地、显式执行、幂等、解除、历史、审计和一致性。
- Phase 02 通用申请测试改用明确的 `GENERAL_SERVICE`，重点人口两种类型继续被通用入口拒绝。
- 最终全量：Tests run 297，Failures 0，Errors 0，Skipped 0，BUILD SUCCESS，约 62 s。

## MySQL 8

- MySQL Community Server 8.4.10，隔离地址 `127.0.0.1:3412`，临时库 `population_miniterm_phase10_verify`。
- 全新初始化及两份演示数据成功：33 张表，重点人口三表和 3 个权限存在。
- 从 Phase 09 merge `8ea1f21` 构造 V4_008 状态，V4_009 连续执行三次成功；legacy residents、既有权限和字典保留。
- admin/population/approver 登录成功；完成建档创建、材料准备、提交、审批、确认未自动落地、显式执行、查询记录/历史、解除创建/提交/审批、确认未自动解除、显式解除。
- 重复执行 409、approver 执行 403、跨区域创建 404、通用入口创建重点人口类型 400；写审计 4 条且无完整身份证。
- 12 项一致性检查异常数均为 0。应用、临时库、MySQL、安装包、数据/上传/导出目录在验收后清理。
