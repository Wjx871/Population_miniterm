# 2026-11-10 审计状态独立对账报告

个人 integration 基线：`c49e7531d9aa58d37510a784b02e74101c3f44d5`。本轮为 DOCS ONLY；没有生产代码或数据库修改，也未重新运行历史 H2、MySQL 或 Maven 测试。下列历史验证及 ChatGPT 独立复核结论据对应材料与核验聊天记录，不冒称本轮重跑。所有关联提交经 `git merge-base --is-ancestor` 验证属于上述基线。

| 编号 | 状态变更 | 复核结论、提交及限制 |
|---|---|---|
| 3 | CODE_CANDIDATE → FIXED_VERIFIED | 历史修复 `a1cff942` 经 `c49e753` 的 H2/MySQL 8.4 慢 OCR、连接与锁观测及 UNKNOWN 文件保留验证；ChatGPT 独立复核通过。EXISTING_FIX_CONFIRMED / REVIEW_PASSED / INTEGRATED / CLEANUP_COMPLETED。同步请求仍占用线程，无持久化崩溃恢复协议。 [证据](issue3-ocr-transaction-verification.md) |
| 15 | CODE_CANDIDATE → FIXED_VERIFIED | 家庭户建立、成员入户、迁入及历史恢复日期边界已修复；H2 RED/GREEN、MySQL 并发及全量回归经独立复核。提交 `3f273088`。 [证据](issue15-household-date-boundary.md) |
| 19 | CODE_CANDIDATE → FIXED_VERIFIED | 取消草稿释放有效批次号占位；`d443f30b`、V4_019 条件唯一方案及 H2/MySQL 8.4 并发门禁经独立复核。历史数据升级需备份与受控回填；隔离验证不代表生产已执行 V4_019。 [证据](issue19-migration-batch-reuse.md) |
| 22 | CODE_CANDIDATE → FIXED_VERIFIED | `3268a831` 要求 executeOut 归档受影响行数为 1；零行旧版 RED、H2/MySQL GREEN、回滚与回归经独立复核；-1/2 为模拟返回。 [证据](issue22-archive-affected-rows.md) |
| 34 | CODE_CANDIDATE → FIXED_VERIFIED | `5b95f2be` 按人员、通用业务类型及未结状态排他；真实 HTTP/H2 与 MySQL RR 并发门禁经独立复核。RR 结论依赖当前入口先取得父记录锁再建立一致性快照，不涵盖已有旧快照的外层事务。 [证据](issue34-permit-first-issue-exclusivity.md) |
| 37 | CODE_CANDIDATE → FIXED_VERIFIED | `5d05109e` 使用有界 ID keyset 分页、冻结日期、锁后重验；MySQL 两类各 10000 候选及并发/锁顺序回归经独立复核。分页不消除数据库扫描成本。 [证据](issue37-expiry-keyset-pagination.md) |
| 78 | OPEN → NOT_REPRODUCED | `aec60c4f` 的真实普通及审批后敏感 XLSX 导出经 94 组输入、OOXML、POI 和 Excel 16.0 只读验证，未复现可执行公式注入；没有真实漏洞 RED 或生产修复。CSV 转换、手动编辑和其他 Excel 版本未测试。 [证据](issue78-xlsx-formula-triage.md) |

#3 另有历史修复提交 `a1cff942f7f9d7370ad7e4858133435ca2b70115` 和验证提交 `c49e7531d9aa58d37510a784b02e74101c3f44d5`。#19 生产部署 V4_019 前须完成历史数据检查、备份和受控回填；隔离数据库验证不代表生产已迁移。其他事项的具体部署前提及未验证范围见各证据文档；均不宣称部署完成。

账本表格通过基线逐行对比：编号、原级别、问题摘要保持不变，七项之外 79 行逐字相同；总计 86 项，仅使用原有六种状态。历史材料中的候选及未集成表述为当时快照，当前状态以 [审计账本](../testing/2026-11-02-antigravity-audit-ledger.md) 为准。
