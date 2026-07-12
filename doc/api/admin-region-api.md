# 行政区划 API

统一响应使用 `ApiResponse<T>`，均需 Bearer JWT。`region:view` 可调用 `GET /api/admin-regions`、`/tree`、`/{regionCode}`、`/{regionCode}/children`；列表条件为 `parentCode/regionLevel/status/keyword`。参考数据树不按登录用户 regionCode 截断，业务统计仍须使用 `DataScopeCriteria`。

`region:manage` 可调用 `POST /api/admin-regions`、`PUT /api/admin-regions/{regionCode}`、`POST .../disable` 和 `POST .../enable`。创建字段为 `regionCode/regionName/parentCode/regionLevel/fullName/sortNo`；更新和启停须提供 `version`。编码重复、版本冲突、存在启用子节点时停用返回 409；父节点不存在返回 404；层级或循环错误返回 400；权限不足返回 403。

示例：`GET /api/admin-regions/110100/children` 返回按 `regionLevel, sortNo, regionCode` 稳定排序的直接子节点。树由一次数据库查询构造，不产生 N+1。
