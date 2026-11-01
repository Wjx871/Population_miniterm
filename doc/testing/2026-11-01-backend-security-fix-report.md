# 2026-11-01 后端安全修复报告

## 基线与范围

- 起点：`test/20261101-backend-deep-regression`，提交 `47362d67452bc85e35d37e9927a3a88275d4352c`。已 `fetch origin` 并确认远端测试分支指向该提交。
- 修复分支：`fix/20261101-backend-security-hardening`，独立 worktree。
- 本次只修改 OCR 影印本、人口建档绑定、导出下载授权相关后端代码/测试及 API/测试文档；未修改正式数据库 SQL。

## 缺陷、根因与修复

### OCR 影印本详情泄露与对象级读写越权

- **根因：** 上传回填和只读详情共用无条件返回 OCR 完整身份证号/地址的视图；详情仅按 `imageId` 读取，未验证草稿上传者或已绑定人口记录的数据范围。SHA-256 全局去重命中后会复用别人的行并继续改 OCR 状态；人口创建入口也未校验影印本上传者。
- **修复：** 上传与详情视图分开生成。上传回填保留获准经办人的完整 OCR 字段；详情按 `PersonMapper.selectScopedById` 校验已绑定人口范围，未绑定草稿限上传者或 `SYSTEM_ADMIN`。详情只有 `sensitive-data:view-full` 可见完整证件号和地址，其余用户返回脱敏字段。越权按 404 隐藏对象存在性。
- **状态/文件不变量：** 跨上传者 SHA 命中及任何已绑定行的重复上传均在 OCR 调度前返回 409；只清理本次请求新生成的重复临时文件，既有影印本文件及数据库引用不变。OCR 更新 SQL 要求当前上传者匹配且 `person_id IS NULL`；绑定 SQL 限定观察到的上传者和未绑定状态，并检查受影响行数，竞争时回滚建档。
- **补充缺陷根因：** `PaddleOcrHttpClient` 曾把 `RestClientException.getMessage()`（可能含 HTTP 响应正文）拼入异常消息；`uploadAndRecognize` 又将其写入 `ocr_error`、上传/详情 DTO 和 `operation_log.error_message`。
- **补充修复：** HTTP 错误仅保留状态码类别（如 `OCR_HTTP_502`），连接失败、非法响应、未配置和其他失败分别记录稳定错误类别。客户端异常不保留原始异常消息或 cause，服务层不再持久化、返回或审计不可信 OCR 原文；失败路径不写异常原文到普通日志。故障诊断仍可区分 HTTP 状态、不可达和响应结构错误。
- **合法审批移交：** 普通建档仍仅能绑定当前用户上传的草稿。既有 `ApprovalService.approveAndCreatePerson` 在核验审批人、范围和必需材料后，通过独立的 `createForApproval` 路径允许绑定该申请人上传的影印本；审批状态流和权限检查保持原样。
- **影响范围：** 详情字段权限和对象授权收紧；合法上传表单回填及经审批的恢复登记建档继续工作。响应路由及字段形状不变。

### 导出下载授权混淆

- **根因：** `ExportService` 将 `DataScope.ALL` 当作管理员，并由 Controller 的“普通或敏感下载权限任一”粗粒度入口放行；viewer 因普通导出权限可到达敏感文件下载。
- **修复：** `ExportPolicy.mayDownload` 作为 Controller、Service、列表/详情 `downloadable` 的共享规则。普通导出要求普通导出权限及申请人/执行人身份，或 `SYSTEM_ADMIN`。敏感导出要求申请人/执行人身份且具备普通或敏感下载入口权限；跨用户下载限同区域、同时具有 `approval:view` 和 `data:export:sensitive:download` 的授权审批人员，或 `SYSTEM_ADMIN`。`ALL` 本身不产生管理员或敏感字段权限。
- **影响范围：** 文件下载身份和范围校验收紧；申请、审批、显式执行和下载审计不变。详情/列表的 `downloadable` 与实际下载使用同一授权规则，非 `COMPLETED` 文件仍不可下载。
- **缺失记录语义：** `canDownload` 对不存在的记录允许请求进入 Service，Service 按既有 API 契约返回 404；记录存在但调用者无权限仍返回 403。该逻辑不提供文件访问权限，无需修改。

