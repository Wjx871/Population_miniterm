# 人口数据库管理系统前端

本目录是人口数据库管理系统的 Vue 3 前端，提供登录、工作台、人口、户籍、迁入迁出、注销、流动人口、居住证、重点人口、导出审计、操作/登录日志、综合查询、数据大屏、数据字典与行政区划等页面。

## 技术栈

- Vue 3、Vite、Vue Router、Pinia
- Element Plus、Axios、Day.js、ECharts

## 环境要求

- Node.js 20 或更高版本
- npm 10 或更高版本

## Windows 一键启动（推荐）

在项目根目录（非 `frontend/`）双击：

```text
start.bat
```

根目录仅保留这一处启动入口，具体脚本统一放在 `scripts/windows/`：

| 脚本 | 作用 |
|------|------|
| `start.bat` | 一键启动前后端（推荐） |
| `scripts/windows/start_all.bat` | 验证 `/api/health` 的数据库状态后启动前端 |
| `scripts/windows/start_backend.bat` | 仅启动后端 |
| `scripts/windows/start_frontend.bat` | 仅启动前端（健康检查失败时需明确确认才继续） |

首次使用前，在项目根目录复制配置并填写数据库密码：

```cmd
copy config\start.local.env.example start.local.env
```

`start.local.env` 含密码，已被 `.gitignore` 忽略，不会提交。

默认端口：后端 `8080`、前端 `5180`。修改 `start.local.env` 中的 `SERVER_PORT` 和 `FRONTEND_PORT` 即可自定义。修改 `SERVER_PORT` 后 Vite 代理会自动同步，无需手动修改 `vite.config.js`。

## 安装与启动（手动）

在 `frontend/` 目录执行：

```bash
npm ci
npm run dev
```

开发服务器默认从 `http://localhost:5180` 提供服务；端口被占用时 Vite 会直接退出（`strictPort: true`），不会自动切换到其他端口。

## 构建

```bash
npm run build
```

构建产物位于未提交的 `dist/` 目录。

## 前后端联调

- 前端请求基地址由 `VITE_API_BASE_URL` 配置，默认值为 `/api`；可参考 `.env.example`。启动脚本会显示该值，非 `/api` 会明确提示其绕过 Vite 代理的风险，但不会覆盖用户本地配置。
- 开发环境会将 `/api` 代理到后端地址（默认 `http://127.0.0.1:8080`），代理目标由 `scripts/windows/start_frontend.bat` 通过 `VITE_BACKEND_TARGET` 环境变量传入，也可在 `start.local.env` 中通过 `SERVER_PORT` 控制。
- 前端分页状态使用 `current`（从 1 开始），请求层会转换为 Spring `Pageable` 的 `page`（从 0 开始）和 `size`。

`npm run dev` 和根目录 `start.bat` 使用 Vite `/api` 代理；`npm run preview` 和直接部署 `dist/` 不提供开发代理。部署时应通过 Nginx 或网关配置同源 `/api` 反向代理，参考 `config/nginx.population.conf.example`。

## 开发约定

- 开发分支：`feat/frontend-population`。
- 所有前端源码和配置均位于 `frontend/`；不要在仓库根目录新增重复的 Vite、Vue 或 `src/` 文件。
- 业务 API 必须复用 `src/api/request.js`，不要自行创建 Axios 实例或读取 Token。
- 专业业务申请详情统一走 `/applications/:applicationId` + `features/applications/handlers`，不新增重复详情页。
- 显式执行默认只提交真实 `version`；导出下载必须 `responseType: 'blob'` 且 `rawResponse: true`。
- 不提交 `node_modules/`、`dist/`、本地环境文件或敏感信息。

## 测试

```bash
npm run test:unit
npm run build
npm run check
```

单元测试使用 Node 原生 `node --test`，覆盖 payload/route/handler/权限契约。
