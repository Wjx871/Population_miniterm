# M0 阶段：现有前端整理与基线稳定执行方案

> 项目：人口数据库管理系统  
> 仓库：`Wjx871/Population_miniterm`  
> 执行分支：`feat/frontend-population`  
> 执行对象：代码 Agent  
> 阶段定位：前端正式扩建前的工程清理，不新增业务功能  
> 对应里程碑：M0「现有前端整理与基线稳定」

---

## 1. 阶段目标

在不改变现有页面业务行为和视觉设计的前提下，完成前端工程清理和基础规范收口，使项目达到以下状态：

```text
目录边界明确
-> 请求层唯一
-> 分页结构统一
-> 无效引用和模板文件清理
-> 样式变量无明显错误
-> 开发环境配置清晰
-> 前端能够稳定启动和构建
```

M0 完成后，项目才进入 M1「后台框架与三级权限展示」。

---

## 2. 当前基线

当前前端位于：

```text
frontend/
```

现有技术栈：

```text
Vue 3
Vite
Element Plus
Vue Router
Pinia
Axios
Day.js
ECharts
```

当前已存在：

- 登录页；
- 后台主布局；
- 工作台；
- 人口管理；
- 家庭户及成员管理；
- 迁入迁出管理；
- 证件管理；
- 用户管理；
- 基础请求封装、分页、查询面板和状态标签。

已确认的 M0 问题：

1. `frontend/src/api/person.js` 和 `frontend/src/api/persons.js` 重复；
2. `person.js` 自行创建 Axios，并从错误位置读取 Token；
3. `router/index.js` 导入了未使用的 `Home.vue`；
4. 多个列表页对分页结果使用不同兼容写法；
5. 页面中存在未定义或命名不一致的 CSS 变量；
6. `frontend/README.md` 仍是 Vite 默认模板；
7. 可能存在未使用的示例组件和默认资源。

---

## 3. 执行边界

### 3.1 本阶段允许修改

```text
frontend/src/api/
frontend/src/router/
frontend/src/utils/
frontend/src/views/ 中仅与分页解析和无效引用有关的代码
frontend/src/style.css
frontend/README.md
frontend/.env.example
frontend/vite.config.js（仅在确有配置问题时）
frontend/.gitignore（仅在确有忽略项问题时）
```

### 3.2 本阶段禁止修改

- 不重设计登录页；
- 不修改后台菜单结构；
- 不实现三级权限；
- 不新增 403、404 页面；
- 不新增业务页面；
- 不重构人口、家庭户、迁入迁出业务流程；
- 不修改接口路径和后端字段含义；
- 不修改 Spring Boot 后端代码；
- 不修改数据库脚本；
- 不升级依赖版本；
- 不把 JavaScript 全量迁移为 TypeScript；
- 不进行与 M0 无关的大规模目录重构。

发现超出 M0 的问题，只记录到执行报告，不顺手扩展施工。

---

## 4. 执行前检查

Agent 开始修改前必须执行：

```bash
git status --short
git branch --show-current
git log -1 --oneline
```

要求：

1. 当前分支必须是 `feat/frontend-population`；
2. 如存在用户未提交的修改，不得覆盖或回滚；
3. 记录开始时的 commit SHA；
4. 进入 `frontend/` 后执行一次基线构建。

推荐命令：

```bash
cd frontend
npm install
npm run build
```

如果基线构建失败：

- 先记录原始错误；
- 仅修复属于 M0 范围的问题；
- 不为了通过构建删除业务代码；
- 无法安全判断时停止并报告。

---

# 5. 任务清单

## M0-01 确认前端目录边界

### 目标

保证所有前端源码和配置位于 `frontend/` 内。

### 操作

1. 检查仓库根目录是否存在误放的前端源码或配置；
2. 重点检查：
   ```text
   package.json
   vite.config.*
   src/*.vue
   src/api/
   src/router/
   ```
3. 不移动后端资源和项目启动脚本；
4. 如根目录存在重复前端文件，先判断是否被使用；
5. 只有确认是误提交的重复文件时才清理。

### 验收

- 前端源码仅在 `frontend/` 内；
- 不误删后端或项目级文件；
- 执行报告中写明检查结果。

---

## M0-02 合并重复人口 API

### 目标

全项目只保留统一请求封装。

### 保留文件

```text
frontend/src/api/persons.js
frontend/src/api/request.js
```

### 待删除文件

```text
frontend/src/api/person.js
```

### 操作步骤

