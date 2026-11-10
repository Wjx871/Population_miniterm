# #78 XLSX 公式注入独立复核候选（2026-11-10）

## 结论与边界

**NOT_REPRODUCED**。在精确基线 `5d05109ecabb15738c0d1c7d017e5d8220f490c1` 上，普通脱敏导出和审批后敏感导出的原始 XLSX 都未产生可执行公式。前导空白/格式字符加公式形状文本仍写成共享字符串，不能把未加单引号等同于公式注入。

没有真实安全 RED，因此本轮**未修改任何生产代码、数据库结构或迁移**，也未为前导空白追加单引号。账本 #78 保持 OPEN，仅建议重新分类为 NOT_REPRODUCED，等待 ChatGPT 独立复核；不是 FIXED_VERIFIED，也不是修复后 GREEN。专项测试通过是现状证据和回归保障。

本轮分支：`fix/20261110-issue78-xlsx-formula-triage`。施工 worktree：`C:\Users\jxgm\.codex\worktrees\issue78-xlsx-formula-triage\小学期`。开工实时远端 integration SHA 与指定基线一致。仅推送本轮 feature，保留 worktree，不合并 main/integration，不清理历史 worktree。

## 威胁模型与源码核验

当前生产导出入口是 `ExportController` 的普通导出、敏感申请/执行及下载。`ExportService.normal` 和 `executeSensitive` 均调用同一个 `ExportFileService.generate`。敏感申请的 `requested_format` 固定为 XLSX，文件名后缀 `.xlsx`，下载 MIME 为 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，生成器为 `XSSFWorkbook`。检查 `src/main` 未发现 CSV 生成入口或 `setCellFormula` 调用。

数据行始终 `createCell(i, CellType.STRING).setCellValue(cell(v))`。表头不是显式传入 STRING，而是新建单元格后调用字符串版 `setCellValue`；字段由 `ExportPolicy` 白名单限制，实际输出也验证为 STRING。数据库用户值经 `ExportMapper.selectRows`、普通导出的脱敏或敏感导出的原值映射进入字符串 API，没有公式上下文或拼接原始 XML 的写入路径。现有 `cell(v)` 只在首字符为 `=+-@` 时加单引号，这不是 XLSX 字符串类型安全性的唯一依据。

必须区分四类行为：

| 类别 | 判定依据 | 本轮结果 |
|---|---|---|
| A：真实 XLSX 公式 | 工作表 `<f>`、POI FORMULA、Excel HasFormula | 三者均为 0 |
| B：公式形状的文本 | `t="s"` 指向 sharedStrings，POI STRING | 已实测；这不是 A |
| C：CSV 导入自动解释 | 文本格式导入器重新推断类型 | 当前无 CSV 导出路径；不作为 A 的证据 |
| D：用户编辑后重新解析 | 用户修改、复制粘贴、文本转列等操作 | 未执行，不把它作为原始 XLSX 的漏洞证据 |

