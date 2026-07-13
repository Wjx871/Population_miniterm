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
    ├── config/                          # 配置类（MyBatis-Plus 分页、Web 配置、Jackson、CORS 等）
    │   └── JacksonConfig                # Sprint 4 P0：注册 @Masked 序列化器
    ├── controller/                      # 控制器层（26 个 Controller）
    ├── aspect/                          # Spring AOP 切面
    │   └── DataScopeAspect              # Sprint 4 P0：数据范围切面（审计/调用跟踪）
    ├── annotation/                      # 自定义注解
    │   ├── DataScope                    # Sprint 4 P0：方法级标记，触发 @DataScopeAspect 审计
    ├── dto/                             # 数据传输对象（基础 + P0 白名单 + Sprint 4 P0 新增）
    │   ├── 通用：PageDTO / PageVO / Result / LoginDTO / RegisterDTO
    │   └── 业务：PersonCreateDTO（必带 applicationId）/ PersonUpdateDTO / PersonQueryDTO /
    │          HouseholdCreateDTO（必带 applicationId）/ HouseholdMemberDTO / HouseholdMemberTransferDTO /
    │          ResidenceRegisterDTO / MigrationInDTO / MigrationOutDTO / CancellationDTO
    │   └── P0 白名单（Sprint 3）：*UpdateDTO / *CreateDTO，用于阻断 Mass Assignment
    │          （SysUser / SysRole / SysDepartment / SysPermission / AdminRegion /
    │           HouseholdMember / ResidenceRegistration / BusinessApplication /
    │           ApplicationMaterial / Certificate / KeyPopulation / FloatingPopulation /
    │           ResidencePermit / DataDictionary）
    │   └── Sprint 4 P0：
    │          PersonVO（脱敏响应 VO，@Masked 标注）
    │          DataScopeQuery（数据范围过滤上下文）
    │          DataExportRequestDTO（高敏导出入参，含 expectedRows / sensitivity hints）
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
        # === Sprint 4 P0 新增 ===
        ├── DataScopeContext / DataScopeHelper            # 数据范围 ThreadLocal + Wrapper 应用
        ├── Masked / MaskedRule / MaskedSerializer        # 敏感字段脱敏（Jackson 注解 + 序列化器）
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

### 2. 数据库与密钥配置

`src/main/resources/application.yml` 已将敏感信息外部化为环境变量占位符。**禁止将真实密钥提交到 Git 或写入 YAML**。

#### 2.1 必填环境变量

| 变量 | 用途 | 备注 |
|------|------|------|
| `DB_PASSWORD` | MySQL 密码 | 必填，无默认；缺启动报错 |
| `JWT_SECRET`  | JWT 签名密钥 | 必填，HS256 ≥ 256 bit，建议 `openssl rand -base64 64` 生成 |
| `CORS_ALLOWED_ORIGINS` | 跨域白名单 | 逗号分隔；默认 `http://localhost:5173,http://localhost:8081` |
| `CORS_ALLOW_CREDENTIALS` | 是否带凭据 | 默认 `false`；生产建议保持关闭 |

#### 2.2 示例：本地开发（PowerShell）

```powershell
$env:DB_PASSWORD   = "your_strong_password"
$env:JWT_SECRET    = (openssl rand -base64 64)
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"
```

#### 2.3 application.yml 片段（节选）

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/${DB_NAME:population_miniterm}?...
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}    # 必须通过环境变量注入

jwt:
  secret: ${JWT_SECRET:}         # ≥ 256 bit
  expiration: ${JWT_EXPIRATION_MS:1800000}        # access token 30 min
  refresh-expiration: ${JWT_REFRESH_EXPIRATION_MS:604800000}  # refresh token 7d

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:8081}
  allow-credentials: ${CORS_ALLOW_CREDENTIALS:false}
```

### 4. 本地配置文件（可选）

如需将敏感配置保存到本地文件（替代环境变量），可创建 `src/main/resources/application-local.yml`：

```yaml
jwt:
  secret: your-256-bit-secret-here
```

> 该文件已加入 `.gitignore`，不会被提交到 Git。

启动时指定 `local` profile：

```powershell
mvn "spring-boot:run" "-Dspring-boot.run.profiles=local"
```

### 5. 初始化数据库

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

### 6. 运行项目

```bash
mvn spring-boot:run
```

启动成功后，服务监听 `http://localhost:8080`。

