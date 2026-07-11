# M1 阶段：后台框架与三级权限展示执行方案

> 项目：人口数据库管理系统  
> 仓库：`Wjx871/Population_miniterm`  
> 执行分支：`feat/frontend-population`  
> 执行对象：代码 Agent  
> 前置条件：M0 已验收通过  
> 阶段定位：建立前端三级权限展示与后台导航框架，不新增业务模块

---

## 1. 阶段目标

在现有前端页面基础上，建立统一、可复用的三级权限展示机制，使系统达到：

```text
登录身份可规范化
→ 页面刷新后角色信息可恢复
→ 菜单按角色显示
→ 路由禁止越权访问
→ 操作按钮按权限显示
→ 403/404 有明确反馈
→ 登录后可返回原目标页面
```

M1 只负责**前端可见性与导航控制**。真正的接口鉴权、数据范围校验仍由后端负责，前端隐藏按钮不能替代后端安全校验。

---

## 2. 当前基线

当前代码已具备：

- 登录接口与用户 Store；
- Token 本地保存；
- 登录状态路由守卫；
- 后台主布局；
- 工作台及现有业务页面；
- M0 完成后的统一请求层和分页工具。

当前需要解决的问题：

1. `userStore` 仅保存 `roleName`，没有统一角色代码、权限等级和权限集合；
2. 路由只判断“是否登录”，不判断角色和页面权限；
3. 侧边栏菜单全部硬编码，所有登录用户看到相同菜单；
4. 页面新增、编辑、删除等按钮没有统一权限控制；
5. `MainLayout.vue` 使用 `roleName === 'admin'` 判断管理员，兼容性差；
6. 未定义 403 和 404 页面，未知路由直接返回首页；
7. 请求层未单独处理 403；
8. 登录成功后固定跳转 `/home`，无法返回登录前目标页面。

---

## 3. 三级角色定义

前端内部统一使用以下角色代码：

```js
NORMAL_USER       // 普通用户，一级权限
HOUSEHOLD_ADMIN   // 户口管理员，二级权限
SUPER_ADMIN       // 超级管理员，三级权限
```

| 权限等级 | 角色 | 前端能力 |
|---:|---|---|
| 1 | 普通用户 | 查询、查看详情，不能新增、修改、删除和审批 |
| 2 | 户口管理员 | 查询及常规维护，可发起业务办理；重大操作暂不直接开放 |
| 3 | 超级管理员 | 查看全部菜单和操作，进入系统管理，拥有全部前端权限 |

### 3.1 兼容当前后端返回

M1 不要求修改后端登录接口。角色归一化规则集中实现，不允许散落在页面中。

角色代码优先级：

```text
loginVO.roleCode
→ loginVO.roleName 映射
→ 无法识别时降级为 NORMAL_USER
```

兼容映射至少包括：

| 后端值 | 前端角色代码 |
|---|---|
| `SUPER_ADMIN`、`ROLE_SUPER_ADMIN`、`admin`、`系统管理员`、`超级管理员` | `SUPER_ADMIN` |
| `HOUSEHOLD_ADMIN`、`ROLE_HOUSEHOLD_ADMIN`、`户口管理员`、`户籍管理员` | `HOUSEHOLD_ADMIN` |
| `NORMAL_USER`、`ROLE_NORMAL_USER`、`USER`、`普通用户`、`查询用户` | `NORMAL_USER` |

未知角色必须按最低权限处理，不得默认管理员。

---

## 4. 本阶段边界

### 4.1 允许修改

```text
frontend/src/constants/
frontend/src/utils/permission.js
frontend/src/directives/
frontend/src/stores/user.js
frontend/src/router/index.js
frontend/src/api/request.js
frontend/src/main.js
frontend/src/layouts/MainLayout.vue
frontend/src/views/Login.vue
frontend/src/views/error/
现有业务页面中与按钮权限显示有关的模板代码
```

### 4.2 禁止修改

