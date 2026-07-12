# 人口管理信息系统 — 后端 API 对接文档

> 本文档面向**前端开发者**，描述后端已实现的所有功能模块、接口、字段、鉴权与全局约定。
>
> - 后端地址：`http://localhost:8080`
> - 统一前缀：`/api`
> - 鉴权：除 `/api/auth/**` 与 `/api/system/**` 外，所有接口需在 Header 中携带 `Authorization: Bearer <accessToken>`

---

## 0. 全局约定

| 项 | 说明 |
| --- | --- |
| 请求 / 响应 Content-Type | `application/json;charset=UTF-8` |
| 时间格式 | `LocalDate` 用 `yyyy-MM-dd`；`LocalDateTime` 用 `yyyy-MM-dd HH:mm:ss` |
| 分页参数 | 列表接口用 query 传 `current`（默认 1）、`size`（默认 10） |
| 分页响应 | `{ total, pages, records, current, size }` |
| 主键类型 | 全部为 `Long`（自增 ID），前端用 `number` 接收 |
| 业务状态码 | `200` 成功；`400` 参数错误 / 业务校验失败；`401` 未登录或 Token 无效；`500` 服务器错误 |
| 异常消息 | 直接读取 `result.message` 弹 Toast |

### 统一响应结构

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1710000000000
}
```

### 全局错误处理建议

- `code === 401`：Token 失效或未登录 → 清空本地登录态并跳登录页
- `code !== 200`：弹 `message` 提示
- `code === 200` 但 `data` 为空：通常对应"删除/更新/新增"成功，前端按"成功"处理即可

---

## 1. 认证模块（`/api/auth`）

负责登录、注册与 Token 颁发。**不需要** Token。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| POST | `/api/auth/login` | 用户名 + 密码登录，成功返回 JWT 与用户信息 |
| POST | `/api/auth/register` | 用户自助注册（默认分配 `roleId=1`，状态"启用"） |

### 登录

请求体：

```json
{
  "username": "admin",
  "password": "123456"
}
```

响应 `LoginVO`：

```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "userId": 1,
  "username": "admin",
  "realName": "管理员",
  "roleName": "超级管理员"
}
```

### 注册

请求体：

```json
{
  "username": "zhangsan",
  "password": "123456",
  "confirmPassword": "123456",
  "realName": "张三",
  "phone": "13800000000"
}
```

校验规则：
- 用户名、密码、确认密码均不能为空
- 两次密码必须一致
- 用户名全局唯一

> 注：注册时密码以明文传给后端即可，后端会自动哈希入库。

---

## 2. 系统模块（`/api/system`）

系统级信息查询，**不需要** Token，可用于健康检查与首页展示。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/system/health` | 健康检查，返回 `{status, timestamp, redis}` |
| GET | `/api/system/info` | 系统基础信息（`name / version / description`） |

---

## 3. 用户管理模块（`/api/users`）

管理后台账号。需要 Token。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/users?username=&status=&current=&size=` | 分页 + 模糊查询用户 |
| GET | `/api/users/list` | 获取全部用户（下拉框用） |
| GET | `/api/users/{id}` | 根据 ID 查询用户（密码已脱敏） |
| POST | `/api/users` | 新增用户 |
| PUT | `/api/users/{id}` | 更新用户 |
| DELETE | `/api/users/{id}` | 删除用户 |

**SysUser 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | Long | 主键，新增时无需传 |
| `username` | string | 用户名，唯一 |
| `passwordHash` | string | 密码（创建时传明文即可，后端会哈希） |
| `roleId` | Long | 角色 ID |
| `realName` | string | 真实姓名 |
| `phone` | string | 手机号 |
| `status` | string | `启用` / `禁用` |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `roleName` | string | 仅回显用，不会入库 |

---

## 4. 人员管理模块（`/api/persons`）

人口信息核心表，系统最常用的模块。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/persons` | 条件分页查询（推荐前端用此接口） |
| GET | `/api/persons/list?status=` | 获取所有人员（按状态过滤） |
| GET | `/api/persons/{id}` | 按 ID 查询 |
| GET | `/api/persons/id-card/{idCard}` | 按身份证号精确查询 |
| GET | `/api/persons/name/{name}` | 按姓名模糊查询 |
| GET | `/api/persons/phone/{phone}` | 按电话查询 |
| POST | `/api/persons` | 新增人员（身份证号重复会被拒绝，返回 400） |
| PUT | `/api/persons/{id}` | 更新人员 |
| DELETE | `/api/persons/{id}` | 删除人员 |
| GET | `/api/persons/statistics` | 人员统计：总数、男性数、女性数、正常状态数 |

