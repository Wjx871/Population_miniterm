# #41 / #43 / #45 独立复核与核销

日期：2026-11-11（Asia/Shanghai）。依据用户提供的 ChatGPT 独立 GitHub 源码复核结论，并在本轮核验精确 integration 基线、祖先、Blob 和历史证据，三项状态从 CODE_CANDIDATE 调整为 **FIXED_VERIFIED**。该状态表示已有修复及其历史验证经过复核，**不表示生产环境已部署或已验收**。本轮文档分支以个人 integration `af0cfd63f8729d45dac0fd978f7b1c96916b1c8b` 为基线；没有重跑 Maven 或启动 MySQL。

## 基线和源码身份

以下三项提交均经 `git merge-base --is-ancestor` 验证为上述基线的祖先：#41/#45 实现 `5e6ca5082e98a78ab012cd108738b0efcabdcadf`；#43 原始修复 `e96acbb2a9abf0abdd1e5b6bd57589efc9939123`；#43 后续证据 `8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2`。

基线以下 Git Blob 与 #41/#45 最新已复核实现提交完全一致：

| 文件 | Git Blob SHA-1 |
|---|---|
| `src/main/resources/mapper/KeyPopulationMapper.xml` | `2b78ec0720b39d66b601f16fe81a1f75c70159ca` |
| `src/main/java/com/wjx871/population/keypopulation/KeyPopulationMapper.java` | `47cf6e4e0067d04cfaf9d93f60136da10cff7694` |
| `src/main/java/com/wjx871/population/keypopulation/KeyPopulationService.java` | `c043ce673c7fbdceaebf2d51b6e65ecb44df36e0` |
| `src/main/java/com/wjx871/population/keypopulation/KeyPopulationWorkflow.java` | `6692c62400786acd0241b7869136c1ae1e051b4c` |
| `src/main/java/com/wjx871/population/keypopulation/KeyPopulationReleasePolicy.java` | `c30906505d10abe178d05c7777c30396793cd3cf` |
| `src/test/java/com/wjx871/population/keypopulation/Issue4145KeyPopulationScopeHttpIntegrationTest.java` | `93812320532a4a3f358ae4eacfd1a5eb6cc031f8` |

#43 测试 `src/test/java/com/wjx871/population/keypopulation/Issue43KeyPopulationSubmitPermissionIntegrationTest.java` 的基线 Blob `40bb412964435dc61ec2955755ccb8d74eb13344` 与 #43 实现及证据提交一致。Workflow 相比 #43 证据提交的 `6d984d2b08531610ca4c9d0f26a1c64f17f2c3b4` 有后续 #41/#45 修改，基线与最新复核的 `5e6ca508` 相同，双权限检查仍在。历史 [测试源码 SHA-256](evidence/issue41-45-tested-source-sha256.txt) 的七项均与基线 Git Blob 对应字节一致；新 worktree 的两个文件受 Git CRLF 转换影响，按 LF 归一化后与记录一致。

## #41：注销后重点人口历史责任

原问题是人员注销后，合法历史责任 REGION 用户无法解除重点人口记录。当前人员按 ACTIVE 户籍检查当前范围；仅 CANCELLED/DECEASED 且无 ACTIVE 户籍时考虑严格历史兜底：记录自身的 COMPLETED REGISTER 申请、专业明细、人员、类型和 REGISTERED 建档历史必须一致，最近合法注销归档及其已完成注销来源必须可验证；原建档来源区划和归档区划均须属于当前用户真实 `regionCodes`，不能用字符串前缀推断授权。历史链缺失或不一致不授予权限。RELEASE 创建、提交、批准和执行分别经记录管理范围检查，拒绝场景的观察业务表无成功副作用。

关键路径为 `KeyPopulationMapper.xml` 的 `regionRecordScope` / `managementScope`，`KeyPopulationService` 的创建与执行、`KeyPopulationWorkflow.validate` 提交校验，以及 `KeyPopulationReleasePolicy` 批准和执行复核。实现 `5e6ca5082e98a78ab012cd108738b0efcabdcadf` 及 Blob 核验见上。ChatGPT 独立复核结论为修复已进入指定 integration，可按已验证范围核销；不承诺所有并发事务线性化。

