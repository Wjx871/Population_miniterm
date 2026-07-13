# Backend V1 权限矩阵

符号：R=可读，W=可写/申请，E=显式执行，A=审批，—=无权。所有非公开接口由方法级或类级 `@PreAuthorize` 保护；viewer、population、household、approver 默认受各自 SELF/REGION/DEPARTMENT 范围约束，admin 为 ALL。

| 模块 | 接口 | 方法 | 权限 | viewer | population | household | approver | admin | 数据范围 | 脱敏 | 审计 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 认证 | `/api/auth/login` | POST | 匿名 | R | R | R | R | R | — | 无密码回显 | 成功/失败 |
| 当前用户/退出 | `/api/auth/me`,`/logout` | GET/POST | authenticated | R | R | R | R | R | SELF | 是 | 退出 |
| 人口 | `/api/persons/**` | GET/POST/PUT | `population:view/edit` | R | R/W | R | R | R/W | SQL | 默认 | 写 |
| 家庭户 | `/api/households/**` | GET/POST/PUT | `household:view/edit` | R | R | R/W | R | R/W | SQL/写前复核 | 地址脱敏 | 写 |
| 迁移 | `/api/migrations/**` | POST/PUT/GET | `migration:*` | R | W | W/E | R | W/E | SQL/执行复核 | 是 | 申请/执行 |
| 注销 | `/api/cancellations/**` | POST/PUT/GET | `cancellation:*` | R | W | W/E | R | W/E | SQL/执行复核 | 是 | 申请/执行 |
| 流动人口 | `/api/floating-registrations/**` | 全部 | `floating:*` | R | W | R | R | W/E | SQL/区域复核 | 是 | 写/执行 |
| 居住证 | `/api/residence-permits/**` | 全部 | `permit:*` | R | W | R | R | W/E | SQL/区域复核 | 证号脱敏 | 写/执行 |
| 区划 | `/api/admin-regions/**` | GET/POST/PUT | `region:view/manage` | R | R | R | R | R/W | 公共参考数据 | — | 写 |
| 字典 | `/api/dictionaries/**` | GET/POST/PUT | `dictionary:view/manage` | R | R | R | R | R/W | 启用项/管理项 | — | 写 |
| 通用证件 | `/api/certificates/**` | GET/POST/PUT | `certificate:view/edit` | R | W | R | R | W | SQL | 证号脱敏 | 写 |
| 重点人口 | `/api/key-populations/**` | 全部 | `key-population:*` | — | R/W/E | — | R | R/W/E | SQL/执行复核 | 严格 | 写/执行 |
| 申请 | `/api/applications/**` | 全部 | `application:*` | R | W | W | R | W | SQL | 是 | 状态变更 |
| 材料 | `/api/**/materials` | 全部 | `material:*` | R | W | W | 核验 | 全部 | 继承申请 | 路径隐藏 | 下载/核验 |
| 审批 | `/api/approvals/**` | GET/POST | `approval:view/handle` | — | — | — | R/A | R/A | SQL | 是 | 全部 |
| 导出 | `/api/exports/**` | 全部 | `data:export:*` | 普通 | 普通/申请 | 普通/执行 | 审批 | 全部 | 与列表一致 | 默认脱敏 | 全部 |
| 综合查询 | `/api/query/**` | GET | 模块 view | R | R | R | R | R | SQL | 默认 | — |
| 统计 | `/api/statistics/**` | GET | `statistics:view` | R | R | R | R | R | SQL+缓存键 | 聚合 | — |
| 日志 | `/api/logs/**` | GET | `log:view` | — | — | — | R | R | SQL | payload 脱敏 | 只读 |
| 健康 | `/api/health` | GET | 匿名 | R | R | R | R | R | — | 无秘密 | — |

审批角色不会因 `approval:handle` 自动获得任何专业 execute 权限。越权详情优先返回 404；少数需要明确告知当前主体范围冲突的写操作返回 403。应用草稿和申请材料可删除，但 person、household 等核心主档不提供普通物理删除接口。
