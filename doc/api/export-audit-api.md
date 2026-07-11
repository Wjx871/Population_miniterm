# 数据导出与审计 API

## 普通导出

`POST /api/exports/normal`，权限 `data:export:normal`。请求只接受受控模块、过滤条件和字段白名单，例如：

```json
{"module":"PERSON","filters":{"regionCode":"110000"},"fields":["name","maskedIdentityNo","gender","regionCode"]}
```

普通导出始终使用当前用户数据范围并强制脱敏，不能请求 `identityNo`、`phone`、`permitNo`、完整地址、SQL、表名或表达式。超过 `EXPORT_NORMAL_MAX_ROWS` 返回 409。

## 敏感导出

`POST /api/exports/sensitive/applications` 创建 `business_application` 与 `data_export_request`。类型固定为 `SENSITIVE_DATA_EXPORT`，通用申请入口拒绝直接创建。请求包含模块、受控过滤器、字段、理由、预计上限、标题和备注。

申请沿用通用提交和审批接口。审批通过不生成文件；具有 `data:export:sensitive:execute` 的经办人调用 `POST /api/exports/sensitive/applications/{applicationId}/execute`，请求 `{"version":2}`。实际数量不得超过批准额度或系统敏感上限。

## 记录与下载

- `GET /api/exports`：`data:export:log:view`，按 SELF/DEPARTMENT/REGION/ALL 查询。
- `GET /api/exports/{id}`：记录详情，不返回 `storage_path`、原始 SQL或导出结果。
- `GET /api/exports/{id}/download`：普通导出需要 `data:export:normal`；敏感导出需要申请人/执行人身份或范围管理员及相应权限。

每次下载增加计数、更新时间并写操作日志。状态不是 COMPLETED、物理文件不存在或已清理时返回 410。

## 文件与安全

输出格式仅 XLSX。服务端生成 UUID 存储名；工作表名由模块白名单确定；所有单元格按文本写入；以 `= + - @` 开头的内容增加前导单引号，防止公式注入。生成后计算 SHA-256。API 只返回业务文件名，不返回内部路径。

文件存于 `EXPORT_DIR`，默认 `./data/exports`。超过 `EXPORT_FILE_RETENTION_DAYS` 的完成文件由配置化任务清理并标记 EXPIRED，重复执行幂等。

主要错误：400 非法模块/字段/过滤器，401 未登录，403 权限或范围不足，404 记录不存在，409 数量/状态/版本冲突，410 文件过期或不存在。
