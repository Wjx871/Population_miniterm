# 业务申请、材料与审批 API

## 状态与权限

申请状态：`DRAFT → UNDER_REVIEW → APPROVED/REJECTED`；待处理时可转为 `WITHDRAWN`。`COMPLETED` 留给后续实际业务执行。审批状态：`PENDING → APPROVED/REJECTED/CANCELLED`。材料状态：`PENDING → VERIFIED/REJECTED`。

新增权限：`application:view/create/edit/submit/withdraw`、`material:view/upload/delete/verify`、`approval:view/handle`。HTTP 和业务 `code` 保持一致，响应继续包含 `timestamp`。

## 申请接口

| 方法 | URL | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/applications` | `application:create` | 创建 DRAFT，编号由后端生成 |
| GET | `/api/applications` | `application:view` | 按编号、类型、状态、申请人、日期分页查询 |
| GET | `/api/applications/{id}` | `application:view` | 范围内详情 |
| PUT | `/api/applications/{id}` | `application:edit` | 仅 DRAFT，必须传 version |
| DELETE | `/api/applications/{id}` | `application:edit` | 将本人 DRAFT 软取消为 CANCELLED |
| POST | `/api/applications/{id}/submit` | `application:submit` | 转 UNDER_REVIEW 并创建 PENDING 审批 |
| POST | `/api/applications/{id}/withdraw` | `application:withdraw` | 撤回未处理审批 |
| GET | `/api/applications/{id}/approval-logs` | 申请或审批查看 | 完整只读轨迹 |

创建示例：

```json
{"businessType":"PERSON_CANCELLATION","title":"人员销户申请","targetPersonId":1,"reason":"课程演示申请","remark":"本阶段不执行销户"}
```

## 材料接口

`POST /api/applications/{id}/materials` 使用 `multipart/form-data`，字段为 `materialType`、`materialName`、`requiredFlag`、`file`。允许 PDF/JPG/JPEG/PNG 和对应 MIME，默认最大 10MB。服务端生成存储名、校验规范化路径并计算 SHA-256；数据库不保存二进制，响应不返回 `storage_path`。

`GET /api/applications/{id}/materials` 查看元数据；`GET /api/materials/{id}/download` 安全下载；`DELETE /api/materials/{id}` 仅申请人删除草稿材料；`POST /api/materials/{id}/verify` 需要 `material:verify`：

```json
{"result":"VERIFIED","comment":"材料真实有效"}
```

## 审批接口

| 方法 | URL | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/approvals/pending` | `approval:view` | 范围内待办 |
| GET | `/api/approvals/processed` | `approval:view` | 范围内已办 |
| GET | `/api/approvals/{id}` | `approval:view` | 申请、材料、主单、日志 |
| POST | `/api/approvals/{id}/approve` | `approval:handle` | 必需材料均 VERIFIED 后通过 |
| POST | `/api/approvals/{id}/reject` | `approval:handle` | 驳回意见必填 |

```json
{"comment":"材料齐全，同意办理","version":0}
```

申请人不能审批自己提交的申请，SYSTEM_ADMIN 也默认禁止自审。审批 SQL 同时限制 `PENDING` 和 `version`；冲突返回：

```json
{"code":409,"message":"记录已被其他用户处理，请刷新后重试","data":null,"timestamp":0}
```

401 表示缺少/无效令牌，403 表示权限或数据范围不足。普通申请人默认只看本人；审批人员按 DEPARTMENT/REGION/SELF；ALL 查看全部。

本阶段审批通过仅把申请置为 `APPROVED`，不删除人员、不注销家庭户、不执行迁移、不生成敏感导出文件。第三阶段业务执行器应以 `application_id` 幂等消费已批准申请，成功后再置为 `COMPLETED`。
