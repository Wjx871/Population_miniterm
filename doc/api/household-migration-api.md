# 户籍迁移 API

## 模型与状态

`residence` 只保存当前有效户籍，每人最多一条；`residence_archive` 保存迁出时的不可变快照。迁移类型为 `OUTSIDE_CITY`、`IN_CITY_CROSS_DISTRICT`，专业状态依次为 `DRAFT → UNDER_REVIEW → APPROVED → COMPLETED`，也可能进入 `REJECTED/WITHDRAWN`；迁入草稿取消时业务申请与专业明细会进入 `CANCELLED`。审批通过只授权执行，不直接修改户籍。

## 接口

| 方法 | URL | 权限 | 数据范围 | 说明 |
|---|---|---|---|---|
| POST | `/api/migrations/in/applications` | `migration:in:create` | REGION/ALL | 同事务创建通用申请和迁入草稿 |
| PUT | `/api/migrations/in/applications/{id}` | `application:edit` + `migration:in:create` | SELF | 修改本人草稿 |
| DELETE | `/api/applications/{id}` | `application:edit` | 申请人本人/管理员 | 取消 `DRAFT` 通用申请；迁移申请同事务将迁移明细置 `CANCELLED` 并释放批次，原批次号保留 |
| POST | `/api/migrations/out/applications` | `migration:out:create` | REGION/ALL | 原户籍由后端读取 |
| PUT | `/api/migrations/out/applications/{id}` | `application:edit` + `migration:out:create` | SELF | 修改本人草稿 |
| GET | `/api/migrations/applications/{id}` | `migration:view` | 沿用申请可见范围 | 查询申请、专业详情、当前户籍及成员 |
| POST | `/api/migrations/in/applications/{id}/execute` | `migration:execute` | REGION/ALL | 执行已批准迁入 |
| POST | `/api/migrations/out/applications/{id}/execute` | `migration:execute` | REGION/ALL | 归档并执行迁出 |
| GET | `/api/residence-archives` | `migration:archive:view` | 区域过滤 | 分页查询，未授权角色身份证脱敏 |
| GET | `/api/residence-archives/{id}` | `migration:archive:view` | 区域校验 | 归档详情 |

创建迁入示例：

```json
{"personId":1,"migrationType":"OUTSIDE_CITY","fromRegionCode":"370100","fromAddress":"济南市示例地址","toRegionCode":"110101","toHouseholdId":10,"inDate":"2026-07-12","reason":"投靠亲属","title":"户口迁入申请"}
```

创建迁出示例：

```json
{"personId":1,"migrationType":"OUTSIDE_CITY","toRegionCode":"310000","toAddress":"上海市示例地址","outDate":"2026-07-12","reason":"工作调动","newHeadPersonId":2,"title":"户口迁出申请"}
```

执行请求为 `{"version":2}`。版本或状态冲突、重复执行、重复当前户籍和批次重复使用返回 HTTP 409；参数错误返回 400；越权返回 403；资源不存在返回 404。

## 取消迁入草稿与批次释放（#19）

`DELETE /api/applications/{id}` 仅允许取消 `DRAFT` 申请（`ApplicationService.cancelDraft`），成功返回 HTTP 200。对迁入/迁出迁移草稿：

- `business_application.status` 变为 `CANCELLED`，同一事务内 `migration_in.business_status` 或 `migration_out.business_status` 同步变为 `CANCELLED`，并写入 `sys_approval_log` 的 `CANCEL` 记录；任一步失败整个取消事务回滚。
- `migration_in.transfer_batch_no` **保留原值、不置空**；`migration_in` 的生成列 `active_transfer_batch_no` 对 `CANCELLED` 行自动为 `NULL`，因此该批次可被同市跨区迁入重新引用。
- 取消只释放批次，不删除原申请/材料/审批记录；新建迁入仍必须重新满足人员、目标区划、批次有效性、材料、审批和执行权限校验。
- 同一批次存在活动迁移（`DRAFT`/`UNDER_REVIEW`/`APPROVED`）或已完成迁移（`COMPLETED`）时，创建或更新迁入返回 HTTP 409（批次占用/已使用）；`REJECTED`/`WITHDRAWN`/`RETURNED` 仍按既有契约占用批次。重复取消、对非 `DRAFT` 申请取消或版本冲突同样返回 409。
- 错误文案示例：`迁移批次已被占用，请使用未使用的合法批次`；`迁移批次无效`；`迁移批次不匹配或已使用`。

## 材料与特殊规则

迁入必需 `IDENTITY_PROOF` 及 `HOUSEHOLD_BOOK/ADDRESS_PROOF` 之一，跨区增加 `MIGRATION_PROOF`。迁出必需 `IDENTITY_PROOF`、`HOUSEHOLD_BOOK`；户主迁出且仍有成员时还需有效新户主或 `HOUSEHOLD_CONSENT`。这些是课程模拟规则，不宣称等同任何地区真实政务规定。

同市跨区迁出完成后生成 `TR-yyyyMMdd-xxxxxx`。迁入必须关联同人员、同目标区域、已完成且未使用的批次；“未使用”指该批次没有任何非 `CANCELLED` 的 `migration_in` 记录，已 `CANCELLED` 的旧草稿不占用批次，`COMPLETED` 迁入记录永久占用。最后一名有效成员迁出后家庭户进入 `PENDING_CANCELLATION`，本阶段不执行真正销户。