- 不修改 Spring Boot 后端；
- 不修改数据库、初始化数据或创建测试账号；
- 不实现后端角色、权限表管理；
- 不实现审批中心和申请流程；
- 不新增人口、户籍、迁移等业务功能；
- 不重写登录页视觉；
- 不改变现有业务接口路径；
- 不改变现有按钮实际业务行为；
- 不拆分整个布局为大量新组件；
- 不引入新的权限框架或第三方依赖；
- 不实现动态路由下发；
- 不将“前端隐藏按钮”描述为安全鉴权完成。

现有直接删除、撤销等业务逻辑，本阶段仅限制其可见角色；后续由 M2/M3 按业务流程改造。

---

# 5. 权限常量设计

## M1-01 新增角色常量

新增：

```text
frontend/src/constants/roles.js
```

建议导出：

```js
ROLE_CODE
ROLE_LEVEL
ROLE_LABEL
ROLE_BADGE_TYPE
```

示例结构：

```js
export const ROLE_CODE = Object.freeze({
  NORMAL_USER: 'NORMAL_USER',
  HOUSEHOLD_ADMIN: 'HOUSEHOLD_ADMIN',
  SUPER_ADMIN: 'SUPER_ADMIN',
})

export const ROLE_LEVEL = Object.freeze({
  [ROLE_CODE.NORMAL_USER]: 1,
  [ROLE_CODE.HOUSEHOLD_ADMIN]: 2,
  [ROLE_CODE.SUPER_ADMIN]: 3,
})
```

要求：

- 全项目不再直接使用中文角色名判断业务权限；
- 不再出现 `roleName === 'admin'`；
- 中文角色名仅用于显示。

---

## M1-02 新增权限常量与默认矩阵

新增：

```text
frontend/src/constants/permissions.js
```

建议权限码：

```text
dashboard:view
person:view
person:create
person:update
person:delete
household:view
household:create
household:update
household:member:manage
household:delete
migration:view
migration:apply
migration:delete
floating:view
floating:manage
key:view
key:apply
key:manage
certificate:view
certificate:manage
certificate:delete
user:view
user:manage
dictionary:view
dictionary:manage
```

默认矩阵：

### 普通用户

```text
dashboard:view
person:view
household:view
migration:view
floating:view
certificate:view
```

### 户口管理员

包含普通用户全部权限，并增加：

```text
person:create
person:update
household:create
household:update
household:member:manage
migration:apply
floating:manage
key:view
key:apply
certificate:manage
```

### 超级管理员

```text
*
```

`*` 表示拥有全部前端权限。

### 后端权限数组兼容

若登录响应明确包含 `permissions` 数组，则以该数组为准；若字段不存在，才根据角色使用前端默认矩阵。

注意区分：

```js
Array.isArray(loginVO.permissions) // 后端明确返回，包括空数组
```

空数组表示没有权限，不得再次回退为角色默认权限。

---

# 6. 用户 Store 重构

## M1-03 扩展 `stores/user.js`

新增状态：

```text
roleCode
permissionLevel
permissions
```

保留状态：

```text
accessToken
tokenType
userId
username
realName
roleName
```

新增或调整 Getter：

```text
isLoggedIn
displayName
roleLabel
isSuperAdmin
```

新增方法：

```text
normalizeRoleCode(roleCode, roleName)
setLoginInfo(loginVO)
hasLevel(minLevel)
hasPermission(permission)
hasAnyPermission(permissions)
canAccess(meta)
logout()
```

### `setLoginInfo` 要求

1. 优先使用 `loginVO.roleCode`；
2. 兼容 `loginVO.roleName`；
3. 计算 `permissionLevel`；
4. 解析后端权限数组或角色默认权限；
5. 将新字段写入 `population_user`；
6. 不保存密码；
7. 不信任无法识别的高权限字符串。

### 本地恢复要求

