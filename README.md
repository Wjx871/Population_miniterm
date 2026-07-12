# Population System Spring Boot Backend

## 项目简介

这是一个基于 **Spring Boot 3 + MyBatis-Plus** 的人口管理信息系统后端服务，为前端 Vue.js 应用提供 RESTful API 接口。
系统覆盖人口档案、户籍管理、迁入迁出、居住证/居住登记、流动人口、重点人口、证件管理、业务申请审批、RBAC 权限、操作日志、数据字典等完整的基层人口业务场景。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.5 | 核心框架（JDK 17+） |
| MyBatis-Plus | 3.5.6 | ORM 框架，启用逻辑删除 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 6.0+ | 缓存（可选，未启用时不阻塞启动） |
| JWT (jjwt) | - | 无状态身份认证 |
| Knife4j | 4.x | Swagger UI 增强的 API 文档 |
| Lombok | - | 简化样板代码 |
| Maven | 3.8+ | 构建工具 |

## 项目结构

```
Population_System/
├── pom.xml                              # Maven 配置（Spring Boot 3.2.5 / JDK 17）
├── README.md                            # 项目说明（本文件）
├── scripts/
│   └── count-lines.ps1                  # 统计 src/main/java 下各包代码行数
├── docs/db/                             # 数据库设计与核心业务文档（见文末"相关文档"）
├── sql/                                 # 数据库脚本（DDL / 数据 / 字典 / 视图 / 自检）
│   ├── schema.sql                       # 主 DDL（25 张表 + 索引）
│   ├── init.sql                         # 数据字典初始化
│   ├── data.sql                         # 测试数据
│   ├── views.sql                        # 视图
│   ├── dictionary_readme.sql            # 字典说明
│   ├── sanity_check_*.sql               # 一致性自检
│   └── test_*.sql                       # 业务自检 / 事务边界测试
└── src/main/java/com/example/population/
    ├── PopulationApplication.java       # 启动类
    ├── config/                          # 配置类（MyBatis-Plus 分页、Web 配置等）
    ├── controller/                      # 控制器层（26 个 Controller）
    ├── dto/                             # 数据传输对象（16 个）
    │   ├── 通用：PageDTO / PageVO / Result / LoginDTO / RegisterDTO
    │   └── 业务：PersonCreateDTO（必带 applicationId）/ PersonUpdateDTO / PersonQueryDTO /
    │          HouseholdCreateDTO（必带 applicationId）/ HouseholdMemberDTO / HouseholdMemberTransferDTO /
    │          ResidenceRegisterDTO / MigrationInDTO / MigrationOutDTO / CancellationDTO
    ├── entity/                          # 数据库实体（25 个，与表一一对应）
    ├── exception/                       # 全局异常处理
    │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice 统一处理
    │   └── BizException / NotFoundException / DuplicateException /
    │       IdCardInvalidException / PhoneInvalidException /
    │       HouseholdNotEmptyException / HouseholdHasOutstandingApplicationException /
    │       PersonAlreadyHasRegistrationException
    ├── interceptor/                     # JWT 鉴权拦截器
    ├── mapper/                          # MyBatis-Plus Mapper 接口（25 个）
    ├── service/                         # 业务接口
    │   └── impl/                        # 业务实现（25 个 ServiceImpl）
    └── util/                            # 工具类
        ├── JwtUtil / PasswordEncoder / PageUtil          # 通用
        └── IdCardValidator / PhoneValidator / IdentityMasker /
            SnapshotCopier                   # 业务
├── src/main/resources/
│   ├── application.yml                  # 主配置（数据源 / Redis / JWT / MyBatis-Plus / Knife4j）
│   └── mapper/                          # 自定义 XML 映射（如有）
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+（可选）

### 2. 数据库配置

修改 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/population_miniterm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: your_password
```

### 3. 初始化数据库

`sql/` 目录下脚本分工如下：

| 脚本 | 作用 |
|------|------|
| `schema.sql` | DDL，建表 + 索引（含 `is_deleted` 逻辑删除字段） |
| `init.sql` | 数据字典初始化（39 类字典，幂等可重复执行） |
| `data.sql` | 测试数据 |
| `views.sql` | 视图 |
| `dictionary_readme.sql` | 字典说明 |
| `sanity_check_*.sql` | 一致性自检（户籍-归档、户主一致性等） |
| `test_*.sql` | 业务自检 / 事务边界测试 |

依次执行（按依赖顺序）：

