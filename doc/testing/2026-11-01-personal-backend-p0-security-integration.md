# Population_miniterm 个人后端 P0 安全集成

## 基线与分支

- 基线：`origin/main` `b54579b6b7f8b1feeda60f43906da05fb2ffda99`
- 施工分支：`integration/20261101-backend-p0-security`
- worktree：`D:\学习用\大二下\小学期-integration-20261101-backend-p0-security`
- Cherry-pick 期间未设置 `origin/main` upstream；未触碰 main。
- 以下未审查闭环提交不在本分支祖先中：`c31c232`、`44520b6`、`f1492cf`、`d7fc1192967a13c7d80d2b60fdc12749fd5218df`。

## 完整 Cherry-pick 列表

| 原提交 | 集成提交 | 内容 |
|---|---|---|
| `83bd9bafc1764a60c9fa108b7c3fd88e36cf8e80` | `4c773c595c09c2d4e84f968f3d0b2e5cabc70184` | 后端缺陷回归测试 |
| `47362d67452bc85e35d37e9927a3a88275d4352c` | `ef87b9c1f7c7c5a3da1585e4873720c6e564357f` | 保留主工作区资产的报告修订 |
| `ff0885e2a9ef18b4aaa7f3df05cbd71947fe2dd4` | `dfc5f8153ea72c0dcec2153034d14150e6b7ff2a` | OCR 对象授权、脱敏和导出下载独立授权 |
| `a42ec2151a798ae98efeb86a0c1c5aaceeb76e44` | `3e887f1a81392795d2573c62570e68498c1983c8` | OCR 失败诊断脱敏 |
| `d1b23206367642aac27553adea61e43c0b37a908` | `6541a0524fb9f33b91d9248f5799951bfbd9cbda` | 生命周期、注销、SELF 和导出范围 P0 |
| `9000b1b9b65d6dc058ce81598787d327d3ec96ec` | `553f539f791b65f85a02423b5ebdb7c62249c1fc` | 敏感导出申请人当前授权复核 |
| `e7bc85229f9f36b82a0aaf31f1dd5b2f242a7de1` | `274517bca64efd5165eb05cb5bd1c688a12d73cd` | 拒绝无法证明包含关系的权限变更 |

## 冲突与语义合并

- `ExportService.java` 在 P0、申请人授权复核及范围包含补丁处发生冲突。最终保留普通导出的范围过滤、敏感导出执行时以申请人的审批快照过滤、执行人下载策略经 `ExportPolicy` 单独校验；申请人必须仍为启用账号和角色、保有敏感申请权限，且部门/区划快照未变化。当前范围仅当与申请时范围相同或为 `ALL` 才执行；查询始终使用批准时范围。因此 SELF/DEPARTMENT/REGION 间不能证明包含关系的转换会拒绝，ALL 扩权也不会放宽结果集。
- `AuthRbacIntegrationTest.java` 冲突保留 QUERY_VIEWER 的 `ALL` 只读契约，并增加真正的 DEPARTMENT 合成角色验证跨部门日志隔离。
- 其他指定提交自动应用，无冲突。

## 最终功能覆盖

- 人口普通编辑不修改生命周期状态；注销门禁缺少条件时实际抛出拒绝。
- SELF 流动登记/居住证按原申请人归属；执行人不同不获得对象归属，列表、详情和操作受同一规则约束。
- 每个导出模块的普通导出按当前范围过滤；敏感导出的行数统计与 XLSX 查询共用同一批准范围。合成数据测试覆盖 XLSX 内容及行数，包含 REGION→ALL 保留原范围、SELF/DEPARTMENT→不包含当前范围时拒绝。
- `ExportPolicy` 保留下载独立授权：QUERY_VIEWER 的 `ALL` 可按既有合同查看授权范围内的导出记录元数据，但不因此成为 SYSTEM_ADMIN，也不能下载其他用户的文件。
- OCR 影印本对象级授权、敏感字段权限脱敏、异常消息脱敏，以及跨用户/重复影印本去重保护均有集成测试覆盖。

## 测试与限制

- 定向回归：**134 通过，0 失败，0 错误，0 跳过**。覆盖认证/RBAC、注销、人口、SELF、导出、OCR/影印本。
- `.\mvnw.cmd clean test`：**418 通过，0 失败，0 错误，0 跳过**。
- 测试使用隔离 H2 内存库和合成数据；没有连接真实人口数据库，也未使用真实身份信息。
- 没有修改正式数据库 Schema；现有 API 路由保持不变，补丁收紧后端授权和查询范围。
- 本轮未发现所集成路径的回归失败；MySQL 运行期及 SQL 方言兼容未在本轮复核，H2 结果不能替代 MySQL 集成验证。

## 与 main 的差异

相对 `origin/main`（`b54579b6b7f8b1feeda60f43906da05fb2ffda99`），当前差异为 35 个文件、2186 行增加、89 行删除。差异由上表七项指定修复及本报告组成；没有合并整条安全修复分支，未修改数据库 Schema、前端、运维配置或真实数据。