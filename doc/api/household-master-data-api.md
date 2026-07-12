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