### 7. 访问 API 文档

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
| POST | `/api/auth/login` | 用户登录，返回 `{accessToken, refreshToken, expiresIn, tokenType:"Bearer"}` |
| POST | `/api/auth/refresh` | 用 refresh token 换发新 access token；**会从 DB 重新加载最新权限四元组**，解决改权限/角色后旧 token 仍生效的问题 |
| POST | `/api/auth/logout` | 登出（前端清 token；如需服务端吊销，可扩展为把当前 `jti` 写入 `TokenBlacklist`） |
| GET  | `/api/auth/me` | 获取当前登录用户 |

### 人口档案 `/api/persons`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/persons` | 分页查询（多条件）；敏感字段（姓名/身份证号/手机号/联系地址）默认脱敏返回 |
| GET | `/api/persons?unmask=true` | 仅 L3 用户可开启原文输出；非 L3 仍按脱敏处理 |
| GET | `/api/persons/{id}` | 单条查询，敏感字段同上 |
| GET | `/api/persons/identity?type=&no=` | 按证件类型+号码查询 |
| POST | `/api/persons` | 新增人口（L3 直通，L1/L2 走审批） |
| PUT | `/api/persons/{id}` | 更新人口 |
| DELETE | `/api/persons/{id}` | 删除人口（软删） |

> 敏感字段脱敏规则：身份证号 → `前6 **** 末4`；手机号 → `138****8000`；中文姓名 → `张*`；联系地址 → `北京市朝阳区****123号`。L3 管理员可通过 `?unmask=true` 临时查看原文，但仅在 `SecurityContext.permissionLevel >= 3` 且线程 ThreadLocal 标记 `MaskedSerializer.UNMASK=true` 时才生效。

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

#### 申请人 ≠ 审批人（Sprint 4 P0 / D-05）

`ApprovalGateServiceImpl#approve` 与 `reject` 入口先做 `applicant-approver` 分离校验：

```
if (business_application.submit_user_id == currentUserId) {
    throw new ForbiddenException("申请人不能审批自己的申请");
}
```

杜绝 L2 经办自审、自驳带来的越权风险。校验命中的同时会在 WARN 级别记录审计日志（`applicantUserId` / `approverUserId` / `approvalId`），便于事后追溯。

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
| * | `/api/data-export-logs` | 数据导出日志（分页查询） |
| POST | `/api/data-export-logs/submit` | **Sprint 4 P0**：高敏导出三级审批入口。系统按 `SensitivityEvaluator` 自动判定导出请求的敏感级别（L1/L2/L3），L1 直写 data_export_log；L2/L3 走 `ApprovalGateService.submit`，businessType=`SENSITIVE_EXPORT_L2` 或 `SENSITIVE_EXPORT_L3`，审批通过后由 `SensitiveExportService.landApprovedExport` 回写 exportId + `resultCode=APPROVED` |

#### 敏感度评估规则（Sprint 4 P0）

| 触发条件 | 最低级别 |
|----------|----------|
| `exportTypeCode ∈ {KEY_POPULATION, RAW_IDENTITY, RAW_CERT, OPERATION_LOG_FULL, EXPORT_HISTORY}` | **L3** |
| `containsSensitiveFields=true` 且 `exportTypeCode ∈ {PERSON, FLOATING, HOUSEHOLD, RESIDENCE_ARCHIVE, MIGRATION, CANCELLATION, PERMIT}` | **L3** |
| `expectedRows > 10000` | **L3** |
| `expectedRows > 1000` 或命中 L2 类型 | **L2** |
| 其它 | **L1**（直接落日志，无需审批） |

L2/L3 提交时 `exportReason` 必填，且 L2 → `requiredLevel=2`、L3 → `requiredLevel=3`（必须 L3 审批）。

#### 数据范围过滤（Sprint 4 P0）

`DataScopeAspect` + `@DataScope` 注解统一控制查询的数据范围（ALL / DEPARTMENT / REGION / SELF，对应 `sys_role.data_scope_code`）：

| Service 方法 | 实体类型 | 注入逻辑 |
|--------------|----------|----------|
| `PersonServiceImpl.queryPage / listByIdsWithScope` | PERSON | EXISTS 子查询关联 `residence_registration + household` 过滤部门/区划 |
| `HouseholdServiceImpl.page` | HOUSEHOLD | 直接按 `department_id` / `region_code` 过滤 |
| `FloatingPopulationServiceImpl.page` | MIGRATION | 按 `handling_department_id` / `current_region_code` 过滤 |

