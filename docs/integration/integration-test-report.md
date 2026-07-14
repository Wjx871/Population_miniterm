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
- 此处记录的是汇合当时的历史限制；2026-07-14 已通过下述 Playwright 最终冒烟补齐五角色真实浏览器验收。

## 最终浏览器自动化冒烟（2026-07-14）

### 环境与执行方式

- 分支：`integration/final-reconcile`；开始提交：`6d0a8ebdc37ad893e0cfc1ceb388fb3bb1d2f0fb`。
- 后端：`http://127.0.0.1:8080`，真实 MySQL `population_miniterm`，健康检查 200、数据库 `UP`、Redis `DISABLED`、缓存模式 `MYSQL_FALLBACK`。
- 前端：Vite `http://127.0.0.1:15180`，通过 `/api` 代理访问后端。
- 浏览器：Playwright Chromium 149；配置默认单 worker，角色之间清理 cookie、localStorage 和 sessionStorage 后重新登录。
- 命令：设置 `E2E_PASSWORD` 后运行 `npm run test:e2e`；HTML 报告使用 `npm run test:e2e:report` 查看。

### 自动化覆盖与结果

| 项目 | 结果 |
|---|---|
| Playwright 用例 | 11/11 通过，最终增强版耗时 57.1 秒 |
| 角色 | viewer、population、household、approver、admin，共 5 类 |
| 管理员页面巡检 | 17 个：工作台、人口、户籍、迁入、迁出、注销、流动人口、居住证、通用证件、重点人口、审批、综合查询、统计大屏、操作日志、登录日志、行政区划、数据字典 |
| 认证生命周期 | 登录成功、失败登录 401、`/auth/me`、刷新恢复、退出与会话清理全部通过 |
| 权限边界 | viewer 无写/审批；population 无户籍写/日志；household 无人口写/日志；approver 无执行/日志；越权进入 403 页后会话保持 |
| HTTP 500 | 0 个非预期响应 |
| 控制台 error | 0 个非预期 error |
| 页面异常 | 0 个 `pageerror` / 未捕获渲染异常 |
| 文本异常 | 未发现 `undefined`、`[object Object]` 或原始 `{{` 模板文本 |

审批中心稳定复现 Vue 已知警告 `Component provided template option but runtime compilation is not supported`。警告已作为 Playwright 附件记录；审批标题、待办/已办标签和页面请求均正常，因此按既定规则不阻断验收。

测试全程不清库、不修改用户、不触发创建、审批、执行、注销或删除等写业务。失败时配置会保留截图和 trace，始终生成 HTML 报告；`frontend/test-results/` 与 `frontend/playwright-report/` 已加入忽略规则，不提交运行产物。

### 仍保留人工验收项

- 涉及真实数据变更的新增、编辑、审批与专业执行闭环，仅在专用可回滚验收数据上人工确认。
- 导出文件内容、下载后的办公软件兼容性，以及大数据量下的耗时与内存表现。
- 移动端/小屏布局、不同浏览器兼容性、生产域名 TLS、反向代理和部署环境安全配置。

结论：最终浏览器冒烟门禁通过，未发现阻断发布的前后端契约、权限、渲染或 HTTP 500 问题。

## 人工验收集中回归（2026-07-14，已完成）

- P0 根因证据：正式 MySQL 日志最后一层为 `Cannot execute statement in a READ ONLY transaction`，调用点是注销详情中的 `CancellationMapper.lockResidence`。
- 定向后端：Phase04 21/21；Phase05 38/38；Failures 0，Errors 0，Skipped 0。
- 定向前端：195/195；新增审批 SFC、Radio value、家庭户状态/搜索、迁出资格、人口关系和标签布局契约。
- 数据核对：700001 通用申请、流动人口和最终居住证存在，专业申请缺失；700002 通用申请和注销专业记录存在。王家兴没有当前有效户籍，不补造用户测试数据。
- DEMO SQL：`scripts/data/fix-manual-acceptance-demo.sql` 仅匹配 `DEMO-PERMIT-001`，使用 `NOT EXISTS` 保证幂等；尚未执行正式库。
- DEMO SQL 回滚验证：首次插入 1、第二次插入 0、事务内记录 1、回滚后记录 0，确认幂等且未持久修改正式库。
- 后端最终门禁：`clean test` 与 `clean package` 均 PASS，326 tests，Failures 0，Errors 0，Skipped 0，JAR 构建成功。
- 前端最终门禁：`npm run check` 与独立 `npm run build` 均 PASS，195/195 tests。
- 浏览器专项回归：9/9 PASS；全量增强回归：20/20 PASS。全程 HTTP 500 为 0、控制台 error 为 0、`pageerror` 为 0，runtime template warning 与 Element Plus radio label warning 均为 0。
- 最终结论：8 项代码/界面问题 CLOSED；FUNC-PERMIT-001 因正式库缺少 700001 专业记录保持 DATA_REQUIRED，当前 UI 对 404 给出明确提示，需展示专业详情 200 时再执行幂等 SQL。