### Fresh 初始化 viewer 范围差异（交接项）

- 静态检查仍发现主初始化 SQL 的 `QUERY_VIEWER` 为 `DEPARTMENT`，而 `V4_012_dashboard_viewer_scope.sql` 将其改为 `ALL`；Fresh 初始化脚本读取主 SQL。此差异未在本次修改。
- 请数据运维负责人确认 Fresh 与升级迁移后的最终 viewer 范围，并在隔离 MySQL 库核验初始化结果；如需统一，请由数据运维维护主 SQL/迁移执行顺序并补初始化验收。**本次未执行真实库写入。**

## 新增/保留回归用例

- OCR：上传者回填完整字段；viewer 草稿 ID 猜测访问被拒；population/viewer/approver 读取范围内绑定详情时脱敏；household（有敏感查看权限）及 SYSTEM_ADMIN 读取获授权完整字段；approver-other 越范围读取被拒。
- 影印本数据完整性：其他上传者同哈希上传返回 409，OCR 状态、上传者、SHA、存储名、路径和原文件不变，临时重复文件被清理；同一上传者重复上传已绑定图片（`skipOcr` 与 OCR 重试）均为 409，完整记录和文件集合不变；普通人口建档中的 admin/approver 猜测他人草稿 ID 被拒，上传者及既有审批流程合法绑定通过。
- 导出：viewer 和非所有 population 仅凭普通导出权限不能下载他人 `SENSITIVE_APPROVED`；直接下载返回 403，`downloadable=false`。同区域授权 approver、申请人、执行人和 SYSTEM_ADMIN 正常下载；跨区域 approver 被拒、SYSTEM_ADMIN 可跨范围。保留普通导出所有权、审批执行、审计与过期文件用例。
- 缺陷复现证据：修复前定向集合 41 项，39 通过、2 失败（普通导出越权下载、OCR 详情泄露）；新增敏感导出直连测试修复前独立运行 1 项，实际 HTTP 200、预期 403。所有断言均保留并在修复后转绿。
- OCR 错误正文复现：HTTP 502 合成响应含测试身份证标记、地址和密钥标记；修复前用例确认上传响应、数据库 `ocr_error`、详情和操作审计四处均可观察到这些标记。修复后四处只包含安全错误类别；服务不可达和非法响应结构分别由 `OCR_UNAVAILABLE`、`OCR_INVALID_RESPONSE` 门禁覆盖。

## 验证结果

| 验证 | 结果 |
|---|---:|
| Phase 06 Export 定向测试 | 34 通过，0 失败，0 跳过 |
| Phase 14 影印本测试 | 12 通过，0 失败，0 跳过 |
| Phase 14 OCR 测试（本地 HTTP stub） | 8 通过，0 失败，0 跳过 |
| Phase 02 审批兼容回归 | 21 通过，0 失败，0 跳过 |
| `.\mvnw.cmd clean test` 全量后端回归 | 388 通过，0 失败，0 跳过 |
| `git diff --check` | 通过 |
| MySQL 写入验证 | 未运行：没有隔离测试库连接配置；未连接正在运行的 MySQL 服务 |

OCR 测试使用 `MockRestServiceServer`，不访问外部 OCR 服务。回归数据为合成样例。

## 尚未解决的问题与风险

- Fresh viewer 范围不一致仍待数据运维处理及隔离 MySQL 验收；本次只有静态证据，没有数据库运行结果。
- MySQL、Redis 和外部 OCR 的真实集成未验证；本次证明范围为 H2 后端回归及 OCR stub。
- 敏感导出跨用户策略现由角色权限、审批权限、区域及所有者共同决定；后续若角色授权契约变化，应同步更新 `ExportPolicy`、下载路由和本报告对应测试。