脱敏场景：`JWT claims` 中新增 `deptId`，由 `JwtAuthInterceptor` 写入 ThreadLocal `DataScopeContext`，Service 通过 `DataScopeQuery.fromCurrentContext()` 拿。**禁止前端传入任意 `departmentId` 或 `regionCode` 绕过权限**。

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

- 除 `/api/auth/login` 外，访问其他接口需在请求头携带：`Authorization: Bearer <accessToken>`
- **Access token** 默认 30 分钟过期；**Refresh token** 默认 7 天过期，只能用于 `/api/auth/refresh`，不能直接访问业务接口
- 每个 token 携带 `jti`（UUID）和 `type`（`access` / `refresh`）双字段
- `JwtAuthInterceptor` 会校验 token 类型、签名、有效期，并查询 `TokenBlacklist`（Redis）拒绝已吊销的 `jti`
- 用户的权限四元组 (`userId / roleCode / permCodes / dataScope`) 会在签发 access token 时写入 claims；调用 `/api/auth/refresh` 时**从 DB 重新加载最新值**，确保权限变更立即生效
- 未通过鉴权时由 `GlobalExceptionHandler` 统一返回错误码
- 业务级权限（细粒度到 `module:action`）由 `@RequiresPermission` 切面强制，详见《数据库设计v4.0_Cursor详细说明.md》权限章节

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
| `DataScopeContextTest` | Sprint 4 P0：`ThreadLocal → DataScopeQuery` 转换（ALL / DEPARTMENT / REGION / SELF / 无上下文 5 用例） |
| `MaskedSerializerTest` | Sprint 4 P0：`@Masked` 序列化 + `UNMASK` ThreadLocal + 原文返回（3 用例） |
| `SensitivityEvaluatorTest` | Sprint 4 P0：L1/L2/L3 边界（按类型/行数/containsSensitiveFields，6 用例） |
| `ApprovalGateServiceApproveTest` | Sprint 4 P0 新增 2 用例：`approve` / `reject` 的申请人=审批人自审阻断 |

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

### 2026-07-13 — Sprint 4：横向 P0 安全加固

基于深度代码评审落地，聚焦 **数据范围 / 敏感字段 / 越权阻断 / 高敏导出**。完整提交：`942f181 feat(security): implement P0 hardening - data scope, masking, self-review block, sensitive export approval`；30 文件 +1559/-29。**修复编号 = 《数据库设计 v4.0》§6 矩阵条目**。

#### 1. 数据范围过滤（D-04）

| 项 | 改动 |
|---|---|
| `DataScopeContext`（新增）| ThreadLocal holder，封装 `dataScopeCode / userId / departmentId / departmentRegionCode / visibleRegionCodes`；`JwtAuthInterceptor` 写入、`afterCompletion` 清理，杜绝线程复用串号 |
| `JwtUtil` | access token claims 追加 `deptId`（sys_user.department_id），拒绝每次请求再查 DB |
| `JwtAuthInterceptor` | 从 token 读 `deptId` → 注入 SecurityContext + DataScopeContext；通过 SysDepartmentMapper 反查 region_code；REGION 范围递归拉取 `AdminRegion.listChildren` 构造可见区划集合 |
| `DataScopeAspect`（新增）| `@Around("@annotation(@DataScope)")`，仅审计（uid / scope / method） |
| `DataScopeQuery`（新增）| 静态工厂 `fromCurrentContext()`，按 ALL/DEPARTMENT/REGION/SELF 映射字段 |
| `DataScopeHelper`（新增）| 按实体类型应用过滤：Person 用 `EXISTS` 子查询关联 residence_registration + household；Household 直接 `department_id` / `region_code` 等值 |
| `PersonServiceImpl.queryPage / listByIdsWithScope` / `HouseholdServiceImpl.page` / `FloatingPopulationServiceImpl.page` | 方法级 `@DataScope` + 显式 `applyPersonScope / applyHouseholdScope / applyBusinessScope` |

**关键安全语义**：禁止前端在 DTO 里直接传 `departmentId` / `regionCode` 绕过权限；Service 内部从 ThreadLocal 拿强制范围与前端查询做 AND 合并。

#### 2. 敏感字段统一脱敏（D-04 续）

