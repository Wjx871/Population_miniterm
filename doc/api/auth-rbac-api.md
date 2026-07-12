# 登录认证与 RBAC 接口

所有响应使用 `ApiResponse<T>`：`code`、`message`、`data`、`timestamp`。除登录外，请求头使用：

```http
Authorization: Bearer <token>
```

## 登录

`POST /api/auth/login`

```json
{"username":"admin","password":"123456"}
```

成功响应的 `data`：

```json
{
  "token": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": {
    "userId": 1,
    "username": "admin",
    "realName": "系统管理员",
    "roleCode": "SYSTEM_ADMIN",
    "roleName": "系统管理员",
    "roleLevel": "L3",
    "dataScope": "ALL",
    "departmentId": 1,
    "departmentName": "系统管理部门",
    "regionCode": "110000",
    "permissions": ["population:view"]
  }
}
```

用户名不存在或密码错误返回 HTTP/业务码 401；用户或角色停用返回 403。请求和日志均不会记录密码。

## 当前用户

`GET /api/auth/me`：返回 token 对应的用户、角色、等级、数据范围、部门和权限编码。

## 退出

`POST /api/auth/logout`：记录退出日志并返回成功，前端随后删除本地 token。当前版本为无状态 JWT，没有 token 黑名单或刷新令牌；已签发 token 在到期前仍可用于请求。需要强制失效时，后续可增加黑名单或短令牌加刷新令牌机制。

## 错误示例

```json
{"code":401,"message":"缺少有效身份令牌","data":null,"timestamp":1783670400000}
```

```json
{"code":403,"message":"当前账号无权执行该操作","data":null,"timestamp":1783670400000}
```

## 角色、数据范围和权限

等级：L1 查询，L2 业务经办，L3 审批/系统管理。数据范围：ALL 全部、DEPARTMENT 本部门、REGION 本行政区、SELF 本人。

权限编码包括：`system:user:view/manage`、`system:role:view/manage`、`population:view/edit`、`household:view/edit`、`migration:view/edit`、`approval:view/handle`、`statistics:view`、`data:export`、`log:view`。

当前细粒度示范接口：`GET /api/persons` 需要 `population:view`；`POST /api/persons` 需要 `population:edit`；`GET /api/statistics/logs` 需要 `log:view` 并应用数据范围。其他 `/api/**` 当前要求登录，后续逐步补齐细粒度权限。

## 前端接入

登录成功后保存 `data.token` 和 `data.user`；Axios 请求拦截器添加 Bearer 请求头；401 清理状态并跳转 `/login`；403 显示“当前账号无权执行该操作”；退出先调用后端再在 `finally` 中清理本地状态。
