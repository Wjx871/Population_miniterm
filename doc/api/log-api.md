# 日志查询 API

- `GET /api/logs/operations`：操作日志分页及组合筛选。
- `GET /api/logs/logins`：从操作日志中筛选 `LOGIN_SUCCESS`、`LOGIN_FAILED`、`LOGOUT`。
- `GET /api/logs/operations/{id}`：范围内单条详情。

接口需要 `log:view`，按 ALL/DEPARTMENT/REGION/SELF 数据范围过滤，只读且无删除入口。详情和错误信息返回前再次执行审计脱敏，不返回密码、JWT 或完整身份证号。
