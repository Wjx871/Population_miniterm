# 前后端联调测试报告

## 第二阶段：全量功能适配与系统联调

- 日期：2026-07-13
- 分支：`integration/frontend-backend-v1`
- 后端环境：Java 17 / Spring Boot 3.5.3 / MySQL 8.4.10；真实库 `population_miniterm`
- 前端环境：Vue 3 / Vite 8 / Element Plus / Pinia / Axios
- 数据库：33 张表，V4_001 至 V4_010；未清库、未改表、未提交备份
- Redis：`REDIS_ENABLED=false`，健康检查为 `MYSQL_FALLBACK`

## 完成范围

- 认证：login、`/auth/me` 刷新恢复、服务端 logout、JWT 撤销、401 去重、403 保持会话。
- 旧契约：删除 register、users CRUD、人口/家庭户物理删除和 `/api/residents` 可执行引用。
- 公共数据：行政区划、数据字典、公共选择器与失败不污染缓存。
- 核心业务：人口、家庭户、成员离户、户主变更、通用证件。
- 查询统计日志：人口、家庭户、迁移历史、Dashboard、操作日志、登录日志。
- 复杂业务：迁入、迁出、人员/家庭户注销、流动人口、居住证、重点人口建档/解除、普通/敏感导出。
- 审批执行：审批通过不自动落地；专业权限显式 execute；完成后重新读取通用与专业状态；重复 execute 为 409。

## 五角色真实 MySQL 验证

| 角色 | `/auth/me` | 数据范围 | 查询 | 写/执行边界 |
|---|---:|---|---|---|
| viewer | 200 | DEPARTMENT | 人口、家庭户、证件、三类综合查询、统计、日志、注销、重点人口、导出列表均按权限返回 | 人口写、注销创建均 403 |
| population | 200 | REGION | 核心与复杂业务查询 200 | 具备人口/重点人口/敏感导出申请的后端正式权限；无户籍专属越权 |
| household | 200 | REGION | 核心与复杂业务查询 200 | 具备户籍、迁移、注销和敏感导出执行的正式权限 |
| approver | 200 | REGION | 待审批及业务查询 200；日志 403 | 注销、重点人口、敏感导出 execute 均 403；普通导出和重点人口创建 403 |
| admin | 200 | ALL | 全量查询 200 | 专业权限以后端 `/auth/me` 返回为准 |

日志权限真实结果：viewer/admin 200，population/household/approver 403，完全按后端 `log:view` 矩阵；前端不自行推测。

## 分组回归

| 分组 | 结果 |
|---|---|
| AuthRbac + token revocation | 21 tests，0/0/0 |
| 人口 + 家庭户 + 通用证件 | 40 tests，0/0/0 |
| Phase 11 查询/统计/权限 | 22 tests，0/0/0 |
| 迁移 + 注销 + 流动/居住证 + 导出 + 重点人口 | 159 tests，0/0/0 |
| 前端最终单元/契约 | 105 tests，全部通过 |

## 最终门禁

| 命令 | 结果 |
|---|---|
| `.\mvnw.cmd clean test` | PASS：323 tests；Failures 0；Errors 0；Skipped 0；BUILD SUCCESS |
| `.\mvnw.cmd clean package` | PASS：323 tests；JAR `target/population-miniterm-1.0.0.jar` |
| `npm install` | PASS：up to date；0 vulnerabilities |
| `npm run check` | PASS：105 tests；Vite production build 成功 |
| `npm run test` | 脚本不存在；实际 `test:unit` 已由 `check` 执行 |
| `npm run build` | 已由 `check` 执行并 PASS |
| `git diff --check` | PASS（文档提交前后均要求无输出） |

Vite 仅报告第三方 pure annotation、动态 import 与大 chunk 警告，不影响构建成功；未生成可提交的 `dist/`、`node_modules/` 或 `target/` 产物。

## 工具限制与结论

Codex 内置浏览器控制运行时初始化失败，无法完成本轮五角色浏览器点击录像；已用真实 MySQL API、router/store 权限回归、源码契约测试和 production build 覆盖。除这项外部工具限制外，第二阶段代码、接口、权限、测试和构建门禁均达到验收标准，可进入第三阶段总回归。当前阶段不 push。

## 第三阶段最终版本汇合记录

- 合并提交：`69bca05`，父提交为 `da68098` 和 `9aa540d`。
- 12 个冲突文件均逐文件语义处理，未使用整树 ours/theirs；自动合并高风险文件已复核。
- 前端最终基线：189 tests，Failures 0，Errors 0，Skipped 0；`npm run check` 与 `npm run build` 成功；`npm install` 为 0 vulnerabilities。
- 后端最终基线：323 tests，Failures 0，Errors 0，Skipped 0；`clean test` 与 `clean package` 均成功。
- 真实环境沿用已完成的 MySQL 33 表验收：`/api/health` 200、数据库 UP、Redis 关闭为 `MYSQL_FALLBACK`、Vite `/api` 代理可用、admin 登录和 `/auth/me` 正常。
- 浏览器自动控制仍因 `failed to write kernel assets: path not found` 无法提供交互录像；该项不归因于项目代码，仍需人工五角色验收。