历史 [正式 RED](evidence/issue41-45-red-confirmed.txt) 在旧版复现，3 项失败；[最终 H2 全量](evidence/issue41-45-full-final.txt) 包含专项 24/24；[MySQL 8.4 RR](evidence/issue41-45-mysql-final.txt) 专项 24/24、既有 C2 4/4。旧快照和授权/户籍同时变化的完整线性化尚未证明；见[原实现报告](issue41-45-keypopulation-scope.md)。

## #43：通用提交绕过专属权限

原问题是通用 application submit 可绕过重点人口专属 apply 权限。`KeyPopulationWorkflow.validate()` 在状态写入前统一要求 `application:submit` 与 `key-population:apply`，保留材料及人员范围校验。历史矩阵覆盖通用路由、专业 REGISTER/RELEASE、RETURNED 重提交、直接 `ApprovalService.submit`、真实数据库撤权后复用旧 JWT，以及拒绝后申请、材料、审批、历史和日志等观察表不变。原实现为 `e96acbb2a9abf0abdd1e5b6bd57589efc9939123`，后续证据为 `8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2`；祖先及测试和 Workflow Blob 见上。ChatGPT 独立复核结论为修复已进入指定 integration，可按已验证范围核销。

[历史证据](issue43-keypopulation-submit-permission-evidence.md) 记录旧版 RED、27 项专项 GREEN、独立全量 797 项通过及首次全量中断；后续 #41/#45 的全量也包含 27 项回归通过。原 #43 独立验证主要使用 H2，**没有宣称专属 MySQL 门禁**。

## #45：INACTIVE 户籍不能构成当前归属

原问题是将历史 INACTIVE 户籍误作为当前区域归属。Mapper、Service 和 Workflow 使用 ACTIVE 户籍界定当前范围，REGION 空集合 fail-closed，不用无关申请赋予 REGION 范围；列表、count、详情、历史及管理操作沿各自适用的一致范围规则，当前区划展示不冒用历史值，ALL 显式区划筛选也只使用 ACTIVE 户籍。注销后的历史责任仅按 #41 严格规则生效。实现 `5e6ca5082e98a78ab012cd108738b0efcabdcadf`，祖先及 Blob 见上。ChatGPT 独立复核结论为修复已进入指定 integration，可按已验证范围核销。

[旧版 RED](evidence/issue41-45-red-confirmed.txt)、[H2 全量](evidence/issue41-45-full-final.txt) 与 [MySQL 真实业务场景](evidence/issue41-45-mysql-final.txt) 支持上述边界；详细矩阵见[原实现报告](issue41-45-keypopulation-scope.md)。

## 历史验证与未验证范围

#41/#45 原全量 893 项，其中 890 通过、3 跳过、无失败或错误；#43 独立全量 797 项通过；后续 #3 验证记录为 972 项、968 通过、4 跳过、无失败或错误，见 [#3 验证报告](issue3-ocr-transaction-verification.md)。这些均为历史运行，不是本轮重新执行。没有验证生产环境部署、生产规模性能、JDK 17 实际运行时门禁、物理文件上传与材料核验完整 UI；测试材料使用合成 VERIFIED 元数据。#41 的 MySQL RR 旧快照与授权/户籍同时变化的完整线性化未证明，历史来源链不完整时不会自动获得历史解除权限。#43 未完成专属 MySQL 门禁。

原 #41/#45 报告所列 worktree `C:\Users\jxgm\.codex\worktrees\issue41-45-keypopulation-scope\小学期` 本轮只读检查不存在；不能据此推断其数据或历史独立 MySQL 目录状态。远端 #41/#45 feature 本轮查询不存在。原 #43 feature 的远端仍指向 `8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2`；本地异常现场及 ignored 文件未清理。原报告记录 #43 RED/GREEN 原始日志在施工 worktree，本轮未验证其实际存在，保留已有证据与现场，不作删除、重置或修剪。历史 MySQL 目录及全部历史证据不作改动。
