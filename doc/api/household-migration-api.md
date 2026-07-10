# 户籍迁移 API

## 模型与状态

`residence` 只保存当前有效户籍，每人最多一条；`residence_archive` 保存迁出时的不可变快照。迁移类型为 `OUTSIDE_CITY`、`IN_CITY_CROSS_DISTRICT`，专业状态依次为 `DRAFT → UNDER_REVIEW → APPROVED → COMPLETED`，也可能进入 `REJECTED/WITHDRAWN`。审批通过只授权执行，不直接修改户籍。

## 接口

| 方法 | URL | 权限 | 数据范围 | 说明 |
|---|---|---|---|---|
| POST | `/api/migrations/in/applications` | `migration:in:create` | REGION/ALL | 同事务创建通用申请和迁入草稿 |
| PUT | `/api/migrations/in/applications/{id}` | `application:edit` + `migration:in:create` | SELF | 修改本人草稿 |
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

## 材料与特殊规则

迁入必需 `IDENTITY_PROOF` 及 `HOUSEHOLD_BOOK/ADDRESS_PROOF` 之一，跨区增加 `MIGRATION_PROOF`。迁出必需 `IDENTITY_PROOF`、`HOUSEHOLD_BOOK`；户主迁出且仍有成员时还需有效新户主或 `HOUSEHOLD_CONSENT`。这些是课程模拟规则，不宣称等同任何地区真实政务规定。

同市跨区迁出完成后生成 `TR-yyyyMMdd-xxxxxx`。迁入必须关联同人员、同目标区域、已完成且未使用的批次。最后一名有效成员迁出后家庭户进入 `PENDING_CANCELLATION`，本阶段不执行真正销户。