1. 全局搜索对 `api/person.js` 的引用；
2. 如果存在引用，将其迁移到 `api/persons.js`；
3. 保证所需方法已经由 `persons.js` 提供：
   ```text
   getPersonPage
   getPersonList
   getPersonById
   createPerson
   updatePerson
   deletePerson
   getPersonStatistics
   ```
4. 删除 `person.js`；
5. 再次全局搜索，确保：
   - 不再直接 `import axios from 'axios'` 创建业务请求实例；
   - 不再读取 `localStorage.getItem('token')`；
   - 所有业务 API 均复用 `api/request.js`。

### 验收

```text
person.js 不存在
persons.js 正常被引用
业务 API 不再自行创建 Axios
人口页面功能不变
```

---

## M0-03 清理无效导入和模板文件

### 目标

清理确定未使用的初始化模板代码，不影响现有页面。

### 必做

删除 `router/index.js` 中未使用的：

```js
import Home from '../views/Home.vue'
```

### 候选清理文件

```text
frontend/src/views/Home.vue
frontend/src/components/HelloWorld.vue
frontend/src/assets/vite.svg
frontend/src/assets/vue.svg
frontend/src/assets/hero.png
```

### 操作规则

1. 对每个候选文件执行全局引用搜索；
2. 只有“无任何实际引用”时才删除；
3. 登录背景、徽章、视频、图标资源不得删除；
4. 不删除仍被页面、CSS 或 HTML 引用的文件；
5. 在执行报告中列出：
   - 已删除文件；
   - 因存在引用而保留的文件。

### 验收

- 路由文件无无效 `Home` 导入；
- 无明显 Vue 模板残留；
- 构建不存在资源缺失错误。

---

## M0-04 统一分页结果处理

### 目标

现有列表页统一使用后端已约定的分页结构：

```json
{
  "records": [],
  "total": 0,
  "pages": 0,
  "current": 1,
  "size": 10
}
```

查询参数统一为：

```text
current
size
```

### 涉及页面

```text
views/persons/PersonList.vue
views/households/HouseholdList.vue
views/migrations/MigrationList.vue
views/certificates/CertificateList.vue
views/users/UserList.vue
```

### 推荐实现

新增：

```text
frontend/src/utils/page.js
```

建议内容：

```js
export function normalizePageResult(data) {
  return {
    records: Array.isArray(data?.records) ? data.records : [],
    total: Number(data?.total ?? 0),
    pages: Number(data?.pages ?? 0),
    current: Number(data?.current ?? 1),
    size: Number(data?.size ?? 10),
  }
}
```

各列表页统一改为：

```js
const page = normalizePageResult(res)
tableData.value = page.records
total.value = page.total
```

### 禁止

不得继续新增：

```js
res.records || res.content || []
res.total || res.totalElements || 0
```

也不得在 M0 中改动后端分页协议。

### 验收

- 五个列表页分页解析方式一致；
- 查询参数仍使用 `current` 和 `size`；
- 空数据时返回空数组和 0；
- 页面切页和修改每页数量正常。

---

## M0-05 修复样式变量问题

### 目标

保证页面使用的全局 CSS 变量都有定义，命名一致。

### 当前标准变量

```text
--color-accent
--color-accent-hover
--color-accent-light
--color-ink
--color-ink-muted
--color-surface
--color-surface-muted
--color-border
--color-border-hover
--color-success
--color-warning
--color-danger
--radius-base
--radius-large
--shadow-subtle
--shadow-elevated
--shadow-focus
--transition-base
```

### 操作

1. 全局搜索 `var(--`；
2. 汇总所有使用到的变量；
3. 对照 `src/style.css`；
4. 将明显的旧名称统一替换，例如：
   ```text
   --color-primary      -> --color-accent
   --color-ink-lighter  -> --color-ink-muted
   ```
5. 不改变现有主题色和整体视觉；
6. 不在 M0 中重新设计设计 Token。

### 验收

- 页面不再引用明显未定义的 CSS 变量；
- 工作台图标和占位文字颜色正常；
- 登录页和后台主视觉无明显变化。

---

## M0-06 整理环境配置和前端说明

### 目标

让其他组员能够按文档启动前端并连接本地后端。

### 环境配置

新增或完善：

```text
frontend/.env.example
```

建议内容：

```env
VITE_API_BASE_URL=/api
```

保留当前开发代理：

```text
/api -> http://127.0.0.1:8080
```

除非当前代理确实无法使用，否则不要修改端口和目标地址。

### README

将默认 Vite 模板说明改为项目说明，至少包含：