API/格式语义参考：[Apache POI Cell API](https://poi.apache.org/apidocs/dev/org/apache/poi/ss/usermodel/Cell.html)、[Microsoft Open XML Cell](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.cell)、[CellValues](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.cellvalues)。这些资料用于解释格式；结论来自本轮生成文件和 Excel 的直接证据。

## 可复核实测路径

`ExportFormulaInjectionIntegrationTest` 使用真实 Spring Security/JWT、MockMvc、Controller、Service、Mapper、H2 和文件系统。普通链路为登录→普通导出→下载；敏感链路为登录→敏感申请→提交→审批→执行→下载。没有 mock 导出器、SQL 或审批服务。

为直接验证最坏的已存储值，94 组姓名/地址用参数化 JDBC 插入独立 H2，测试事务结束回滚。这不宣称每种字符均能通过人员创建接口。材料沿用现有导出集成测试的 VERIFIED 元数据夹具，没有真实上传/核验 PDF。普通导出验证姓名，敏感导出同时验证姓名和地址；其他字段和表头也检查单元格类型、公式和外链。

每种模式生成、下载两次（重新申请/审批，不重复执行同一敏感申请）。保存下载原始字节，对照数据库 `file_sha256`，再解析 ZIP XML、用 POI 只读重开，检查前后 SHA 不变。普通每份 285 个单元格、94 项输入映射；敏感每份 380 个单元格、188 项输入映射。

输入矩阵由 `ExportFormulaPayloads` 定义：21 种前缀 × `=1+1`、`+1+1`、`-1+1`、`@SUM(1,1)`，另有 10 个文本对照，共 94 组。前缀包括空前缀、普通空格、Tab、CR、LF、CRLF、NBSP U+00A0、全角空格 U+3000、EM SPACE U+2003、NEL U+0085、U+200B/U+200C/U+200D/U+200E/U+200F/U+202A/U+202E/U+2060/U+2066/U+FEFF 和组合前缀。对照包括中文、前导零、数字文本、负数、已带单引号、纯空白、嵌入 CRLF、XML 形状文本及 OOXML 转义形状文本。原单测还保留整数和 BigDecimal 对照。

## 原始文件与 OOXML

证据根目录：[issue78-xlsx-formula-triage](../testing/evidence/issue78-xlsx-formula-triage/)。每个模式子目录均有 `download.xlsx`、`manifest.json`、原始 `sheet1.xml` 和 `sharedStrings.xml`。清单逐项保存输入、预期、实际文本、POI CellType 和对应单元格 XML；原始 XLSX ZIP 保留全部 OOXML 和关系文件。

| 文件 | 原始下载 SHA256 |
|---|---|
| normal-first/download.xlsx | `a1b5cec93fcd07d67086c28464a4026e5d423e0630324b1b0c89690cbef31c4f` |
| normal-regenerated/download.xlsx | `a1b5cec93fcd07d67086c28464a4026e5d423e0630324b1b0c89690cbef31c4f` |
| sensitive-first/download.xlsx | `2203b362d10d25c704033ebc99281af9fcf299cd2d3beaac2ff6aacf372a64e3` |
| sensitive-regenerated/download.xlsx | `1667b86a286802d09d0bacf7015ce799c447998599f7250a6e02f5abbd75bc6b` |

四份文件：全部单元格为 POI STRING，XML 全部 `t="s"`；使用 `xl/sharedStrings.xml`，未使用 inlineStr；工作表 `<f>` 数为 0，POI 公式数为 0，`xl/externalLinks/` 部件数为 0，ZIP 中 External 关系数为 0。

例如普通首份 `A2` 的原始单元格是 `<c r="A2" t="s" s="0"><v>4</v></c>`，指向共享字符串 `'=1+1`，而不是公式。前导空格的 ` =1+1` 仍是 `t="s"` 指向的文本，XML 的字符串空白通过 `xml:space="preserve"` 保留。`<f>1+1</f>` 探针写入共享字符串中的转义文本，没有变成 XML 公式元素。

另有独立阳性对照：测试主动 `setCellFormula("1+1")`，检测器同时得到 1 个 POI FORMULA 和 1 个 XML `<f>`。该工作簿只用于证明检测器能识别 A，不是生产链路产生的 RED，也未交给 Excel 运行。

## Excel 实际打开

本机 Excel 16.0，通过 [只读验证脚本](../../scripts/issue78-excel-readonly.ps1) 创建独立隐藏实例。先设 ForceDisable 宏、关闭事件、关闭链接提示，再设手动计算、禁止保存前计算；`Workbooks.Open` 使用 UpdateLinks=0、ReadOnly=true。不运行宏、外链、刷新或公式求值，也不保存工作簿。

[excel-readonly.json](../testing/evidence/issue78-xlsx-formula-triage/excel-readonly.json) 记录逐格 `HasFormula`、`Value2` 与 POI 文本对照。四份文件均 ReadOnly=true、连接数 0、公式数 0、与 POI 文本不一致数 0；关闭后重新核对原始文件 SHA 不变。这里的“一致”指 Excel 与 POI 回读一致；下述既有转义解码差异不被隐去。

## 非安全 RED 与测试过程纠正

未修改的原专项最初运行 1 项通过。新增探针首轮为 57 项、3 个断言失败：POI 5.4.1 将字面 `_x000D_=1+1` 解码为 CR 加 `=1+1`，仍是 STRING、无 `<f>`。保留该用例并明确预期为既有解码行为；Excel 回读也与此一致。这是文本保真度限制，未作为 #78 安全 RED，也未扩展本轮生产修改范围。

第二轮有 1 个哈希断言失败：新证据读取器最初使用 `WorkbookFactory.create(File)`，默认读写打开 OPC 包，关闭可能重写 ZIP。已把**测试读取器**改为 `WorkbookFactory.create(file, null, true)`，加上检查前后 SHA 不变的断言，并重新生成全部交付原始证据。失败轮的本地日志/产物保留，未被包装为生产缺陷；交付文件取自其后通过的专项运行。

生产既有行为仍包括对 `-12.50` 等首字符命中文本加单引号；本轮没有增加、去除或重复该转义，没有擅自调整中文、空白和数字文本。

## 回归

环境：Windows、JDK 21.0.7，项目编译 release 17，Apache POI 5.4.1，Spring Boot 3.5.3，测试 H2 MySQL 模式。

```powershell
.\mvnw.cmd '-Dtest=ExportFileServiceFormulaInjectionTest,ExportFormulaInjectionIntegrationTest,Phase06ExportAuditIntegrationTest,ExportDataScopeAuditIntegrationTest' test
.\mvnw.cmd test
```

专项最终：57 项，0 失败、0 错误、0 跳过，BUILD SUCCESS。其中文件专项 3 项（原有 1 项、矩阵 1 项、阳性对照 1 项）、新增 HTTP 链路 2 项、既有导出权限/审批/下载 34 项、数据范围 18 项。

完整 `test`：82 个测试类、969 项，**965 通过、4 跳过、0 失败、0 错误**，BUILD SUCCESS，耗时 3 分 27 秒。Surefire XML 独立汇总与 Maven 结尾一致；跳过项为基线已有 `Issue34FirstIssueRedTest` 3 项（未设置 `issue34.red`）和 `Issue37ExpiryBatchIntegrationTest.tenThousandCandidatesPerKindHaveBoundedResults` 1 项（未设置 `issue37.large`），本轮没有新增跳过或停用测试。JaCoCo 提示缺少 execution data，未生成覆盖率报告，不宣称覆盖率已验证。

[validation.json](../testing/evidence/issue78-xlsx-formula-triage/validation.json) 包含统计、逐项跳过原因和本地原始日志 SHA256。五份原始 Maven 日志保留于本轮 worktree 根目录；只提交摘要，避免把整个运行输出及测试令牌写入版本库。根目录另保留一份未跟踪的保护快照副本 `issue78-protected-before.json`，其内容也已纳入交付证据，没有为清理状态而删除文件。

## 保护引用与限制

开工远端 main=`b54579b6b7f8b1feeda60f43906da05fb2ffda99`，本地 main=`d7fc1192967a13c7d80d2b60fdc12749fd5218df`，二者原本不同，本轮不调整。integration=`5d05109ecabb15738c0d1c7d017e5d8220f490c1`；#43=`8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2`；#81/#83=`f5c3a959cf99f69fed8e8640d845a99b23428563`；S3=`758014cbcccdfe820efb2a6fae73bc3168030484`；#22 detached=`3f2730882583fdb0cfb3b5aafb50583ea1aecade`。提交前重新实时核验，以上受保护引用均与开工一致；[保护核对记录](../testing/evidence/issue78-xlsx-formula-triage/protected-verification.json) 包含开工/提交前远端 SHA、本地引用、完整 worktree 清单及保护目录文件哈希。主工作区 `.gitignore` SHA256 仍为 `8399b5de049e3989e123b747d457a02a5d8edadf6a57867b57f1bd760fa68de3`，`.agent-teams/`、`.claude/` 的 5 份文件也逐一匹配。没有删除、reset、prune 或移动旧 worktree。

未运行真实 MySQL 8.4、LibreOffice、其他 Excel 版本；未覆盖 CSV 再导出/导入、剪贴板、手动编辑/文本转列和第三方转换器。完整 HTTP 动态矩阵覆盖 PERSON 普通/敏感链路；其他导出模块经静态检查共享同一字符串生成器，但未逐模块注入相同矩阵。没有穷举全部 Unicode 序列或超大字符串。这些限制不构成当前 XLSX 可利用的证据。

保留本轮 feature/worktree，等待 ChatGPT 独立复核；只有复核通过后才考虑快进个人 integration。
