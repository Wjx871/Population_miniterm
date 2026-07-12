# 人口数据库管理系统前端

本目录是人口数据库管理系统的 Vue 3 前端，提供登录、工作台、人口、户籍、迁入迁出、证件和用户管理页面。

## 技术栈

- Vue 3、Vite、Vue Router、Pinia
- Element Plus、Axios、Day.js、ECharts

## 环境要求

- Node.js 20 或更高版本
- npm 10 或更高版本

## 安装与启动

在 `frontend/` 目录执行：

```bash
npm install
npm run dev
```

开发服务器默认从 `http://localhost:5180` 提供服务；端口被占用时 Vite 会选择下一个可用端口。

## 构建

```bash
npm run build
```

构建产物位于未提交的 `dist/` 目录。

## 前后端联调

- 前端请求基地址由 `VITE_API_BASE_URL` 配置，默认值为 `/api`；可参考 `.env.example`。
- 开发环境会将 `/api` 代理到 `http://127.0.0.1:8080`。
- 前端分页状态使用 `current`（从 1 开始），请求层会转换为 Spring `Pageable` 的 `page`（从 0 开始）和 `size`。

## 开发约定

- 开发分支：`feat/frontend-population`。
- 所有前端源码和配置均位于 `frontend/`；不要在仓库根目录新增重复的 Vite、Vue 或 `src/` 文件。
- 业务 API 必须复用 `src/api/request.js`，不要自行创建 Axios 实例或读取 Token。
- 不提交 `node_modules/`、`dist/`、本地环境文件或敏感信息。
