# 第二批核心业务修复：前端交接清单

本轮没有修改前端文件。以下结论来自当前组件与后端状态门禁的静态核对，供前端组员实施 RETURNED 补正交互时使用。

## 需要配合

- [ ] 在 `frontend/src/views/applications/ApplicationDetail.vue` 中，把材料上传区和材料删除权限条件从仅 DRAFT 扩展为 DRAFT 或 RETURNED；继续检查 `material:upload`、`material:delete` 权限及业务 Handler 提供的材料选项。
- [ ] 在 `frontend/src/components/business/ApplicationActionBar.vue` 与申请详情页的 Handler 组合逻辑中，让 RETURNED 申请人能触发重新提交。当前动作栏虽包含 RETURNED，但直接提交还要求 `canSubmitDirect`；迁移 Handler 的 `buildEditRoute` 总会返回专业编辑路由，因此 `canSubmitDirect` 为 false；“继续编辑专业申请”按钮又只在 DRAFT 显示。迁移 RETURNED 详情因此没有重提按钮。
- [ ] 保留 RETURNED 的“放弃并撤回”动作和既有 `application:submit` 权限判断。
- [ ] 补充前端用例：DRAFT 和 RETURNED 可按权限上传/删除材料；UNDER_REVIEW、APPROVED、COMPLETED 不展示修改入口；RETURNED 迁入/迁出能重新提交；无提交权限时不显示或不允许提交。
- [ ] 材料补正后刷新详情和材料列表。新的材料初始核验状态为 PENDING；审批人必须重新核验，核验通过后才能二次批准。

## 仍保持只读

- 迁移业务字段在 RETURNED 仍不可编辑：后端 `MigrationService.updateIn/updateOut` 只允许 DRAFT，`MigrationApply.vue` 也把非 DRAFT 设为只读。
- 不要仅为让页面重提而开放所有专业字段编辑。若业务确实需要在退回后修改迁移字段，应先设计对应的后端状态、版本和审批历史规则。

## 当前源码位置

- `frontend/src/components/business/ApplicationActionBar.vue`
- `frontend/src/views/applications/ApplicationDetail.vue`
- `frontend/src/features/applications/handlers/migrationHandler.js`
- `frontend/src/views/migrations/MigrationApply.vue`