页面刷新后，从 `population_user` 恢复并再次执行角色归一化，不能直接相信旧的 `permissionLevel`。

原因：旧版本本地数据可能只有 `roleName`，或保存了过期字段。

### `logout` 要求

同时清空：

```text
roleCode
permissionLevel
permissions
```

并删除 `population_user`。

---

# 7. 路由权限

## M1-04 为现有路由增加权限元数据

修改：

```text
frontend/src/router/index.js
```

统一路由元信息：

```js
meta: {
  title: '人口信息管理',
  requiresAuth: true,
  minLevel: 1,
  permission: 'person:view',
  menu: true,
  group: '人口户籍',
  order: 10,
  icon: 'User',
}
```

详情路由示例：

```js
meta: {
  title: '户籍详情',
  requiresAuth: true,
  minLevel: 1,
  permission: 'household:view',
  menu: false,
  activeMenu: '/households',
}
```

### 路由访问矩阵

| 路由 | 最低等级 | 权限码 | 菜单分组 |
|---|---:|---|---|
| `/home` | 1 | `dashboard:view` | 工作台 |
| `/persons` | 1 | `person:view` | 人口户籍 |
| `/households` | 1 | `household:view` | 人口户籍 |
| `/households/:id` | 1 | `household:view` | 隐藏详情路由 |
| `/migrations/in` | 1 | `migration:view` | 业务办理 |
| `/migrations/out` | 1 | `migration:view` | 业务办理 |
| `/floating-population` | 1 | `floating:view` | 扩展业务 |
| `/key-population` | 2 | `key:view` | 扩展业务 |
| `/certificates` | 1 | `certificate:view` | 扩展业务 |
| `/users` | 3 | `user:view` | 系统管理 |
| `/dictionary` | 3 | `dictionary:view` | 系统管理 |

本阶段不新增当前户籍、历史归档、我的申请、审批中心等后续页面。

---

## M1-05 重写全局路由守卫

守卫按以下顺序执行：

```text
设置页面标题
→ 判断是否需要登录
→ 未登录跳转 /login?redirect=原地址
→ 已登录访问 /login 时跳转 /home
→ 校验 minLevel
→ 校验 permission
→ 无权限跳转 /403
→ 正常放行
```

要求：

- 使用 `return` 或统一 `next` 风格，避免重复导航；
- `/403`、`/404` 不得形成重定向循环；
- 无权限时保留来源地址，便于返回；
- 页面标题统一拼接“人口数据库管理系统”；
- 不根据菜单是否可见判断路由权限，直接访问也必须校验。

建议无权限跳转：

```js
{
  path: '/403',
  query: { from: to.fullPath },
  replace: true,
}
```

---

# 8. 403、404 页面

## M1-06 新增错误页面

新增：

```text
frontend/src/views/error/Forbidden.vue
frontend/src/views/error/NotFound.vue
```

### 403 页面

内容：

- “无权访问”；
- 简短原因说明；
- 返回工作台；
- 返回上一页。

### 404 页面

内容：

- “页面不存在”；
- 返回工作台或登录页；
- 返回上一页。

路由要求：

```text
/403 -> Forbidden.vue
/404 -> NotFound.vue
/:pathMatch(.*)* -> /404
```

不得继续将所有未知路径静默重定向到 `/home`。

---

# 9. 请求层 403 处理

## M1-07 修改 `api/request.js`

在保持 M0 请求层结构不变的前提下，增加：

- 业务响应 `code === 403`；
- HTTP 状态码 `403`。

处理规则：

```text
提示“无权执行该操作”
→ 跳转 /403
→ 保留原错误 Promise
```

要求：

- 401 仍清空登录状态并跳登录页；
- 403 不清除登录状态；
- 当前已经在 `/403` 时不得重复跳转；
- 404 接口提示与页面 404 路由互不混淆；
- 不大规模重写请求拦截器。

---

# 10. 按钮权限指令

## M1-08 新增 `v-permission`

