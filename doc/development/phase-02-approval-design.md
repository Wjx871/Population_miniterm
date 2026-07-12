# 第二阶段申请审批设计

## 聚合关系

具体人口、户籍、迁移和导出业务只保存或引用 `application_id`，避免复制审批字段并把流程耦合到每张业务表。`business_application` 是申请主单，`sys_approval_request` 是当前单级审批主单；本阶段每个申请通过唯一约束只产生一个审批请求。材料归属申请，审批日志同时关联申请和审批请求。

`operation_log` 记录系统请求行为，供安全审计；`sys_approval_log` 是不可修改的业务轨迹，记录 CREATE、SUBMIT、MATERIAL_VERIFY/REJECT、APPROVE、REJECT、WITHDRAW、CANCEL。两者不能互相替代。

## 状态机与并发

状态规则集中在 `ApplicationStateMachine` 和审批服务。只有 DRAFT 可编辑、取消或提交；UNDER_REVIEW 可审批或在 PENDING 时撤回；APPROVED、REJECTED、WITHDRAWN 为本阶段终态。REJECTED 不循环回草稿，需要新建申请。

申请状态更新使用 `WHERE status=? AND version=?`；审批决策使用 `WHERE status='PENDING' AND version=?`。受影响行为 0 时返回 409。状态、审批主单和 `sys_approval_log` 在同一事务写入，从数据库层阻止重复点击生成两条成功日志。

## 文件存储

本地目录由 `APP_UPLOAD_DIR` 配置，默认 `./data/uploads`，最大文件由 `APP_UPLOAD_MAX_SIZE_MB` 配置。仅允许 PDF/JPEG/PNG；服务端生成 UUID 文件名、规范化并限制在根目录、计算 SHA-256。数据库仅保存元数据和内部路径，API 不暴露路径。上传数据库写入失败会删除新文件；物理删除失败会停止元数据删除并返回错误。真实上传目录已加入 `.gitignore`。

## 数据权限

申请人仅操作本人草稿并默认查询本人申请。审批用户按角色 `data_scope` 在 SQL 与详情服务双重校验：ALL 不过滤，DEPARTMENT 按申请部门，REGION 按申请区域，SELF 按申请人。普通审批人和管理员均默认不能自审；本阶段没有开放管理员自审配置。

## 阶段边界与第三阶段接入

本阶段不执行人员/家庭户销户、迁入迁出落表、户籍归档、重点人口变更或敏感导出。第三阶段为每种 `business_type` 实现独立执行器：只接收 APPROVED 申请，以 `application_id` 做幂等键，在单独事务中更新具体业务，成功后把申请置为 COMPLETED，并追加执行日志。无需改变本阶段审批表结构。