```bash
mysql -u root -p population_miniterm < sql/schema.sql
mysql -u root -p population_miniterm < sql/init.sql
mysql -u root -p population_miniterm < sql/data.sql
mysql -u root -p population_miniterm < sql/views.sql
```

> 旧版教程中提到的 `population_miniterm.sql` 已按职责拆分为上述多文件，请使用 `schema.sql` 作为 DDL 入口。

### 4. 运行项目

```bash
mvn spring-boot:run
```

启动成功后，服务监听 `http://localhost:8080`。

### 5. 访问 API 文档

启动后访问 Knife4j 文档：

```
http://localhost:8080/doc.html
```

## 核心功能模块

| 模块 | 说明 |
|------|------|
| 认证模块 | 用户登录 / 登出 / 获取当前用户信息（JWT） |
| 人口档案 | 人员基本信息 CRUD，按证件号查询，分页多条件检索 |
| 户籍管理 | 户籍主表与户籍成员管理 |
| 居住管理 | 居住登记、居住证、居住档案 |
| 迁入迁出 | 迁入记录、迁出记录 |
| 特殊人口 | 流动人口、重点人口 |
| 证件管理 | 各类证件（身份证、居住证等）的发放与维护 |
| 业务申请 | 业务申请单（BusinessApplication）与申请材料（ApplicationMaterial） |
| 审批流 | 审批请求（SysApprovalRequest）+ 审批日志（SysApprovalLog）+ 注销记录（CancellationRecord） |
| 系统管理 | 用户、角色、权限、角色-权限关联、部门、行政区划 |
| 运维支撑 | 操作日志、数据字典、数据导出日志 |

## API 接口

所有接口统一以 `/api` 为前缀，统一返回结构 `Result<T>`。

### 认证 `/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录，返回 token |
| POST | `/api/auth/logout` | 登出（前端清 token 即可） |
| GET  | `/api/auth/me` | 获取当前登录用户 |

### 人口档案 `/api/persons`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/persons` | 分页查询（多条件） |
| GET | `/api/persons/{id}` | 根据 ID 查询 |
| GET | `/api/persons/identity?type=&no=` | 按证件类型+号码查询 |
| POST | `/api/persons` | 新增人口 |
| PUT | `/api/persons/{id}` | 更新人口 |
| DELETE | `/api/persons/{id}` | 删除人口 |

### 户籍管理 `/api/households`、`/api/household-members`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/households` | 分页查询户籍 |
| GET | `/api/households/{id}` | 户籍详情 |
| POST | `/api/households` | 新增户籍 |
| PUT | `/api/households/{id}` | 更新户籍 |
| DELETE | `/api/households/{id}` | 删除户籍 |
| GET/POST/PUT/DELETE | `/api/household-members` | 户籍成员 CRUD |

### 居住管理

| 方法 | 路径 | 说明 |
|------|------|------|
| * | `/api/residence-registrations` | 居住登记 CRUD |
| * | `/api/residence-permits` | 居住证 CRUD |
| * | `/api/residence-archives` | 居住档案 CRUD |

### 迁入/迁出

| 方法 | 路径 | 说明 |
|------|------|------|
| * | `/api/migration-in` | 迁入记录 CRUD |
| * | `/api/migration-out` | 迁出记录 CRUD |

### 特殊人口

| 方法 | 路径 | 说明 |
|------|------|------|
| * | `/api/floating-population` | 流动人口 CRUD |
| * | `/api/key-population` | 重点人口 CRUD |
| * | `/api/certificates` | 证件管理 CRUD |

### 业务申请与材料

业务申请与材料是"必交闸门"的入口，新增人口 / 立户 / 迁入迁出 都必须先建业务申请、上传材料并核验通过，再发起业务。

#### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET    | `/api/business-applications/{id}` | 业务申请详情 |
| POST   | `/api/business-applications` | 创建业务申请草稿，返回 `applicationId` |
| PUT    | `/api/business-applications/{id}/submit` | 提交申请（DRAFT → SUBMITTED） |
| GET    | `/api/application-materials?applicationId=` | 列出某申请下的全部材料 |
| POST   | `/api/application-materials` | 上传一份材料（`materialTypeCode`、`requiredFlag` 等见 `MATERIAL_TYPE` 字典） |
| PUT    | `/api/application-materials/{id}/verify?verifierId=&passed=` | L1/L2 核验：通过则 `verify_status=VERIFIED`，驳回则 `REJECTED` |
| DELETE | `/api/application-materials/{id}` | 撤回某份材料（仅在 UNVERIFIED 时允许） |

#### 业务必交材料闸门（最低必交规则）

