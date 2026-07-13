# 第六阶段接口权限审计

审计范围为 16 个 `@RestController`、81 个 HTTP 映射。登录是唯一公开接口；`/api/auth/me`、退出和健康检查明确要求已认证。自动化反射测试保证其余映射方法均声明 `@PreAuthorize`。

| 模块 | HTTP 方法 | URL | 审计前权限 | 目标/最终权限 | 数据范围 | 脱敏 | 日志 |
|---|---|---|---|---|---|---|---|
| 认证 | POST | `/api/auth/login` | 公开 | 公开 | 无 | 不返回密码哈希 | 成功/失败 |
| 认证 | GET/POST | `/api/auth/me`,`/logout` | 仅全局认证 | `isAuthenticated()` | SELF | 用户 VO | 登录审计 |
| 系统 | GET | `/api/system/health` | 仅全局认证 | `isAuthenticated()` | 无 | 无 | 否 |
| 人口 | GET | `/api/persons`,`/{id}`,`/id-card/{idCard}` | 部分缺失 | `population:view` | ALL/REGION/DEPARTMENT/SELF | 身份证、手机、地址 | 查询不写敏感值 |
| 人口 | POST/PUT/DELETE | `/api/persons/**` | 部分缺失 | `population:edit` | 详情范围；DELETE 仅逻辑状态 | 响应脱敏 | 业务日志 |
| 旧居民演示 | — | `/api/residents/**` 已删除 | — | — | Phase 08 后不再兼容 | 不得作为正式数据源 | 已移除 |
| 通用申请 | GET/POST/PUT/DELETE | `/api/applications/**` | 已有 | `application:view/create/edit` | 申请人、部门、区划 | 目标信息按业务 VO | CREATE/状态日志 |
| 申请流转 | POST | `/submit`,`/withdraw` | 已有 | `application:submit/withdraw` | 本人 | 无 | 审批轨迹+操作日志 |
| 材料 | GET/POST/DELETE | `/api/applications/*/materials`,`/api/materials/*` | 已有 | `material:view/upload/delete` | 继承申请范围 | 路径不返回、文件名过滤 | 核验/下载 |
| 材料 | POST | `/api/materials/{id}/verify` | 已有 | `material:verify` | 审批范围 | 无 | 审批轨迹 |
| 审批 | GET/POST | `/api/approvals/**` | 已有 | `approval:view/handle` | 部门/区划并禁止自批 | 材料路径隐藏 | 审批日志+操作日志 |
| 迁移 | GET/POST/PUT | `/api/migrations/**` | 已有 | `migration:view/in:create/out:create/execute` | 申请/区划 | 身份字段按详情权限 | 执行日志 |
| 户籍归档 | GET | `/api/residence-archives/**` | 已有 | `migration:archive:view` | 区划 | 身份证快照脱敏 | 查询只读 |
| 注销 | GET/POST/PUT | `/api/cancellations/**` | 已有 | `cancellation:view/person:create/household:create/execute` | 申请/区划 | 身份证快照脱敏 | 执行日志 |
| 家庭户归档 | GET | `/api/household-archives/**` | 已有 | `cancellation:archive:view` | 区划 | 地址按范围 | 只读 |
| 流动人口 | GET/POST/PUT | `/api/floating-registrations/**`,`/api/floating-populations/**` | 已有 | `floating:view/create/edit/execute/close` | SELF/部门/区划 | 统一脱敏服务 | 执行/关闭 |
| 居住证 | GET/POST/PUT | `/api/residence-permits/**` | 已有 | `residence-permit:view/apply/issue/endorse/cancel/log:view/expiry:view` | 区划、申请人 | 身份证、手机、证号、地址 | 生命周期+操作日志 |
| 统计 | GET | `/api/statistics/summary`,`/charts` | 无 | `statistics:view` | 聚合 | 不返回明细 | 否 |
| 日志 | GET | `/api/statistics/logs` | 已有 | `log:view` | 授权角色 | detail 统一过滤 | 只读 |
| 普通导出 | POST | `/api/exports/normal` | 不存在 | `data:export:normal` | 强制当前范围 | 永久强制脱敏 | 导出日志+操作日志 |
| 敏感导出 | POST/GET | `/api/exports/sensitive/applications`,`/applications/{id}` | 不存在 | `data:export:sensitive:apply/execute` 或审批查看 | 申请+执行时双重范围 | 批准字段 | 审批+导出+操作日志 |
| 导出记录 | GET | `/api/exports`,`/{id}` | 不存在 | `data:export:log:view` | SELF/部门/区划/全部 | 不返回存储路径和结果集 | 只读 |
| 导出下载 | GET | `/api/exports/{id}/download` | 不存在 | `data:export:normal` 或 `data:export:sensitive:download` | 请求人/执行人/范围管理员 | 文件按导出类型 | 下载计数+操作日志 |

## 修复结论

原有问题包括人口详情和写接口缺权限、旧居民模块无权限、统计接口无权限、人口查询无数据范围、脱敏规则散落、敏感导出类型未登记专业入口。现已补齐。系统管理权限编码已初始化；仓库当前没有角色/部门管理 Controller，未来新增时必须使用对应 `system:*` 权限，普通 L2 不授权。

删除策略保持：人员主档只做逻辑状态更新；申请仅本人 DRAFT 可删除；材料仅草稿可删除；审批日志、归档、注销记录、居住证日志和导出日志没有删除接口。
