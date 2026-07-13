# Phase 10 重点人口实施总结

Phase 10 在既有 `key_population` 表上兼容升级，新增专业申请详情 `key_population_application` 和追加式 `key_population_history`。没有新增审批表或材料表，完整复用 business_application、application_material、sys_approval_request/log 与现有显式执行规则。

同一人员/类型的 ACTIVE 唯一性由 MySQL 生成列唯一索引、事务锁和执行前复核共同保证；同一记录只允许一个处理中解除申请。建档和解除审批只同步专业详情状态，execute 才在一个事务中修改当前记录、追加历史、完成通用申请和写审计。失败整体回滚，记录与历史均不物理删除。

查询范围进入 Mapper SQL，越权详情隐藏为 404，响应复用统一敏感字段策略。申请 payload 只保存人员 ID、分类、等级、原因、日期和责任信息，历史快照只保存分类/等级/状态。本阶段未新增导出、预警、轨迹、画像评分或外部联网。
