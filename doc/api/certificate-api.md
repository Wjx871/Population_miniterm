# 通用证件 API

通用证件与 `residence_permit` 专业模块严格分离，禁止 `RESIDENCE_PERMIT` 类型，也不复制 `person.id_card`。

`certificate:view`：

- `GET /api/certificates`，条件包括 `personId/personName/certificateType/certificateNo/status/issueDateFrom/issueDateTo/expireDateFrom/expireDateTo/expiringWithinDays/page/size`；
- `GET /api/certificates/{certificateId}`；
- `GET /api/persons/{personId}/certificates`。

查询在 Mapper SQL 中关联 person 应用 ALL/REGION/DEPARTMENT/SELF 范围；越权详情按不存在返回 404。证件号、身份证、手机和地址默认脱敏，只有 `sensitive-data:view-full` 可查看完整值。ACTIVE 且有效期已过的数据在响应中显示为 `EXPIRED`。

`certificate:edit`：`POST /api/certificates`、`PUT /api/certificates/{id}`、`POST /api/certificates/{id}/cancel`。创建字段为 `personId/certificateType/certificateNo/issueDate/expireDate`；更新另需 `version`；注销需 `reason/version`。支持 `PASSPORT/DRIVER_LICENSE/OTHER`，以启用字典项为准。重复号码、已注销人员、重复注销或恢复注销证件返回 409；人员/证件不存在返回 404；日期、类型错误返回 400；越权返回 403。没有 DELETE 接口，所有写操作均审计且日志不记录完整证件号。