| 项 | 改动 |
|---|---|
| `@Masked` 注解 + `MaskedRule` 枚举（新增）| 标注规则：`ID_CARD` / `PHONE` / `NAME` / `ADDRESS` / `FULL` |
| `MaskedSerializer`（新增）| `StdScalarSerializer<Object>` + `ContextualSerializer`；线程 ThreadLocal `UNMASK` 标志位可临时跳过脱敏 |
| `JacksonConfig`（新增）| `BeanSerializerModifier.changeProperties` 替换带 `@Masked` 字段的 `BeanPropertyWriter`，子类化后强制注入脱敏 serializer 到 `_serializer` |
| `PersonVO`（新增）| 响应 VO，`identityNo / phone / name / contactAddress` 全部 @Masked |
| `PersonController.page / get` | 改用 `PersonVO.from(entity)`；支持 `?unmask=true` 但 **仅 L3 (permissionLevel ≥ 3)** 才生效，非 L3 仍按脱敏处理 |

#### 3. 申请人 ≠ 审批人 自审阻断（D-05）

| 项 | 改动 |
|---|---|
| `ApprovalGateServiceImpl.approve` | 入口加 1 行：`business_application.submit_user_id == currentUserId` → `ForbiddenException("申请人不能审批自己的申请")`；命中同时 WARN 级别审计 |
| `ApprovalGateServiceImpl.reject` | 同上，Apply/Approve 共用同一规则 |

#### 4. 高敏导出三级审批链路（D-07）

| 项 | 改动 |
|---|---|
| `SensitivityEvaluator`（新增接口）| `evaluate(req) → 1/2/3`；提供 `requiresApproval / requiresL3` 默认方法 |
| `DefaultSensitivityEvaluator`（新增）| L3 触发：KEY_POPULATION / RAW_IDENTITY / RAW_CERT / OPERATION_LOG_FULL / EXPORT_HISTORY；L2 触发：PERSON/FLOATING/HOUSEHOLD/RESIDENCE_ARCHIVE 等中敏表；行数 `>10000` → L3，`>1000` → L2；中敏表含 `containsSensitiveFields=true` → 直接升 L3 |
| `DataExportRequestDTO`（新增）| 入参含 `exportTypeCode / expectedRows / containsSensitiveFields / exportReason` 等 |
| `DataExportLogController.submit`（改造）| L1 直写日志；L2/L3 走 `ApprovalGateService.submit(businessType=SENSITIVE_EXPORT_L2/L3)`，L2 requiredLevel=2、L3 requiredLevel=3，强制 `exportReason` 非空 |
| `SensitiveExportService` + 实现（新增）| `executeDirect`（L1）/ `landApprovedExport`（L2/L3 审批通过后回调落 data_export_log + resultCode=APPROVED） |
| `ApprovalGateServiceImpl.dispatchLanding` | 新增 `SENSITIVE_EXPORT_L2 / SENSITIVE_EXPORT_L3` switch 分支，调用 `SensitiveExportService.landApprovedExport` 落地 exportId |

#### 测试

`mvn test`：**263 个测试，0 失败，0 错误**（Sprint 3 的 247 个 + Sprint 4 新增 16 个）。新增单测：

- `DataScopeContextTest` —— ThreadLocal → DataScopeQuery 转换：ALL/DEPARTMENT/REGION/SELF/无上下文 5 用例
- `MaskedSerializerTest` —— `@Masked` 默认脱敏 + `UNMASK` ThreadLocal + L3 行为验证 3 用例
- `SensitivityEvaluatorTest` —— L1/L2/L3 边界：类型/行数/containsSensitiveFields 6 用例
- `ApprovalGateServiceApproveTest` —— 追加 2 用例（`approve_selfReviewForbidden` / `reject_selfReviewForbidden`）

#### 部署注意事项

- 无 DB schema 变更，纯代码层改动
- 旧 token 因为没有 `deptId` claim 仍可继续使用（Sprint 4 在 interceptor 兼容 `deptId = null`，DataScopeContext 退化为 deptId 默认值）
- 旧 `RedisPermissionCache` 持有的权限缓存仍可继续读；但建议下一次重启后强刷一次，让所有用户重新登录以应用 `data_scope_code` 变更
- 如要完全禁止 L1 角色不带 `deptId` 也能走 REGION 范围查询，可在 `JwtAuthInterceptor` 加上 "token 缺 deptId 即视为旧 token 强制重登" 策略
- 旧的 `/api/data-export-logs` POST 接口仍然可用（仅写日志），但**新流程必须走 `/api/data-export-logs/submit`**

### 2026-07-12 — Sprint 3：P0 安全修复（第二批）

