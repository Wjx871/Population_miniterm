# 微信小程序移动管理端 V1 测试报告

报告日期：2026-07-14。代码基线：`origin/develop@47fa77f`，开发分支 `feat/wechat-miniprogram-v1`。

## 自动化结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| 小程序单元测试 | PASS | `npm test`：19 tests，19 pass，0 fail，0 skipped |
| 小程序静态检查 | PASS | 11 个页面文件完整；JavaScript 语法、WXML 标签、旧接口和 BASE_URL 检查通过 |
| `git diff --check` | PASS | 无空白错误 |
| 后端 `clean test` | PASS | 326 tests，Failures 0，Errors 0，Skipped 0，BUILD SUCCESS |
| 后端 `clean package` | PASS | 326 tests，Failures 0，Errors 0，Skipped 0，JAR 构建成功 |
| PC 前端 `npm install` | PASS | 106 packages，0 vulnerabilities |
| PC 前端 `test:unit` | PASS | 195 tests，195 pass，0 fail，0 skipped |
| PC 前端 `check/build` | PASS | 检查与 Vite 生产构建成功；仅有既有依赖注释/包体积警告 |

自动化覆盖：`ApiResponse<T>` 解包、Bearer Token、401 清理、403 不退出、400/404/409/500 文案、服务完整路径、人口/家庭户 Adapter、申请和材料状态中文化、权限入口、审批查看/处理权限分离、记住账号不保存密码以及 logout 清理。

## 微信环境状态

当前命令行环境未提供可确认的微信开发者工具 CLI、可登录开发者身份、合法 AppID 或真机，因此未虚报以下结果：

- 微信开发者工具编译/交互烟测：未执行。
- 真机局域网或 HTTPS 联调：未执行。
- 真机尺寸、滚动、键盘遮挡、刘海和底部安全区：未执行。
- 运行时 HTTP 500 与 console error 计数：待开发者工具实测。

## 人工验收步骤

1. 按开发指南导入 `miniprogram`，启动后端并配置地址。
2. 使用 `admin/123456` 登录；网络面板确认先调用 login，再调用 `/api/auth/me`。
3. 刷新/重新编译，确认 Token 存在时由 `/api/auth/me` 恢复身份。
4. 工作台真实加载人口、家庭户、待审批；断开后端后确认显示错误与重试而不是 0。
5. 验证人口搜索、分页、下拉刷新、详情脱敏字段和当前家庭关系。
6. 验证家庭户搜索、分页、详情、状态中文和成员关系。
7. 验证“我的申请”的状态筛选、通用详情、专业摘要、材料和审批轨迹；制造专业记录 404 时不能白屏。
8. 使用 `approver/123456` 验证待审批、已审批和审批详情。
9. 对测试申请执行一次通过或驳回；确认请求携带最新 `version`，重复提交显示 409。
10. 通过后确认提示“业务尚未执行”，并检查专业业务表/状态未被自动执行。
11. 使用 `viewer/123456`，确认没有审批入口和审批请求。
12. 分别使用 `population`、`household`，确认入口严格服从 `/auth/me.permissions`，没有专属越权写操作。
13. 退出登录，确认本地 Token/用户清除且旧 Token 请求返回 401；记住账号只保留用户名。
14. 检查页面无 `undefined`、`[object Object]`、英文状态、白屏或横向宽表格。
15. 检查开发者工具 Network 中 400/401/403/404/409/500 提示，以及 console 非预期错误为 0。

## 真机最小清单

- 使用电脑局域网 IP 或部署的 HTTPS 地址，禁止使用手机自身 localhost。
- 登录、核心页面滚动、下拉刷新、分页加载和退出可用。
- 输入键盘不遮挡登录按钮和审批意见。
- 刘海区域、导航栏和底部安全区无内容遮挡。
- 材料图片、PDF/Word 在目标机型上的预览行为符合预期。