`ApplicationMaterialService#assertRequiredVerified(applicationId, businessType)` 是统一闸门，被 `PersonServiceImpl#createPerson`、`HouseholdServiceImpl#establishHousehold` 与 `ApprovalGateServiceImpl#approve` 在事务内调用。规则与《数据库设计v4.0_Cursor详细说明.md》§7 对齐：

| 业务类型 (`businessType`) | AND 必交 | OR 备选组（任一即可） |
|--------------------------|----------|---------------------|
| `PERSON_REGISTER` / `PERSON_CREATE` | `IDENTITY_DOC` | — |
| `HOUSEHOLD_ESTABLISH` / `HOUSEHOLD_CREATE` | `IDENTITY_DOC` | `HOUSEHOLD_BOOKLET` / `RESIDENCE_PROOF` |
| `MIGRATION_IN_*` / `MIGRATION_OUT_*` | `IDENTITY_DOC`、`MIGRATION_CERT` | — |
| 其它业务 | 直接放行 | — |

校验要求：每份必交材料必须 `required_flag=1` 且 `verify_status=VERIFIED`。任一缺失或处于 `UNVERIFIED` / `REJECTED` 状态时，调用方收到 `code=400` 的 `BizException`，消息形如：

```
业务[HOUSEHOLD_ESTABLISH]缺少最低必交材料且未核验通过：身份证明（materialTypeCode=IDENTITY_DOC）。
请先通过业务申请 ID=200 提交并完成核验。
```

#### 标准调用顺序（L2 经办 → L3 审批 → 落库）

1. `POST /api/business-applications` → 拿到 `applicationId`
2. `POST /api/application-materials`（按业务必交清单上传材料）→ 拿到 `materialId`
3. `PUT /api/application-materials/{id}/verify?passed=true` → `verify_status=VERIFIED`
4. `POST /api/persons` 或 `POST /api/households/establish`，请求体里**必带** `applicationId`；闸门通过后落库
5. L3 审批通过（`/api/sys-approval-requests/{approvalId}/approve`）时再次调用同一闸门，未通过则驳回

### 审批流

| 方法 | 路径 | 说明 |
|------|------|------|
| * | `/api/sys-approval-requests` | 审批请求 CRUD |
| * | `/api/sys-approval-logs` | 审批日志 |
| * | `/api/cancellation-records` | 注销记录 |

### 系统管理（RBAC + 组织架构）

| 方法 | 路径 | 说明 |
|------|------|------|
| * | `/api/sys-users` | 系统用户 CRUD |
| * | `/api/sys-roles` | 角色 CRUD |
| * | `/api/sys-permissions` | 权限（菜单/按钮）CRUD |
| * | `/api/sys-role-permissions` | 角色-权限关联 |
| * | `/api/sys-departments` | 部门 CRUD |
| * | `/api/admin-regions` | 行政区划 CRUD |

### 运维支撑

| 方法 | 路径 | 说明 |
|------|------|------|
| * | `/api/operation-logs` | 操作日志 |
| * | `/api/data-dictionaries` | 数据字典 |
| * | `/api/data-export-logs` | 数据导出日志 |

> 以上 `*` 表示各 Controller 提供完整的 `GET`（分页/详情）、`POST`、`PUT`、`DELETE` 能力，详细字段请查阅 Knife4j 文档。

## 统一响应结构

```json
{
  "code": 200,
  "message": "ok",
  "data": { ... }
}
```

- `code`：`200` 成功，业务异常使用约定编码
- `data`：返回的业务对象，分页场景为 `PageVO<T>`，包含 `records / total / page / size`

## 鉴权说明

- 除 `/api/auth/login` 外，访问其他接口需在请求头携带：`Authorization: Bearer <token>`
- Token 由 `JwtAuthInterceptor` 校验，签发密钥与有效期见 `application.yml` 的 `jwt.*`
- 未通过鉴权时由 `GlobalExceptionHandler` 统一返回错误码

## 数据库表

