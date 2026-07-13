# Backend V1 最终完成度审计

审计基线：`develop@38d6970`，2026-07-13。证据来自生产代码、MyBatis XML、V4_001—V4_010、313 项基线测试及 Phase 08—11 的 MySQL 验收报告。

| 能力 | 状态 | 结论与边界 |
|---|---|---|
| JWT、RBAC、五角色 | COMPLETE | 单一 Spring Security/JWT 体系；登录和健康检查为匿名端点 |
| 申请、材料、审批、显式执行 | COMPLETE | 审批不自动落地；专业 execute 独立授权 |
| person 唯一人口模型 | COMPLETE | 生产代码无 residents Mapper/Controller；普通人口无删除 API |
| 严格身份证校验 | COMPLETE | GB 11643 校验位、日期、性别与生日一致性 |
| 家庭户、成员、户主变更 | COMPLETE | CRUD、离户、安全户主变更、乐观锁和审计 |
| 迁入、迁出、户籍归档 | COMPLETE | 审批后显式执行、幂等、回滚、归档 |
| 人员注销、家庭户销户 | COMPLETE | 专业记录、显式执行、历史归档 |
| 流动人口、居住证 | COMPLETE | 登记、关闭、申请、签发、签注、注销和到期任务 |
| 行政区划、数据字典 | COMPLETE | 查询维护、状态控制、审计及可选缓存 |
| 通用证件 | COMPLETE | 范围、脱敏、注销；居住证由专业模块独占 |
| 重点人口 | COMPLETE | 建档、解除、历史、审批和显式执行 |
| 导出与敏感导出 | COMPLETE | 字段白名单、脱敏、审批、额度、下载和清理 |
| 综合查询 | COMPLETE | 人口、家庭户、迁移历史；范围条件进入 SQL |
| 统计与日志查询 | COMPLETE | 统一统计入口；操作/登录日志只读查询 |
| Redis 可选增强 | COMPLETE | 默认关闭、JSON cache-aside、jti 黑名单、故障降级 |
| H2/MySQL 验收 | COMPLETE | H2 自动化门禁；MySQL 8.4.10 真实门禁 |
| 用户/角色/部门在线维护 API | OUT_OF_SCOPE | V1 使用初始化脚本固化五角色和权限，不提供管理 CRUD |
| 多级审批、消息队列、微服务 | OUT_OF_SCOPE | 不属于课程设计后端 V1 |
| 政务联网、实体制卡、短信邮件 | OUT_OF_SCOPE | 需要外部政务或硬件基础设施 |
| AI 风险预测、全国完整区划 | OUT_OF_SCOPE | 不纳入 V1 数据和算法范围 |

审计结论：V1 冻结范围内无 MISSING、BROKEN 或 DUPLICATE 业务实现。`DashboardController` 和旧统计兼容路径仍保留，但规范统计入口统一为 `/api/statistics`，两者共享既有统计服务而非第二套业务事实。旧 `/api/residents` 仅能在历史说明中出现，不是正式 API。