基于深度代码评审落地的 P0 安全修复，覆盖**凭证安全 / 鉴权 / 数据完整性**三大类。
**分支：`household-migration`；提交：`78d2081 fix(security): Sprint 3 — close all P0 issues`；60 文件 +2553/-196。**

#### 凭证与密钥（修复 1/2）

| 项 | 改动 |
|---|---|
| `application.yml` | 数据库密码 / JWT 密钥改为 `${DB_PASSWORD}` / `${JWT_SECRET}` 占位符；强制通过环境变量注入，删除硬编码 `Zhao123@` 与 `population-miniterm-secret-key-2024-...` |
| `JwtUtil.init()` | 启动期校验密钥长度 ≥ 32 byte（HS256 最低），未配置或过短直接 fail-fast |
| `JwtUtil` | 缩短 access token 到 30 min（1800000 ms），引入 7 天 refresh token；每个 token 含 `jti`（UUID） + `type`（access / refresh）双字段 |
| `TokenBlacklist`（新增） | 基于 Redis 的 jti 黑名单，支持主动吊销单个 access token |
| `AuthController.refresh` | 新增 `/api/auth/refresh` 端点：拿 refresh token 换发新 access token，**重新从 DB 加载最新权限四元组**，解决改权限/角色后旧 token 仍生效的问题 |
| `JwtAuthInterceptor` | 强制 access token 类型校验；每次请求校验 jti 未被吊销 |

#### 密码哈希（修复 2）

| 项 | 改动 |
|---|---|
| `pom.xml` | 新增 `spring-security-crypto` 依赖（仅用 BCryptPasswordEncoder，不启用完整 Spring Security 过滤器链） |
| `PasswordEncoder` | 主算法替换为 **BCrypt（cost = 12）**；老 `sha256$<hex>$<base64salt>` 格式向前兼容（`matches` 自动识别）；`SysUserServiceImpl#login` 在登录成功后**自动升级**老 hash 为 BCrypt |
| 老算法 | 显式 `getBytes(StandardCharsets.UTF_8)`（不再用平台默认字符集）；常量时间比较避免时序攻击 |

#### CORS 误配（修复 3）

| 项 | 改动 |
|---|---|
| `application.yml` | 新增 `cors.allowed-origins` 与 `cors.allow-credentials` 配置项，默认从 `CORS_ALLOWED_ORIGINS` / `CORS_ALLOW_CREDENTIALS` 环境变量注入 |
| `CorsConfig` | 拒绝通配符 `*` + `allowCredentials=true` 组合（教科书级 CORS 误配）；配置含 `*` 时自动关闭凭据并 WARN；空配置时直接拒绝所有跨域请求 |

#### 权限缓存失效（修复 5）

| 项 | 改动 |
|---|---|
| `SysRolePermissionServiceImpl.assignPermissions` | 改完权限后查 `sys_user` 找出该角色的所有 userId，循环 `permissionCache.evict(uid)` |
| `SysUserServiceImpl.updateRole / disableUser / resetPassword` | 角色变更 / 停用 / 重置密码后立即 `permissionCache.evict(userId)` |

#### 审批草稿注入（修复 6）

| 项 | 改动 |
|---|---|
| `sql/schema.sql`（sys_approval_request） | 新增 `business_type VARCHAR(50)` / `business_id BIGINT` / `payload_json JSON` 三列；`payload_json` 用 JSON 类型（结构化、可索引），`apply_reason` 仅承载用户自由文本 |
| `sql/migration_20260712_p0_approval_struct.sql`（新增） | 存量数据回填：把旧 `apply_reason` 中的 `[BT=...][PID=...][APPID=...][REASON=...]xxx` 回填到新独立列（幂等可重跑） |
| `SysApprovalRequest` 实体 | 新增 `businessType / businessId / payloadJson` 字段 |
| `ApprovalGateServiceImpl` | 移除 `buildApplyReason / parseApplyReason / extractTag` 字符串拼接逻辑；新提交写独立列；解析时优先独立列，旧格式仅做兜底（迁移期兼容）。**消除用户输入含 `[BT=PERSON_UPDATE]` 触发的草稿劫持漏洞** |

#### Mass Assignment 白名单（修复 7）

12+ Controller 的 `update(@RequestBody X entity)` 全部改造为 `update(@RequestBody XUpdateDTO dto)` + `BeanUtils.copyProperties` 模式；新增以下 DTO：