新增：

```text
frontend/src/directives/permission.js
```

在：

```text
frontend/src/main.js
```

全局注册：

```js
app.directive('permission', permissionDirective)
```

支持：

```vue
<el-button v-permission="'person:create'">新增人口</el-button>
```

以及权限数组：

```vue
<el-button v-permission="['person:update', 'person:delete']">
  操作
</el-button>
```

数组默认按“任一权限满足”处理。

### 指令要求

- 超级管理员通配符 `*` 直接通过；
- 无权限时移除元素；
- 参数无效时按无权限处理；
- 不在每个页面重复读取 `roleName`；
- 不新增第二套 `v-role` 指令；
- 角色固定于登录会话，本阶段不要求运行时切换后恢复已移除节点。

---

# 11. 现有页面按钮接入

## M1-09 为现有操作按钮添加权限码

只调整按钮显示，不改变按钮事件和接口。

### 人口列表

文件：

```text
frontend/src/views/persons/PersonList.vue
```

| 操作 | 权限码 |
|---|---|
| 查询、重置、详情 | 无需额外按钮权限，路由可访问即可 |
| 新增 | `person:create` |
| 编辑 | `person:update` |
| 删除 | `person:delete` |

### 户籍列表

文件：

```text
frontend/src/views/households/HouseholdList.vue
```

| 操作 | 权限码 |
|---|---|
| 新增/立户 | `household:create` |
| 编辑 | `household:update` |
| 撤销/删除 | `household:delete` |

### 户籍详情

文件：

```text
frontend/src/views/households/HouseholdDetail.vue
```

成员添加、移出、关系维护等操作统一使用：

```text
household:member:manage
```

详情查看不隐藏。

### 迁入迁出

文件：

```text
frontend/src/views/migrations/MigrationList.vue
```

| 操作 | 权限码 |
|---|---|
| 办理/登记入口 | `migration:apply` |
| 删除/撤销 | `migration:delete` |

二级户口管理员可看到办理入口，但看不到直接删除或撤销入口。

### 证件管理

文件：

```text
frontend/src/views/certificates/CertificateList.vue
```

| 操作 | 权限码 |
|---|---|
| 新增、编辑 | `certificate:manage` |
| 删除、作废 | `certificate:delete` |

### 用户管理

文件：

```text
frontend/src/views/users/UserList.vue
```

用户新增、修改、删除等管理按钮统一使用：

```text
user:manage
```

即使用户管理路由仅超级管理员可访问，按钮仍应添加权限指令，形成页面内第二层防误显示。

### 注意

- 不给纯查询按钮增加多余指令；
- 不改按钮文字；
- 不将“删除”改成审批流程，本阶段仅隐藏；
- 不因权限隐藏破坏操作列布局。

---

# 12. 动态菜单与面包屑

## M1-10 重构 `MainLayout.vue`

保留现有整体政务蓝视觉，不拆成多个布局组件。

### 菜单生成

使用 `router.getRoutes()` 获取 `meta.menu === true` 的路由，并按以下条件过滤：

```text
userStore.hasLevel(meta.minLevel)
userStore.hasPermission(meta.permission)
```

按 `meta.group` 分组，按 `meta.order` 排序。

建议结构：

```text
工作台

人口户籍
├─ 人口信息管理
└─ 户籍管理

业务办理
├─ 迁入管理
└─ 迁出管理

扩展业务
├─ 流动人口管理
├─ 重点人口管理
└─ 证件管理

系统管理
├─ 用户管理
└─ 数据字典
```

要求：

- 工作台可保持一级菜单；
- 其他分组可使用 `el-sub-menu`；
- 普通用户不显示重点人口和系统管理；
- 户口管理员显示重点人口，但不显示系统管理；
- 超级管理员显示全部菜单；
- 详情页使用 `activeMenu` 保持父菜单高亮；
- 不在模板中硬编码角色判断。

### 图标