**PersonQueryDTO 查询条件**（`GET /api/persons` 直接作为 query 传）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | string | 姓名模糊 |
| `idCard` | string | 身份证精确 |
| `gender` | string | `男` / `女` |
| `ethnicity` | string | 民族 |
| `status` | string | 人员状态 |
| `phone` | string | 电话模糊 |
| `birthDateStart` | date | 出生日期起 (`yyyy-MM-dd`) |
| `birthDateEnd` | date | 出生日期止 (`yyyy-MM-dd`) |
| `current` / `size` | long | 分页参数 |

**Person 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `personId` | Long | 主键 |
| `name` | string | 姓名 |
| `gender` | string | 性别 |
| `idCard` | string | 身份证号（唯一） |
| `birthDate` | date | 出生日期 |
| `ethnicity` | string | 民族 |
| `phone` | string | 电话 |
| `currentAddress` | string | 现居住地址 |
| `status` | string | `正常` / `死亡` / `迁出` 等 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |

---

## 5. 户籍管理模块（`/api/households`）

户籍登记 + 户籍成员管理。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/households?householdNo=&headPersonName=&status=&current=&size=` | 分页查询户籍列表（自动回填 `headPersonName` 与 `memberCount`） |
| GET | `/api/households/list` | 全量户籍（下拉选择） |
| GET | `/api/households/{id}` | 户籍详情（带回户主姓名） |
| POST | `/api/households` | 新建户籍 |
| PUT | `/api/households/{id}` | 更新户籍 |
| DELETE | `/api/households/{id}` | 删除户籍 |
| GET | `/api/households/{id}/members` | 查询某户籍下所有"有效"成员（含 `personName`、`personIdCard`） |
| POST | `/api/households/{id}/members` | 添加户籍成员（自动写入 `status="有效"`） |
| DELETE | `/api/households/{householdId}/members/{memberId}` | 移除户籍成员（**软删除**，status 置为"无效"） |

**Household 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `householdId` | Long | 主键 |
| `householdNo` | string | 户号 |
| `headPersonId` | Long | 户主人员 ID |
| `address` | string | 户籍地址 |
| `establishDate` | date | 户籍建立日期 |
| `status` | string | 户籍状态 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `headPersonName` | string | 冗余：户主姓名（不回存） |
| `memberCount` | int | 冗余：有效成员数（不回存） |

**HouseholdMember 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `memberId` | Long | 主键 |
| `householdId` | Long | 所属户籍 ID |
| `personId` | Long | 人员 ID |
| `relationship` | string | 与户主关系（`户主` / `配偶` / `子女` / `父母` 等） |
| `joinDate` | date | 加入日期 |
| `leaveDate` | date | 离开日期 |
| `status` | string | `有效` / `无效` |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `personName` | string | 冗余：人员姓名 |
| `personIdCard` | string | 冗余：身份证号 |

> 前端在做"户籍档案"页时，**不必**二次联表查询户主姓名 / 成员数 / 成员姓名——后端已经回填好。

---

## 6. 居民管理模块（`/api/residents`）

兼容旧表 `residents` 的接口集合。**仅做基础的 CRUD**，无统计、无关联。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/residents?name=&idCardNumber=&current=&size=` | 分页 |
| GET | `/api/residents/list` | 全量 |
| GET | `/api/residents/{id}` | 详情 |
| POST | `/api/residents` | 新增 |
| PUT | `/api/residents/{id}` | 更新 |
| DELETE | `/api/residents/{id}` | 删除 |

> `Residents` 是简化版居民表（`name, gender, birthDate, idCardNumber, phoneNumber, province, city, district, address, active`）。仅在历史数据迁移或简化登记场景下使用，否则优先使用 `/api/persons`。

---

