# 家庭户主数据 API

统一前缀 `/api/households`。响应使用 `ApiResponse<T>`；查询权限 `household:view`，写权限 `household:edit`。除 `ALL` 外按当前用户区域在 Mapper SQL 中限制；身份证、手机号和详细地址默认脱敏，`sensitive-data:view-full` 可查看完整值。

| 方法 | 路径 | 请求/查询 | 响应 |
|---|---|---|---|
| GET | `/api/households` | householdNo、headPersonName、address、regionCode、householdType、status、page、size、sort | `Page<HouseholdView>` |
| GET | `/api/households/{id}` | 路径 ID | 户档案及成员 `HouseholdView` |
| POST | `/api/households` | householdNo、address、regionCode、householdType、establishDate、可选 headPersonId | 201 和新户档案 |
| PUT | `/api/households/{id}` | address、regionCode、householdType、establishDate、status、version | 更新后档案 |
| GET | `/api/households/{id}/members` | 路径 ID | 成员列表 |
| GET | `/api/households/{id}/members/{memberId}` | 两级 ID | 成员详情 |
| POST | `/api/households/{id}/members` | personId、relationship、joinDate | 201 和成员详情 |
| PUT | `/api/households/{id}/members/{memberId}` | relationship、version | 更新后成员 |
| POST | `/api/households/{id}/members/{memberId}/leave` | leaveDate、version | 成员状态改为 MOVED_OUT |
| POST | `/api/households/{id}/change-head` | newHeadPersonId、reason、version | 更新后的户档案 |

示例：`POST /api/households/10/change-head` 请求 `{"newHeadPersonId":22,"reason":"家庭协商","version":3}`。新户主必须已经是该户 ACTIVE 成员；接口不会自动加成员、创建户籍或迁移记录。

状态码：400 参数/值域错误；401 未认证；403 权限或数据范围不足；404 人员、家庭户或成员不存在；409 户号重复、状态冲突、重复有效关系或乐观锁冲突。普通修改不能销户，销户继续走 cancellation。

## 日期边界（#15）

家庭成员的 `joinDate >= establishDate`，相等允许。创建家庭户时初始户主的入户日期仍自动取立户日期。

- 新增成员早于当前立户日期返回 HTTP 400，提示 `入户日期不能早于家庭户立户日期`。
- 修改立户日期不得晚于本户任何成员记录的入户日期，包含 ACTIVE、LEFT、MOVED_OUT、CANCELLED 等所有状态；违反时返回 HTTP 400，提示 `立户日期不能晚于现有或历史成员的入户日期`。没有任何成员记录的户不受成员日期下限限制；只有历史成员的户仍需校验。
- 校验与写入在同一数据库事务内，先锁定家庭户行；不会自动改写成员的历史日期。日期拒绝不修改业务数据或版本，也不产生成功操作日志；失败审计可按现有机制记录。

历史数据若已有矛盾，应先核查原始证据，并通过另行审核的更正或受控迁移处理；本次不提供自动修复脚本。成员离户仍遵循既有 `leaveDate >= joinDate` 约束。