路由 `meta.icon` 使用字符串，由布局内统一映射到 Element Plus 图标。

不得将图标组件实例直接持久化或写入 Store。

### 角色显示

顶部角色标签改为：

```text
userStore.roleLabel
```

不同角色可使用不同标签类型，但不改变顶部整体布局。

### 面包屑

在主内容区顶部增加简洁面包屑：

```text
工作台
人口户籍 / 人口信息管理
人口户籍 / 户籍管理 / 户籍详情
```

要求：

- 根据当前路由元信息生成；
- 首页不重复显示多级面包屑；
- 不把“人口数据库管理系统”作为每一级面包屑；
- 不改变业务页面原有标题。

---

# 13. 登录后返回原页面

## M1-11 修改 `Login.vue`

路由守卫将未登录用户重定向至：

```text
/login?redirect=/原目标路径
```

登录成功后：

```text
优先跳转 redirect
否则跳转 /home
```

要求：

- 使用 `router.replace`；
- 仅接受以 `/` 开头的站内路径；
- 不允许重定向回 `/login`；
- 不接受完整外部 URL；
- 不修改登录页视觉和表单交互。

---

# 14. 建议执行顺序

```text
执行前检查
→ M1-01 角色常量
→ M1-02 权限常量和默认矩阵
→ M1-03 用户 Store
→ M1-04 路由元数据
→ M1-05 路由守卫
→ M1-06 403/404
→ M1-07 请求层 403
→ M1-08 权限指令
→ M1-09 页面按钮接入
→ M1-10 动态菜单与面包屑
→ M1-11 登录重定向
→ 构建与三级角色验收
→ 输出执行报告
```

不要先改页面按钮、后补用户 Store，以免中途出现大量未定义权限判断。

---

# 15. 预期文件变更

## 新增

```text
frontend/src/constants/roles.js
frontend/src/constants/permissions.js
frontend/src/utils/permission.js
frontend/src/directives/permission.js
frontend/src/views/error/Forbidden.vue
frontend/src/views/error/NotFound.vue
```

`utils/permission.js` 用于集中实现角色归一化、默认权限解析等纯函数；不得与 Store 形成循环依赖。

## 修改

```text
frontend/src/stores/user.js
frontend/src/router/index.js
frontend/src/api/request.js
frontend/src/main.js
frontend/src/layouts/MainLayout.vue
frontend/src/views/Login.vue
frontend/src/views/persons/PersonList.vue
frontend/src/views/households/HouseholdList.vue
frontend/src/views/households/HouseholdDetail.vue
frontend/src/views/migrations/MigrationList.vue
frontend/src/views/certificates/CertificateList.vue
frontend/src/views/users/UserList.vue
```

## 不应删除

M1 原则上不删除现有业务页面、API 文件和资源。

---

# 16. 验证方案

## 16.1 自动检查

在 `frontend/` 执行：

```bash
npm run build
```

要求：

- 构建成功；
- 无权限模块导入错误；
- 无循环依赖导致的运行时初始化错误；
- 不因 M1 新增依赖。

若项目已有 lint/test 命令则执行；没有则不得擅自引入完整测试框架。

---

## 16.2 未登录测试

| 场景 | 预期 |
|---|---|
| 访问 `/login` | 正常显示登录页 |
| 访问 `/persons` | 跳转 `/login?redirect=/persons` |
| 登录成功 | 返回 `/persons` |
| 访问未知路径 | 显示 404 页面 |

如无可用账号，只验证重定向参数和登录页加载；登录成功回跳留待已有账号时验证。

---

## 16.3 三级角色静态验收

代码 Agent 不得创建后端账号、修改数据库或提交角色切换器。

若暂时没有三类真实账号，可使用浏览器开发者工具临时写入 `population_user` 验证 UI。该数据不得写入源码或提交。

示例：

