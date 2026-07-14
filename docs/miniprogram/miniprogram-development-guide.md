# 微信小程序移动管理端开发指南

## 环境要求

- Java 17 与可运行的现有 Spring Boot 后端。
- 微信开发者工具稳定版。
- Node.js 18 或更高版本，仅用于小程序源码单元测试和静态检查；小程序运行本身不依赖 npm 包。

## 导入开发者工具

1. 启动现有后端并确认 `GET http://127.0.0.1:8080/api/health` 返回 200。
2. 微信开发者工具选择“导入项目”，目录指向仓库下的 `miniprogram`。
3. 未配置真实 AppID 时使用测试号/游客模式；不要把真实 AppID 或 AppSecret 提交到仓库。
4. 开发阶段打开“详情 → 本地设置”，勾选“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”。该设置仅供本地开发，不能替代正式域名配置。
5. 编译后使用系统账号登录。课程演示账号为 `viewer/population/household/approver/admin`，本地演示初始密码均为 `123456`。

## 后端地址配置

默认模拟器地址在 `miniprogram/config/index.js`：

```text
http://127.0.0.1:8080
```

本机需要覆盖时，复制 `config/env.example.js` 为 `config/local.js`，只修改 `BASE_URL`。`local.js` 已被 Git 忽略；不得把真实局域网 IP、Token 或个人配置写入业务文件。

真机里的 `localhost/127.0.0.1` 指手机自身，不能访问电脑后端。真机联调可选：

- 手机和电脑连接同一可信局域网，把 `BASE_URL` 临时设为 `http://电脑局域网IP:8080`，并确保系统防火墙只放行所需网络范围；或
- 使用已部署、证书有效且已备案的 HTTPS 后端。

正式发布必须在微信公众平台配置 `request`、`uploadFile` 和 `downloadFile` 合法域名，并使用受信任 HTTPS 证书。不要为了小程序把 Spring Security/CORS 改成允许所有来源；小程序请求也必须继续经过 JWT、权限和数据范围校验。

## 目录与约定

- `services/request.js` 是唯一网络封装，其他 Service 不直接调用 `wx.request`。
- `services` 保存后端正式路径；`adapters` 负责分页、空值和中文状态映射。
- `permissions` 只来自 `/api/auth/me`。入口、按钮和请求调用前都要检查权限。
- 本地只缓存 Token、用户基础信息、权限和记住的用户名；退出时清理会话缓存。
- 详情页面不持久化人口、家庭户、材料或审批数据；下载文件只使用微信临时路径。

## 本地自动化

在 `miniprogram` 目录执行：

```powershell
npm test
npm run check
```

`npm test` 覆盖统一解包、Bearer 请求头、错误状态、缓存生命周期、权限入口和 Adapter；`npm run check` 校验 11 个页面文件、JavaScript 语法、WXML 标签、禁止旧接口以及 BASE_URL 是否散落。

## 常见问题

- “后端服务不可达”：先检查后端健康接口、`BASE_URL`、手机/电脑网络和防火墙。
- 模拟器可以、真机不可以：通常是把手机的 localhost 当成电脑、局域网隔离或合法域名未配置。
- 401：Token 已失效，小程序会清理会话并回到登录页。
- 403：当前账号没有权限或超出数据范围；小程序不会自动退出。
- 409：审批状态或版本已变化，刷新详情后再操作。
- 材料无法预览：图片使用 `wx.previewImage`；PDF/Word 等由 `wx.openDocument` 能力决定，不支持的类型仍可明确提示。