```text
项目名称
技术栈
环境要求
安装依赖
启动命令
构建命令
前后端联调地址
开发分支
目录边界
```

推荐命令：

```bash
cd frontend
npm install
npm run dev
npm run build
```

### 注意

- 不在 `.env.example` 中写密码、Token 或数据库信息；
- 不修改后端数据库配置；
- 不提交 `node_modules/` 和 `dist/`；
- 不主动升级依赖；
- 不改变登录页现有资源路径。

### 验收

- README 不再是 Vite 默认模板；
- 新成员可以根据 README 启动项目；
- `.env.example` 不包含秘密信息；
- 代理配置与请求基地址一致。

---

## M0-07 构建和页面冒烟检查

### 自动检查

在 `frontend/` 下执行：

```bash
npm run build
```

如本机环境允许，再执行：

```bash
npm run dev
```

### 页面冒烟检查

至少检查以下路由可以正常加载：

```text
/login
/home
/persons
/households
/migrations/in
/migrations/out
/certificates
/users
```

### 检查内容

- 页面无白屏；
- 无资源 404；
- 无模块导入错误；
- 登录页视频失败时仍有图片背景；
- 列表接口失败时页面不崩溃；
- 分页组件可以渲染；
- 控制台无因本阶段修改导致的新错误。

### 验收

- `npm run build` 成功；
- 主要页面可打开；
- 无新增的模块解析错误；
- 无误删资源。

---

# 6. 建议执行顺序

```text
执行前检查
  -> M0-01 目录边界
  -> M0-02 合并人口 API
  -> M0-03 清理无效文件
  -> M0-04 统一分页
  -> M0-05 修复样式变量
  -> M0-06 README 和环境示例
  -> M0-07 构建与冒烟检查
  -> 输出执行报告
```

不得跳过构建验证。

---

# 7. 预期文件变更

## 必然修改

```text
frontend/src/router/index.js
frontend/src/style.css
frontend/README.md
```

## 必然删除

```text
frontend/src/api/person.js
```

## 建议新增

```text
frontend/src/utils/page.js
frontend/.env.example
```

## 可能修改

```text
frontend/src/views/persons/PersonList.vue
frontend/src/views/households/HouseholdList.vue
frontend/src/views/migrations/MigrationList.vue
frontend/src/views/certificates/CertificateList.vue
frontend/src/views/users/UserList.vue
```

## 确认无引用后才可删除

```text
frontend/src/views/Home.vue
frontend/src/components/HelloWorld.vue
frontend/src/assets/vite.svg
frontend/src/assets/vue.svg
frontend/src/assets/hero.png
```

---

# 8. 完成标准

M0 只有同时满足以下条件才算完成：

1. 当前分支仍为 `feat/frontend-population`；
2. 用户原有未提交修改没有被覆盖；
3. 所有前端源码仍位于 `frontend/`；
4. `person.js` 已清理，业务 API 统一复用 `request.js`；
5. 路由中无未使用的 `Home` 导入；
6. 候选模板文件均经过引用检查；
7. 五个现有列表页使用统一分页解析；
8. 页面不再引用明显未定义的 CSS 变量；
9. README 已替换为项目启动说明；
10. `.env.example` 不包含敏感信息；
11. `npm run build` 成功；
12. 现有登录页和业务页面未被重设计；
13. 未实现任何 M1 及后续阶段功能；
14. 提供完整执行报告。

---

# 9. Agent 最终输出格式

执行完成后，必须按以下格式回复：

```markdown
## M0 执行结果

### 1. 完成情况
- [x] M0-01 ...
- [x] M0-02 ...
- [ ] M0-xx ...（说明未完成原因）

### 2. 修改文件
- `路径`：修改说明

### 3. 删除文件
- `路径`：删除原因及引用检查结果

### 4. 新增文件
- `路径`：用途

### 5. 验证结果
- `npm run build`：通过/失败
- 页面冒烟测试：通过/部分通过/未执行
- 控制台错误：无/说明

### 6. 未解决问题
- 仅列出超出 M0 范围的问题，不继续扩展处理

### 7. Git 状态
- 当前分支：
- 起始 commit：
- 当前 `git status --short`：
```

不得仅回复“已完成”，必须给出文件级变更和验证结果。

---

# 10. 建议提交信息

M0 完成并人工验收后，建议提交：

```text
refactor(frontend): 完成 M0 工程清理与基线稳定
```

本阶段不要自动合并到 `develop` 或 `main`，由项目负责人检查后决定。
