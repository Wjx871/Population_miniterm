# 审计账本 #78：XLSX 公式注入复核

日期：2026-11-03
范围：当前 worktree `C:\Users\jxgm\.codex\worktrees\s2-java-distributed-tech\小学期`，分支 `feat/20261103-s2-java-distributed-tech`，复核 `ExportFileService`、账本原文和现有导出 XLSX 测试。

## 结论

本轮没有复现可执行的 XLSX 公式。前导空白、Tab、CR/LF 及 Unicode 空白/控制字符确实会绕过 `cell` 中只检查首字符的转义分支，但实际生成并重新打开的工作簿中，这些值仍为文本单元格，且工作簿公式单元格数为 0。由于没有证据表明当前 XLSX 可触发公式执行，本轮未改生产代码；账本文件本身未修改。

## 已证实

- `ExportFileService.generate` 对每个数据值显式调用 `createCell(i, CellType.STRING)`，再写入 `cell(v)` 的结果。`cell` 只在原始首字符为 `= + - @` 时增加单引号；前导空格、Tab、CR/LF、Unicode 空白或控制字符会绕过该分支。
- 现有 `Phase06ExportAuditIntegrationTest` 覆盖 `=危险姓名` 的输出和值，但没有覆盖前导空白、公式单元格总数或重复生成。
- 新增的独立测试直接构造 `ExportFileService`，真实生成两份 XLSX，再用 Apache POI `WorkbookFactory` 重开。每份包含 22 种输入，逐格验证 `CellType` 和读回文本，并统计整个工作簿中的公式格数量。两份文件的所有数据格均为 `STRING`、读回值均符合当前转义行为，公式格均为 0。
- 覆盖项包括 `= + - @`、前导 ASCII 空格/Tab/CR/LF/CRLF、NBSP/EM SPACE/表意空格、NEL 与 LRM、单元格内 CR/LF/CRLF、中文、整数/小数、合法负数、已带单引号的输入，以及重复导出。
- 当前行为会把负数 `-12.50` 写成文本 `'-12.50`；整数和小数也均写为文本。这是本次验证到的导出语义，不构成本轮公式执行证据。

## 未证实

- 账本 #78 将“前导空白绕过前缀检查”描述为“XLSX 公式注入”。本轮确认了前缀检查可绕过，但这只是静态检查缺口；实际 XLSX 往返没有产生公式单元格，也没有复现公式执行。
- 测试使用 POI 重新打开并检查 OOXML 工作簿结构，没有启动 Microsoft Excel 或 LibreOffice 作为公式执行 oracle。因此本轮结论不等同于对所有办公软件、导入器或后续数据处理链的兼容性证明。

## 剩余风险与处理

若下游系统会剥离前导空白、转换文本类型，或以不同规则导入 XLSX，可能形成当前测试未覆盖的解释路径；CSV 转换、复制粘贴和第三方导入流程也不在本次范围内。应针对实际受支持的消费端验证这些路径后，再决定是否需要生产修复。

本轮定向命令：

```powershell
.\mvnw.cmd -Dtest=ExportFileServiceFormulaInjectionTest test
```

结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；构建成功。未连接 MySQL 或课程共享库。因未证明公式执行，按本任务约束保留现有导出行为；本报告只记录复核结论，不变更 `doc/testing/2026-11-02-antigravity-audit-ledger.md` 的 #78 状态。
