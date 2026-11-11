# #7 / #50 / #64 执行证据

基线：`a0ac9a632e3d79d650de01b36f7836d9722950fa`。测试运行于 Windows、JDK 21（编译目标 Java 17），未验证 JDK 17 运行时、生产部署或真实 Redis。原始日志保留在施工 worktree 的同级 `evidence` 目录，未提交 Git；可能含合成测试数据。

## 结果

- #7：初始边界 RED 为 3 项中 1 项失败，8 字符完全暴露；历史影印本旧掩码通过 jshell 动态复现。修复后的 OCR、历史掩码、#3 事务及补偿定向回归 76 项通过、0 跳过。最终全量测试包含新增边界及历史样例。
- #50：旧组件以迁入 RETURNED 且具备提交权限的真实 Handler 路由复现 RED；修复后 Vue SSR 组件矩阵覆盖 12 类业务、5 种状态、5 种权限组合。前端完整单测 218 项通过，构建通过。SSR 不证明浏览器点击、成功刷新、409、重复点击的端到端行为；静态审查显示现有详情页成功和冲突会重新加载。
- #64：隔离 Spring 上下文使用项目已有 ExportConfiguration 启用实际定时调度；本地无查询主动清理和 batch 轮转结果为 5→5→3→2，保留未过期值，并关闭调度器。定向调度、配置、模拟多实例及 HTTP 回归 69 项通过。真实 Redis、网络分区及生产多实例未验证，状态保持 OPEN。
- 最终 `mvn test`：1012 项、0 失败、0 错误、4 跳过。跳过分别为 3 项仅供旧基线复现的 #34 RED 和 1 项需显式启用的 #37 万条规模测试；未新增跳过参数。JaCoCo 因缺少执行数据而跳过报告，不作覆盖率结论。
- `npm run check` 通过，最后的测试矩阵变更后又运行 `npm run test:unit`，218 项通过。构建提示部分产物超过 500 kB。
- 账本：86 行完整，其他 83 行与基线逐字相同；三项原级别与摘要、历史附表不变；Markdown 文件引用均存在。状态统计 FIXED_VERIFIED 51、CODE_CANDIDATE 0、FRONTEND_HANDOFF 21、OPEN 3、FIXED_NOT_VERIFIED 3、NOT_REPRODUCED 8。

## 原始日志 SHA256

- `issue7-red.log`：`7D7D2A3DABACD80481E51D4253196E61CA2C66ABF205340F78E62DCFC95C8182`
- `issue7-historical-red.log`：`E04B2E7623FCF5373AB4290DC91E0A9C926F63C911E4E9AB5E83D1063EC76E15`
- `issue7-green-regression.log`：`79243B3BBEF4FFAFA790CA864C583E747B46F9B82E6FA0999664E8F7C1A8D993`
- `issue50-red-actual.log`：`FB1602F383438027E0ED0B29C34DD243E81504F1E471EF2B41A9B768926D07C5`
- `frontend-unit-last.log`：`F64B0628E5D5634D8C874D320FB1A08A710A49D6948CD202FE07AEDCD6420C0F`
- `frontend-check.log`：`B60746D1F65C18B129C8A4E000FCD591550BC869968AAFF30ACDB2A91BB1B64F`
- `issue64-dynamic.log`：`E31A7904012BA79C317640936E64CE4FCC53C3037288DF724F4541FD240563E5`
- `maven-full-final.log`：`E601CE7CA49F693EE733F212355689902BF4CC0FF49956CC9C2DB81456EC8DCC`

完整独立清单为同级 `evidence/SHA256SUMS.txt`；日志未经脱敏，未推送到 Git。

## 保护与限制

提交前远端 main、个人 integration、S3、snapshot、#43 feature 与任务指定 SHA 一致；主工作区 HEAD、`.gitignore` SHA256 与原有工作区状态一致。没有清理历史现场或更新 integration/main。最终远端保护引用在推送后另行核验。

#18 历史迁出单仍无安全补正入口；MigrationSubmissionValidator 未被绕过。专业 RETURNED 编辑与真实浏览器冲突、重复提交验证仍未完成，#50 保持 FIXED_NOT_VERIFIED。原有自由文本审计脱敏与生产存量文件不属于本轮全面清洗结论。
