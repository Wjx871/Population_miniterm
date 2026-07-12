# Phase 08 家庭户主数据实施总结

Phase 08 完成 person 主模型统一、GB 11643 身份证校验、家庭户查询/维护、成员生命周期和户主变更。未修改前端，未重写迁入迁出、注销、审批、流动人口、居住证和导出。

一致性规则：一个人最多一个 ACTIVE 成员关系由事务锁和冲突检查保证；一个户最多一个 ACTIVE HEAD，户主变更依次锁户、锁成员、调整旧/新关系并用 `household.version` 更新主档，失败整体回滚。普通成员维护不创建 residence 或 migration；HEAD 不能直接离户；核心主档无物理删除接口。

数据库新增 V4_007，仅加入家庭户/成员检索索引并幂等维护既有 household 权限；legacy residents 不删除但应用零依赖。H2 移除 residents 并增加相同查询索引。

阶段门禁已在隔离的 MySQL Community Server 8.4.10 上完成：全新初始化及演示数据导入成功；从提交 `0379a44` 的 V4_006 历史状态连续执行 V4_007 三次成功且数据计数不变；Spring Boot、家庭户 API、权限与数据范围、15 项一致性检查和迁移/注销/流动人口/居住证/导出回归通过。未发现需要修复的真实 MySQL 缺陷，详细证据见 `doc/testing/phase-08-household-master-data-test-report.md`。