## 7. 迁入管理（`/api/migrations/in`）

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/migrations/in?startDate=&endDate=&current=&size=` | 分页查询迁入记录 |
| GET | `/api/migrations/in/{id}` | 详情 |
| POST | `/api/migrations/in` | 新增迁入记录 |
| DELETE | `/api/migrations/in/{id}` | 删除迁入记录 |
| GET | `/api/migrations/in/statistics?startDate=&endDate=` | 迁入统计：总数 `total` |

> 日期参数格式：`yyyy-MM-dd`，均可选。

**MigrationIn 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `inId` | Long | 主键 |
| `personId` | Long | 迁入人员 ID |
| `fromAddress` | string | 迁出地 |
| `toHouseholdId` | Long | 迁入户籍 ID |
| `inDate` | date | 迁入日期 |
| `reason` | string | 迁入原因 |
| `operatorId` | Long | 经办人（操作员）ID |
| `createdAt` | LocalDateTime | 创建时间 |
| `personName` | string | 冗余：人员姓名 |
| `personIdCard` | string | 冗余：身份证号 |
| `householdNo` | string | 冗余：迁入户户号 |
| `operatorName` | string | 冗余：经办人姓名 |

---

## 8. 迁出管理（`/api/migrations/out`）

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/migrations/out?startDate=&endDate=&current=&size=` | 分页查询迁出记录 |
| GET | `/api/migrations/out/{id}` | 详情 |
| POST | `/api/migrations/out` | 新增迁出记录 |
| DELETE | `/api/migrations/out/{id}` | 删除迁出记录 |
| GET | `/api/migrations/out/statistics?startDate=&endDate=` | 迁出统计：总数 |

**MigrationOut 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `outId` | Long | 主键 |
| `personId` | Long | 迁出人员 ID |
| `fromHouseholdId` | Long | 原户籍 ID |
| `toAddress` | string | 迁出去向 |
| `outDate` | date | 迁出日期 |
| `reason` | string | 迁出原因 |
| `operatorId` | Long | 经办人 ID |
| `createdAt` | LocalDateTime | 创建时间 |
| `personName` | string | 冗余：人员姓名 |
| `personIdCard` | string | 冗余：身份证号 |
| `householdNo` | string | 冗余：原户户号 |
| `operatorName` | string | 冗余：经办人姓名 |

---

## 9. 流动人口管理（`/api/floating-population`）

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/floating-population?status=&current=&size=` | 分页查询 |
| GET | `/api/floating-population/{id}` | 详情 |
| POST | `/api/floating-population` | 新增 |
| PUT | `/api/floating-population/{id}` | 更新 |
| DELETE | `/api/floating-population/{id}` | 删除 |
| GET | `/api/floating-population/statistics` | 统计：`total` 总数 / `active` 有效数 |

**FloatingPopulation 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `floatingId` | Long | 主键 |
| `personId` | Long | 人员 ID |
| `sourcePlace` | string | 来源地 |
| `currentAddress` | string | 现居住地 |
| `registerDate` | date | 登记日期 |
| `status` | string | `有效` / `无效` |
| `remark` | string | 备注 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `personName` | string | 冗余：人员姓名 |
| `personIdCard` | string | 冗余：身份证号 |

---

## 10. 重点人口管理（`/api/key-population`）

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/key-population?keyType=&managementLevel=&status=&current=&size=` | 多条件分页 |
| GET | `/api/key-population/{id}` | 详情 |
| POST | `/api/key-population` | 新增 |
| PUT | `/api/key-population/{id}` | 更新 |
| DELETE | `/api/key-population/{id}` | 删除 |
| GET | `/api/key-population/types` | 获取重点类型字典 |
| GET | `/api/key-population/levels` | 获取管理级别字典 |
| GET | `/api/key-population/statistics` | 统计：`total / level1 / level2` |

**KeyPopulation 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `keyId` | Long | 主键 |
| `personId` | Long | 人员 ID |
| `keyType` | string | 类型：`刑满释放人员` / `社区矫正对象` / `吸毒人员` / `严重精神障碍患者` / `涉邪教人员` / `其他重点人员` |
| `managementLevel` | string | 管理级别：`一级` / `二级` / `三级` |
| `registerDate` | date | 登记日期 |
| `status` | string | 状态 |
| `remark` | string | 备注 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `personName` | string | 冗余：人员姓名 |
| `personIdCard` | string | 冗余：身份证号 |

---