```js
localStorage.setItem('population_user', JSON.stringify({
  accessToken: 'M1_PREVIEW_ONLY',
  tokenType: 'Bearer',
  userId: -1,
  username: 'preview',
  realName: '权限预览',
  roleCode: 'NORMAL_USER',
  roleName: '普通用户'
}))
location.reload()
```

分别替换为：

```text
NORMAL_USER
HOUSEHOLD_ADMIN
SUPER_ADMIN
```

建议在后端未启动时测试菜单，避免模拟 Token 被后端 401 清除。测试结束后执行：

```js
localStorage.removeItem('population_user')
location.reload()
```

### 普通用户

- 可见工作台、人口、户籍、迁移、流动人口、证件；
- 不可见重点人口、用户管理、数据字典；
- 看不到新增、编辑、删除、办理按钮；
- 直接访问 `/users` 跳转 403。

### 户口管理员

- 可见普通用户菜单；
- 可见重点人口；
- 不可见用户管理和数据字典；
- 可见新增、编辑、成员维护、迁移办理等常规按钮；
- 看不到直接删除、撤销等高风险按钮；
- 直接访问 `/users` 跳转 403。

### 超级管理员

- 可见全部菜单；
- 可见全部现有操作按钮；
- 可进入用户管理和数据字典；
- 顶部正确显示“超级管理员”。

---

## 16.4 错误页与请求测试

| 场景 | 预期 |
|---|---|
| 访问不存在的前端路径 | 404 页面 |
| 前端路由权限不足 | 403 页面，不清除登录状态 |
| 后端返回业务码 403 | 提示无权限并进入 403 |
| 后端返回 HTTP 403 | 提示无权限并进入 403 |
| 后端返回 401 | 清除登录状态并进入登录页 |
| 退出登录 | Token、角色和权限全部清空 |

---

# 17. 完成标准

M1 只有同时满足以下条件才算完成：

1. 角色代码、等级和权限码集中定义；
2. 旧 `roleName` 可以映射，未知角色降级为普通用户；
3. 用户 Store 可持久化并恢复三级权限信息；
4. 路由同时校验登录、等级和权限码；
5. 菜单由路由元数据生成并按权限过滤；
6. 现有操作按钮接入统一 `v-permission`；
7. 普通用户、户口管理员、超级管理员表现符合矩阵；
8. 直接输入无权限地址会进入 403；
9. 未知地址会进入 404；
10. 403 不清除登录状态，401 会清除；
11. 登录后能够安全返回原目标页面；
12. 顶部不再使用 `roleName === 'admin'`；
13. 未修改后端、数据库和业务接口；
14. 未新增审批等后续页面；
15. `npm run build` 通过；
16. 提供文件级执行报告。

---

# 18. Agent 最终输出格式

执行完成后必须按以下格式回复：

```markdown
## M1 执行结果

### 1. 完成情况
- [x] M1-01 角色常量
- [x] M1-02 权限矩阵
- [ ] M1-xx 未完成项及原因

### 2. 新增文件
- `路径`：用途

### 3. 修改文件
- `路径`：修改内容

### 4. 角色矩阵验证
- 普通用户：通过/部分通过/未执行
- 户口管理员：通过/部分通过/未执行
- 超级管理员：通过/部分通过/未执行
- 验证方式：真实账号/临时本地角色数据

### 5. 路由验证
- 未登录重定向：
- 403：
- 404：
- 登录回跳：

### 6. 构建结果
- `npm run build`：通过/失败
- 现有警告：
- 新增错误：

### 7. 未解决问题
- 仅列出超出 M1 范围或依赖后端的问题

### 8. Git 状态
- 当前分支：
- 起始 commit：
- 当前 commit：
- `git status --short`：
```

不得只回复“已完成”。

---

# 19. 建议提交信息

人工验收通过后，建议提交：

```text
feat(frontend): 完成 M1 三级权限与后台导航框架
```

只允许提交到：

```text
feat/frontend-population
```

不得自动合并到 `develop` 或 `main`。