| 表名 | 说明 |
|------|------|
| `sys_user` | 系统用户 |
| `sys_role` | 角色 |
| `sys_permission` | 权限（菜单/按钮） |
| `sys_role_permission` | 角色-权限关联 |
| `sys_department` | 部门 |
| `admin_region` | 行政区划 |
| `person` | 人口档案 |
| `household` | 户籍主表 |
| `household_member` | 户籍成员 |
| `residence_registration` | 居住登记 |
| `residence_permit` | 居住证 |
| `residence_archive` | 居住档案 |
| `migration_in` | 迁入记录 |
| `migration_out` | 迁出记录 |
| `floating_population` | 流动人口 |
| `key_population` | 重点人口 |
| `certificate` | 证件信息 |
| `business_application` | 业务申请单 |
| `application_material` | 申请材料（与 `application_material` 同名表），关键字段：`material_type_code`（`MATERIAL_TYPE` 字典）、`required_flag`（0/1，闸门只校验 `=1`）、`verify_status`（`UNVERIFIED`/`VERIFIED`/`REJECTED`）、`file_hash`（完整性/去重） |
| `sys_approval_request` | 审批请求 |
| `sys_approval_log` | 审批日志 |
| `cancellation_record` | 注销记录 |
| `operation_log` | 操作日志 |
| `data_dictionary` | 数据字典 |
| `data_export_log` | 数据导出日志 |

> 表 `is_deleted` 字段启用 MyBatis-Plus 逻辑删除（`0` 有效，`1` 删除）。

## 测试

`src/test/java/com/example/population/` 下以单测为主（Mockito + ReflectionTestUtils 注入 MyBatis-Plus `baseMapper`，无需真实数据库）。

```bash
# 跑全部测试
mvn test

# 跑材料必交闸门相关测试
mvn -q test -Dtest='ApplicationMaterialGateTest,PersonServiceTest,HouseholdServiceTest,SimpleCrudServiceTest'

# 仅编译生产代码（不跑测试）
mvn -q -DskipTests compile

# 编译测试代码但不跑
mvn -q -DskipTests test-compile
```

| 测试类 | 覆盖点 |
|--------|--------|
| `ApplicationMaterialGateTest` | `assertRequiredVerified`：PERSON/HOUSEHOLD/MIGRATION 的 AND/OR 必交；UNVERIFIED/REJECTED/未传 applicationId/未知业务类型 等 11 个场景 |
| `PersonServiceTest` | `createPerson` 失败链：身份证格式、手机号格式、重复身份证号、唯一键冲突兜底、材料闸门 |
| `HouseholdServiceTest` | `establishHousehold`：户号冲突、户主自动建关系、户主不存在、闸门 |
| `SimpleCrudServiceTest` | `ResidencePermit.cancel` / `KeyPopulation.release` / `BusinessApplication.submit+getDetail` / `ApplicationMaterial.verify` |
| `PermissionSmokeTest` | JWT 签发+解析全字段、`PermissionCache` 读写（依赖 Redis，缺失时跳过） |

## 辅助脚本

```powershell
# 统计 src/main/java 各包代码行数（PowerShell）
powershell -ExecutionPolicy Bypass -File scripts/count-lines.ps1
```

输出包括总文件数、总行数与按 `controller / service / mapper / entity / dto / util / exception / interceptor / config` 等包维度的行数分布，可用于阶段性进度评估。

## 相关文档

`docs/db/` 目录下提供设计与核心业务说明：

- `赵书亚_工作任务清单.md` —— 数据库 + 户籍核心业务任务拆分与边界
- `核心设计说明_户籍与归档.md` —— 当前户籍与历史归档物理分离、事务边界
- `索引策略.md` —— 9 张主负责表的索引设计与适用查询
- `外键级联策略表.md` —— 外键 / 级联策略汇总
- `E-R图_v4.0.md` —— 实体关系图
- `数据字典_v4.0.md` —— 字典全集与业务含义
- `测试用例_事务边界.md` —— 迁入 / 迁出 / 注销等事务边界用例

## 更新日志

### 2026-07-12 — Sprint 2：写入路径加固 & 防御性闸门

基于深度代码评审的 P0/P1 修复落地，聚焦 **数据一致性 + 权限正确性 + DoS 防护**。完整提交：`f944612 refactor(core): harden write paths and add LIKE/size guards`。

#### 写入路径白名单（修复 Mass Assignment）

| Controller | 改动 |
|---|---|
| `PUT /api/households/{id}` | 新增 `HouseholdUpdateDTO`，仅暴露 `householdTypeCode / headPersonId / registeredAddress / regionCode / departmentId / status`；走 `HouseholdService.updateHousehold`，户号等不可变字段不再可被外部覆盖 |
| `PUT /api/persons/{id}` | 维持既有 `PersonUpdateDTO` 白名单（已在 Sprint 1 引入） |

#### 禁止绕过业务流的 CRUD（HTTP 405）

下列端点原本直接调用 `IService.updateById / removeById`，可绕过业务校验破坏不变量。现统一返回 `405` 并提示走业务流：