## 11. 证件管理（`/api/certificates`）

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/certificates?certificateType=&status=&current=&size=` | 分页 |
| GET | `/api/certificates/{id}` | 详情 |
| POST | `/api/certificates` | 新增 |
| PUT | `/api/certificates/{id}` | 更新 |
| DELETE | `/api/certificates/{id}` | 删除 |
| GET | `/api/certificates/types` | 获取证件类型字典 |
| GET | `/api/certificates/expire-soon?days=30&current=&size=` | 查询 N 天内即将过期的证件（默认 30 天） |

**Certificate 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `certificateId` | Long | 主键 |
| `personId` | Long | 持证人 ID |
| `certificateType` | string | 证件类型 |
| `certificateNo` | string | 证件编号 |
| `issueDate` | date | 签发日期 |
| `expireDate` | date | 到期日期 |
| `status` | string | `有效` / `失效` / `已过期` |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `personName` | string | 冗余：持证人姓名 |
| `personIdCard` | string | 冗余：身份证号 |

证件类型字典：`身份证` / `户口簿` / `护照` / `港澳通行证` / `台湾通行证` / `驾驶证` / `社保卡` / `医保卡` / `其他`

> **重点**：`/api/certificates/expire-soon` 接口常用于 Dashboard 中"证件到期提醒"卡片，前端不需要自己算过期时间。

---

## 12. 数据字典（`/api/dictionary`）

为前端下拉框提供统一来源。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/dictionary/types` | 所有字典类型列表 |
| GET | `/api/dictionary/type/{type}` | 按类型取启用项（按 sortNo 升序） |
| GET | `/api/dictionary` | 字典全量列表 |
| GET | `/api/dictionary/{id}` | 字典详情 |
| POST | `/api/dictionary` | 新增字典项 |
| PUT | `/api/dictionary/{id}` | 更新字典项 |
| DELETE | `/api/dictionary/{id}` | 删除字典项 |
| GET | `/api/dictionary/grouped` | 获取按 `dictType` 分组的字典（一次性拿到所有类型） |