| DTO | 字段白名单 |
|---|---|
| `SysUserUpdateDTO` | realName / phone / roleId / departmentId / status；passwordHash / username / lastLoginAt / isDeleted 不可改 |
| `SysRoleUpdateDTO` / `SysRoleCreateDTO` | roleName / permissionLevel(1-3) / dataScopeCode / description / status；roleCode 不可改 |
| `SysDepartmentUpdateDTO` / `SysDepartmentCreateDTO` | departmentName / departmentTypeCode / regionCode / parentId / status |
| `SysPermissionUpdateDTO` / `SysPermissionCreateDTO` | permissionName / moduleName / actionCode / sensitivityLevel(0-3) / approvalRequired(0/1) |
| `AdminRegionUpdateDTO` / `AdminRegionCreateDTO` | regionName / levelCode / parentCode / enabled |
| `HouseholdMemberUpdateDTO` | relationshipCode / joinDate / memberStatus；迁出请走 `/leave` |
| `ResidenceRegistrationUpdateDTO` | registerTypeCode / registerDate / registeredAddress / regionCode / startDate |
| `BusinessApplicationUpdateDTO` / `BusinessApplicationCreateDTO` | businessTypeCode / applicantName / applicantIdentityType / applicantIdentityNo / applicantPhone / targetPersonId / targetHouseholdId / handlingDepartmentId / status / currentStep |
| `ApplicationMaterialUpdateDTO` / `ApplicationMaterialCreateDTO` | materialTypeCode / materialName / materialNo / fileName / storageUri / fileHash / requiredFlag |
| `CertificateUpdateDTO` / `CertificateCreateDTO` | certificateTypeCode / certificateNo / issueAuthority / issueDate / validFrom / validUntil / certificateStatus |
| `KeyPopulationUpdateDTO` / `KeyPopulationCreateDTO` | keyTypeCode / managementLevelCode / registerDate / manageStartDate / manageEndDate / sourceBasisSummary / responsibleDepartmentId / responsibleUserId / status / remark |
| `FloatingPopulationUpdateDTO` / `FloatingPopulationCreateDTO` | sourceRegionCode / sourceAddress / currentRegionCode / currentAddress / arrivalDate / registerDate / plannedLeaveDate / actualLeaveDate / residenceReasonCode / employmentSchool / landlordName / landlordPhone / status |
| `ResidencePermitUpdateDTO` / `ResidencePermitCreateDTO` | permitTypeCode / permitNo / issueAuthority / issueDate / validFrom / validUntil / permitStatus / cancelDate |
| `DataDictionaryUpdateDTO` / `DataDictionaryCreateDTO` | dictLabel / sortNo / status / remark |

#### 业务工作流旁路阻断（修复 8）

| 端点 | 阻断方式 |
|---|---|
| `DELETE /api/sys-users/{id}` | 抛 405，提示走 `PUT /api/sys-users/{id}/disable` 业务流；新增 `/disable` 与 `/role` 端点 |
| `DELETE /api/household-members/{id}` | 抛 405，提示走 `/leave` |
| `DELETE /api/residence-registrations/{id}` | 抛 405，提示走迁出/注销流程 |
| `POST /api/households`（旧兼容） | 抛 405，统一走 `/establish` 业务流（含材料闸门） |

#### 测试

`mvn test`：**247 个测试，0 失败，0 错误**（Sprint 2 的 232 个 + Sprint 3 新增的 15 个）。新增单测：
- `PasswordEncoderTest` —— BCrypt 主算法 + 老 SHA-256 兼容 + 异常输入降级（5 用例）
- `JwtUtilTest` —— 追加 access/refresh 双 token 类型 + jti UUID 格式 + refresh TTL（3 用例）
- `JwtAuthInterceptorTest` —— 追加 refresh token 拒绝访问 API + jti 黑名单吊销（3 用例）
- `ApprovalGateServiceStructuredFieldsTest` —— submit 写独立列 + 攻击注入不可劫持 + 旧格式兜底兼容（4 用例）

#### 部署注意事项

- 必须设置 `JWT_SECRET`（≥ 256 bit，建议 `openssl rand -base64 64` 生成）
- 必须设置 `DB_PASSWORD`（真实数据库密码，禁止再用 `Zhao123@`）
- 必须设置 `CORS_ALLOWED_ORIGINS`（生产环境前端域名，逗号分隔）
- 数据库需要先跑迁移脚本 `sql/migration_20260712_p0_approval_struct.sql` 再启动应用
- 旧 SHA-256 密码哈希会在用户下次成功登录后自动升级为 BCrypt；不需要批量脚本迁移

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