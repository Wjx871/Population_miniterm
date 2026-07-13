# 前后端联调测试报告

## 第一阶段：静态审计与问题复现

- 日期：2026-07-13
- 分支：`integration/frontend-backend-v1`
- 基线 HEAD：`6936fbc878a3588f6d8d32216e692efb7e7e1deb`
- 后端：本地 8080，真实 MySQL `population_miniterm`，Redis 为可选能力
- 前端：本地 Vite 5180
- 数据库结构：`information_schema` 实测 33 张表

### 已执行验证

| 验证项 | 结果 | 证据摘要 |
|---|---|---|
| Git 基线 | PASS | HEAD、develop、origin/develop、远端联调分支均为 `6936fbc` |
| 工作区安全 | PASS（有保留项） | 用户已有未跟踪 `backup/population_miniterm_before_integration_20260713_150903.sql`，本轮保留且不提交 |
| 登录 | PASS | population/123456 返回 200 和标准 LoginResponse |
| 当前用户 | PASS | `/api/auth/me` 携带 Bearer 返回 200 |
| 通用申请 page=0 | PASS | 修正演示数据并受控重放后返回 200、1 条记录 |
| 非法分页 | PASS | 重启后真实验证：负 page、零 size、非整数 page 均返回 ApiResponse 400 |
| Dashboard overview | PASS | 修正后 200；户籍 2、流动 1、有效/即将到期居住证均为 1 |
| Dashboard charts | PASS | 修正后 200；业务规模有效证件 1、状态分布 `ACTIVE=1` |
| `/api/residents` 静态扫描 | PASS | 生产和前端正式调用均未发现 |
| Axios 解包 | PASS | JSON 成功响应统一返回 `ApiResponse.data`；blob 明确 raw response |
| 分页适配 | PASS | UI 一基 current 转 Spring 零基 page，响应统一适配 Page 字段 |

### 第一阶段修改

- 修正演示数据中的居住证业务类型和状态，使其符合 Backend V1 冻结枚举。
- 修正工作台动态标题，不再渲染原始 `{{ }}`。
- 工作台统计和待办失败时显示服务端错误并保留重试，缺失值维持 `—`，不伪造 0。
- 新增前端渲染回归测试和后端演示数据契约测试。
- 建立契约矩阵和可追溯问题清单。

### 自动化门禁结果

| 命令 | 结果 |
|---|---|
| `./mvnw.cmd clean test` | PASS：323 tests，Failures 0，Errors 0，Skipped 0 |
| `./mvnw.cmd clean package` | PASS：323 tests，生成 `population-miniterm-1.0.0.jar` |
| `npm install` | PASS：依赖已是最新，0 vulnerabilities |
| `npm run check` | PASS：前端 66 tests 全通过，Vite build 成功 |
| `npm run test` | 不存在；项目实际脚本为 `test:unit`，已由 `check` 执行 |

### 尚未执行/后续门禁

第一阶段门禁已完成。五角色权限、复杂写业务、Redis 降级及完整 A-E 联调不在第一阶段复现范围内，将按契约矩阵和问题清单继续推进。