**DataDictionary 字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dictId` | Long | 主键 |
| `dictType` | string | 字典类型（分类标识） |
| `dictCode` | string | 字典编码（值） |
| `dictName` | string | 字典名称（显示文本） |
| `sortNo` | int | 排序号 |
| `status` | string | `启用` / `禁用` |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |

> 前端建议：在系统启动时调用 `/api/dictionary/grouped` 一次，将数据存到 Pinia / Vuex，按 `dictType` 取值。

---

## 13. 已知但暂未开放 Controller 的实体（开发参考）

这些实体已在数据库 / Mapper / Service 中建好，但**当前未提供独立 Controller 接口**，前端暂时不能直接调用，仅供未来扩展参考：

- `SysRole` / `SysPermission`：RBAC 权限骨架已建表，仅在登录回显 `roleName` 时使用，暂未提供管理接口
- `Residence`：居住登记表（人员 - 户籍 - 居住类型三元组），暂未开放接口
- `OperationLog`：操作日志写入方法 `OperationLogService.log() / logAsync()` 已实现，但**当前未在 Controller 中调用**，也未提供查询接口

> 如果后续产品要做"日志审计"页面，需补一个 `OperationLogController`。

---

## 14. 前端对接 Checklist

1. **登录后**立即把 `LoginVO` 写入 Pinia 的 `userStore`，并在 axios 请求拦截器里统一加 `Authorization: Bearer ${token}`
2. **统一错误处理**：在 axios 响应拦截器里判断 `code === 401` 直接跳登录页；`code !== 200` 弹 `message`
3. **时间格式化**：后端返回 `LocalDateTime` 是 `"2026-07-08T15:00:00"` 格式，前端用 `dayjs(...)` 处理；`LocalDate` 同理
4. **分页组件**：所有 `PageVO` 结构一致，写一个通用 `<Pagination>` 组件即可复用
5. **下拉数据**：尽量调用对应的 `types / levels / grouped` 接口，不要在前端硬编码枚举值
6. **统计数据**：Dashboard 卡片的数据源是各模块的 `/statistics` 接口（人员、迁入、迁出、流动人口、重点人口、证件）
7. **冗余字段**：户籍、人员档案、迁入迁出等列表里的 `xxxName / xxxIdCard` 都是后端已经联表查好的，前端**不要再请求 ID 解析名字**的接口
8. **删除操作**：户籍成员、迁入迁出都是**直接 DELETE**；户籍成员采用软删除（status 置"无效"），其余为硬删——前端建议保留 `ElMessageBox.confirm` 二次确认

---

## 15. 接口速查表（按 URL 排序）

| 模块 | 方法 | 路径 |
| --- | --- | --- |
| 认证 | POST | `/api/auth/login` |
| 认证 | POST | `/api/auth/register` |
| 系统 | GET | `/api/system/health` |
| 系统 | GET | `/api/system/info` |
| 用户 | GET | `/api/users` |
| 用户 | GET | `/api/users/list` |
| 用户 | GET | `/api/users/{id}` |
| 用户 | POST | `/api/users` |
| 用户 | PUT | `/api/users/{id}` |
| 用户 | DELETE | `/api/users/{id}` |
| 人员 | GET | `/api/persons` |
| 人员 | GET | `/api/persons/list` |
| 人员 | GET | `/api/persons/statistics` |
| 人员 | GET | `/api/persons/{id}` |
| 人员 | GET | `/api/persons/id-card/{idCard}` |
| 人员 | GET | `/api/persons/name/{name}` |
| 人员 | GET | `/api/persons/phone/{phone}` |
| 人员 | POST | `/api/persons` |
| 人员 | PUT | `/api/persons/{id}` |
| 人员 | DELETE | `/api/persons/{id}` |
| 户籍 | GET | `/api/households` |
| 户籍 | GET | `/api/households/list` |
| 户籍 | GET | `/api/households/{id}` |
| 户籍 | GET | `/api/households/{id}/members` |
| 户籍 | POST | `/api/households` |
| 户籍 | PUT | `/api/households/{id}` |
| 户籍 | DELETE | `/api/households/{id}` |
| 户籍 | POST | `/api/households/{id}/members` |
| 户籍 | DELETE | `/api/households/{householdId}/members/{memberId}` |
| 居民 | GET | `/api/residents` |
| 居民 | GET | `/api/residents/list` |
| 居民 | GET | `/api/residents/{id}` |
| 居民 | POST | `/api/residents` |
| 居民 | PUT | `/api/residents/{id}` |
| 居民 | DELETE | `/api/residents/{id}` |
| 迁入 | GET | `/api/migrations/in` |
| 迁入 | GET | `/api/migrations/in/statistics` |
| 迁入 | GET | `/api/migrations/in/{id}` |
| 迁入 | POST | `/api/migrations/in` |
| 迁入 | DELETE | `/api/migrations/in/{id}` |
| 迁出 | GET | `/api/migrations/out` |
| 迁出 | GET | `/api/migrations/out/statistics` |
| 迁出 | GET | `/api/migrations/out/{id}` |
| 迁出 | POST | `/api/migrations/out` |
| 迁出 | DELETE | `/api/migrations/out/{id}` |
| 流动人口 | GET | `/api/floating-population` |
| 流动人口 | GET | `/api/floating-population/statistics` |
| 流动人口 | GET | `/api/floating-population/{id}` |
| 流动人口 | POST | `/api/floating-population` |
| 流动人口 | PUT | `/api/floating-population/{id}` |
| 流动人口 | DELETE | `/api/floating-population/{id}` |
| 重点人口 | GET | `/api/key-population` |
| 重点人口 | GET | `/api/key-population/statistics` |
| 重点人口 | GET | `/api/key-population/types` |
| 重点人口 | GET | `/api/key-population/levels` |
| 重点人口 | GET | `/api/key-population/{id}` |
| 重点人口 | POST | `/api/key-population` |
| 重点人口 | PUT | `/api/key-population/{id}` |
| 重点人口 | DELETE | `/api/key-population/{id}` |
| 证件 | GET | `/api/certificates` |
| 证件 | GET | `/api/certificates/types` |
| 证件 | GET | `/api/certificates/expire-soon` |
| 证件 | GET | `/api/certificates/{id}` |
| 证件 | POST | `/api/certificates` |
| 证件 | PUT | `/api/certificates/{id}` |
| 证件 | DELETE | `/api/certificates/{id}` |
| 字典 | GET | `/api/dictionary` |
| 字典 | GET | `/api/dictionary/types` |
| 字典 | GET | `/api/dictionary/grouped` |
| 字典 | GET | `/api/dictionary/type/{type}` |
| 字典 | GET | `/api/dictionary/{id}` |
| 字典 | POST | `/api/dictionary` |
| 字典 | PUT | `/api/dictionary/{id}` |
| 字典 | DELETE | `/api/dictionary/{id}` |

---

> 文档基于当前后端代码（`src/main/java/com/example/population/controller/*.java`）自动梳理。
> 如果后端新增 / 修改接口，请同步更新本文档。