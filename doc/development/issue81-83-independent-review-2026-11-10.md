# #81 / #83 独立复核结论登记（2026-11-10）

个人 integration 精确基线：`c8fefa0340456bedcbce1496e1c86474b49b9d53`。原实现提交：`f5c3a959cf99f69fed8e8640d845a99b23428563`；本轮以 `git merge-base --is-ancestor` 对基线检查退出码 0。原施工分支为 `fix/20261107-issue81-83-admin-region-hierarchy`。ChatGPT 已独立复核 GitHub 源码和测试证据，两项结论均为 **REVIEW_PASSED / ALREADY_INTEGRATED**，不代表已在生产部署。

对原实现提交之后至上述基线的六个指定文件执行 Git 历史及端点差异检查，没有后续修改：

- [AdminRegionService.java](../../src/main/java/com/wjx871/population/reference/AdminRegionService.java)：祖先锁计划、锁后重验、后代扫描、状态变更及事务审计。
- [AdminRegionMapper.java](../../src/main/java/com/wjx871/population/reference/AdminRegionMapper.java)、[AdminRegionMapper.xml](../../src/main/resources/mapper/AdminRegionMapper.xml)：主键锁定读、后代 NOWAIT 查询及乐观版本条件。
- [AdminRegionSqlConfiguration.java](../../src/main/java/com/wjx871/population/reference/AdminRegionSqlConfiguration.java)：数据库锁语句方言选择。
- [Issue8183HierarchyHttpTest.java](../../src/test/java/com/wjx871/population/reference/Issue8183HierarchyHttpTest.java)、[Issue8183MySqlIT.java](../../src/test/java/com/wjx871/population/reference/Issue8183MySqlIT.java)：真实 HTTP 与 MySQL 并发测试。

## 不变量和并发协议

- **#81**：新增、更新迁入及启用时检查目标父节点和完整祖先链均启用；历史停用祖先不能被直接父节点的状态掩盖。
- **#83**：停用节点前遍历所有后代，包括穿过 DISABLED 中间节点；发现任意深度 ENABLED 后代则拒绝。
- MySQL 锁计划中祖先取 S 锁、操作节点取 X 锁，按主键升序取得，锁后以当前读重验 `parent_id`；版本条件更新与成功审计处于事务内。后代遍历是第二阶段，并非整个遍历全局主键有序；使用 `FOR SHARE NOWAIT` 遇冲突返回 409 并回滚，以避免等待较老子节点形成锁环。NOWAIT 不意味着所有数据库锁都不会等待。

## 历史验证与界限

历史 [施工报告](issue81-83-admin-region-hierarchy.md)、[RED 证据](issue81-83-admin-region-hierarchy-red-evidence.txt)、[H2 GREEN](issue81-83-admin-region-hierarchy-h2-green-evidence.txt) 和 [MySQL GREEN](issue81-83-admin-region-hierarchy-mysql-green-evidence.txt) 记载：旧版 H2/MySQL 各 3 项 RED；H2 专项 78 项、MySQL 8.4.8 RR 22 项通过，36 组实际 InnoDB 行锁等待；原施工全量 Maven 869 项，其中 866 通过、3 跳过、0 失败/错误。后续 #3 [验证报告](issue3-ocr-transaction-verification.md)记录全量 972 项，其中 968 通过、4 跳过、0 失败/错误。以上均为历史测试，本轮未重跑 Maven、H2 或 MySQL。

所有并发层级写入须遵守 Service 锁定协议；绕过服务的外部写入不在其保证范围内。大型子树停用性能、生产部署及真实生产负载未验证；历史异常数据不会自动修复。历史施工报告中的候选及未集成文字是当时的阶段快照，当前状态见[审计账本](../testing/2026-11-02-antigravity-audit-ledger.md)。

本轮只登记文档结论，未修改生产代码、测试、SQL、配置、迁移或原始证据。原 #81/#83 工作区 `C:\Users\jxgm\.codex\worktrees\issue81-83-admin-region\小学期` 和 MySQL 临时数据现场保留，待单独授权后安全处理；本轮不声明其已清理。