| 端点 | 替代业务流 |
|---|---|
| `DELETE /api/households/{id}` | `PUT /api/households/{id}/disable` |
| `PUT /api/migration-out/{id}`、`DELETE /api/migration-out/{id}` | `POST /api/migration-out`（新建）/ `PUT /api/migration-out/{id}/complete`（办结）/ 审批驳回 |
| `PUT /api/migration-in/{id}`、`DELETE /api/migration-in/{id}` | 同上 |
| `PUT /api/cancellation-records/{id}`、`DELETE /api/cancellation-records/{id}` | `/complete-person`、`/complete-household` |

#### 并发一致性（行锁 & 唯一约束）

| Service | 修复 |
|---|---|
| `CancellationRecordServiceImpl.completeHouseholdCancellation` | 新增 `HouseholdMapper.selectByIdForUpdate`，事务入口先锁户档案行；两 L3 并发销户现在串行化 |
| `PersonServiceImpl.createPerson` | 由 `findByIdentity`（非锁）改为 `findByIdentityForUpdate`，杜绝 SELECT-then-INSERT 竞态 |
| `SysApprovalRequestServiceImpl.approve / reject` | 新增 `SysApprovalRequestMapper.selectByIdForUpdate` + `SysApprovalLogMapper.selectMaxStepNo`；并发审批用 `MAX(step_no)+1` 替代 `COUNT()+1`，配合 `schema.sql` 已有的 `uk_approval_step` 唯一约束，杜绝重复步骤号 |
| `MigrationInServiceImpl.complete` | `householdMemberMapper.insert(hm)` 包 try/catch `DuplicateKeyException` 并翻译为 `BizException(409)`，避免并发场景的 500 |

#### 权限切面强化

| 端点 | 修复 |
|---|---|
| `POST /api/cancellation-records` | `@RequiresPermission(value = {"cancellation:person", "cancellation:household"}, all = true)`，从 OR 改为 AND 语义 |
| `POST /api/approval-gate/approve/{id}` / `/reject/{id}` | 新增 `@RequiresPermission("approval:approve")`，L3 不再能跨业务域审批 |

#### DoS 防护

| 工具类 | 应用范围 |
|---|---|
| `util.SafeLike`（新增） | 转义 `% _ \`，长度上限 64；11 个 Service 的 page 查询 `keyword` 入参全部接入（`Person` / `Household` / `MigrationOut` / `MigrationIn` / `KeyPopulation` / `FloatingPopulation` / `BusinessApplication` / `LoginLog` / `SysUser` / `SysDepartment` / `AdminRegion`）。`keyword=%` 不再触发全表扫描 |
| `PageUtil.clamp`（强化） | 钳制 `size ∈ [1, 200]`；17 个 Service 的 page 方法入口统一接入（`Person` / `Household` / `MigrationOut` / `MigrationIn` / `CancellationRecord` / `KeyPopulation` / `FloatingPopulation` / `BusinessApplication` / `Certificate` / `ResidencePermit` / `ResidenceArchive` / `LoginLog` / `OperationLog` / `DataExportLog` / `SysUser` / `SysDepartment` / `AdminRegion`） |

#### 鉴权响应修正

`JwtAuthInterceptor.writeUnauthorized` 改用 `ObjectMapper.writeValueAsString(Result.error(...))` 序列化 401 响应体，避免字符串拼接导致的 JSON 转义漏洞；同时记录 `ip / user-agent / reason`，便于审计暴力破解。

#### 测试

`mvn test`：**232 个测试，0 失败，0 错误**。涉及更新的单测：
- `JwtAuthInterceptorTest` —— 适配 `ObjectMapper` 注入
- `PersonServiceTest` —— 适配 `findByIdentityForUpdate`
- `CancellationRecordServiceTest` —— 适配 `householdMapper.selectByIdForUpdate`

#### 部署注意事项

`@RequiresPermission("approval:approve")` 是新增权限码，需要在 `sys_permission` 表种入并分配给 L3 角色，否则现有 L3 用户将无法审批：
```sql
INSERT INTO sys_permission (permission_code, permission_name, module_code, enabled_flag)
VALUES ('approval:approve', '审批通过/驳回', 'APPROVAL', 1);
-- 再插入到 sys_role_permission 关联到 L3_APPROVE 角色
```

### 2026-07-12 — Sprint 1：P0 安全修复（已合并）

详见提交 `b85b6c9 fix(core): resolve P0 concurrency bug, dead code and HTTP status mapping` 与后续测试提交 `f1f7687 test(core): cover P0 fixes for migration-out lock and exception handler`。

## License

MIT