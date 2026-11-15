# macOS 本地运行

## 当前工作区

本工作区于 2026-11-01 完成首次初始化，前后端连接真实 MySQL 演示库。

| 项目 | 本机配置 |
| --- | --- |
| 前端 | http://127.0.0.1:5180，Vue 3 + Vite |
| 后端 | http://127.0.0.1:8080，Spring Boot 3.5.3 |
| 健康检查 | http://127.0.0.1:8080/api/health，`data.database` 应为 `UP` |
| 数据库 | `127.0.0.1:3307/population_miniterm_local` |
| MySQL 数据文件 | 项目下 `tmp/mysql/` |
| 本地配置 | 项目下 `.env.local`，已忽略且文件权限为 600 |
| MySQL 管理配置 | `tmp/mysql-client.cnf`，已忽略且文件权限为 600 |
| 日志 | `logs/backend.log`、`logs/frontend.log`、`logs/mysql.log` |

使用已有 Node.js 24.15.0、Homebrew OpenJDK 21.0.12 和 MySQL 9.6.0。
后端按 `pom.xml` 编译为 Java 17 字节码，运行时使用 Java 21。
仓库原验收环境为 MySQL 8.4，本机 9.6 是开发演示环境；后续部署仍应按项目原部署规范验证。
本项目 MySQL 只监听本机 3307，原有 3306 实例保持原状。

## 启动与停止

在项目根目录执行：

```bash
# 启动已配置好的项目数据库、后端和前端。
bash scripts/macos/start.sh

# 修改后端源码后，重新打包启动；前端由 Vite 热更新。
bash scripts/macos/stop.sh
bash scripts/macos/start.sh --build

# 停止前后端与本项目 RAG 隧道。
bash scripts/macos/stop.sh

# 同时停止项目独立 MySQL 实例，数据文件保留。
bash scripts/macos/stop.sh --all
```

启动脚本读取 `.env.local` 中的 `JAVA_HOME`、`MYSQL_HOME`、`MYSQL_PORT`、数据库连接、
JWT 密钥、`SERVER_PORT` 和 `VITE_DEV_PORT`。它不自动建库、不执行迁移，也不覆盖已有数据。
关闭终端不会停止由脚本启动的服务。停止脚本通过 PID 和命令路径确认进程身份。
配置或数据文件缺失时应先检查原因，不要删除 `tmp/mysql/`；该目录包含全部本地业务数据。

登录账号为 `admin`、`viewer`、`population`、`household`、`approver`，本地课程演示密码均为 `123456`。
Redis 关闭。身份证 OCR 配置方法见下节。在线问答通过受限 SSH 隧道接入独立服务器的 DeepSeek RAG。启动脚本会复用或建立已授权隧道，停止脚本会停止本项目隧道。 [RAG 部署说明](../../docs/assistant/deepseek-deployment.md) · [验收报告](../../docs/assistant/deepseek-rag-validation-report.md)
微信小程序需要另行使用微信开发者工具配置，本次运行的是 Web 管理端。

## 身份证远程 OCR

项目根目录运行 `python3 scripts/macos/configure-baidu-ocr.py`，在终端提示中输入已开通身份证识别的百度 OCR **应用** API Key 和 Secret Key。脚本将 `IDCARD_OCR_BAIDU_API_KEY` 与 `IDCARD_OCR_BAIDU_SECRET_KEY` 保存到 Git 忽略且权限为 600 的 `.env.local`。更新代码和配置后按上节重启后端。后端向固定的百度 HTTPS 端点换取 OAuth 访问令牌，再调用身份证正面识别。已有兼容子服务的环境可通过 `IDCARD_OCR_URL` 和 `IDCARD_OCR_PATH` 指定地址，默认路径为 `/recognize/idcard`。 [百度调用文档](https://ai.baidu.com/ai-doc/OCR/Ck3h7y2ia)

从「人口信息管理 → 新增人员 → 扫描身份证」选择 JPG/PNG 正面图片。课程联调使用合成样张。识别成功后对照原图核对姓名、证件号、性别、出生日期、民族和证件住址，点「核对无误，回填表单」再继续建档。现居住地址单独填写。未配置、识别失败或字段有误时，可选择手工录入。远程模型返回的字段仅作为录入建议，建档仍需要单独提交。

仅上传者能取回影印本元数据，查询接口只返回脱敏证件号。上传响应中的完整证件号用于当前页面核对。供应商原始响应不落库，日志保留业务状态和固定错误说明。

## 首次初始化记录

本次在新建独立 MySQL 数据目录中创建 `population_miniterm_local`，导入顺序为：

1. `doc/database/population_miniterm.sql`。
2. `doc/database/migrations/V4_011_application_return.sql`。
3. `doc/database/migrations/V4_012_dashboard_viewer_scope.sql`。
4. `doc/database/migrations/V4_013_person_idcard_image.sql`。
5. `doc/database/migrations/V4_014_person_data_scope_ownership.sql`。
6. `doc/database/demo_data.sql` 和 `doc/database/demo_data_household_migration.sql`。
7. `doc/database/check_backend_v1.sql`，所有检查结果均应为 0。

导入时仅在内存中把 SQL 中的目标库 `population_miniterm` 替换为 `population_miniterm_local`，
原始 SQL 文件不改写。演示数据全部来自仓库课程脚本。
应用使用专用数据库用户和随机密码，独立 MySQL 的 root 也设置了随机密码。

注意：完整建库脚本已包含 V4_001—V4_010 对应结构，不应在其后重新执行整条历史迁移链。
例如 V4_003 仍直接引用旧字段 `residence.residence_type`，新库已使用 `register_type_code`，
混用两种初始化路径会报错。已有数据库的升级必须按实际版本选择脚本。

登录日志实际存储于 `operation_log`，由 `LogQueryMapper` 按 `LOGIN_SUCCESS`、
`LOGIN_FAILED`、`LOGOUT` 过滤。本次同步修正了 SQL 和 Windows 初始化工具要求独立
`login_log` 表的错误检查。

## 验证命令

```bash
# 前端单元测试与生产构建。
(cd frontend && npm run check)

# 后端测试（自动使用测试配置中的 H2）。
set -a
source .env.local
set +a
bash ./mvnw -B test

# 在已启动的真实后端上验证浏览器登录和主要页面。
cd frontend
E2E_BASE_URL=http://127.0.0.1:5180 E2E_PASSWORD=123456 \
  npx playwright test e2e/auth.spec.js e2e/pages.spec.js
```

浏览器测试需要 Playwright Chromium 已安装，可执行 `npx playwright install chromium --only-shell` 下载无头浏览器。
修改本地端口后，同时调整验证命令中的地址。

## 首次运行的测试基线

- 数据库 16 项结构与一致性检查全部通过。
- 五个演示账号均通过登录和 `/api/auth/me` 身份查询。
- 前端 220 项单元测试通过，生产构建通过；构建提示部分资源包超过 500 kB，后续可优化拆包。
- 后端打包通过；全量测试执行 308 项，304 项通过，3 项断言失败、1 项初始化错误，尚未修复：
  - `AuthRbacIntegrationTest` 两项用例仍要求 viewer 为 DEPARTMENT 范围，当前配置为 ALL。
  - `Phase06ExportAuditIntegrationTest.otherNormalUserCannotDownload` 期望 403，实际 200，后续需核实导出下载的数据范围规则。
  - `Phase10KeyPopulationIntegrationTest` 初始化人员时，身份证号与已有测试数据重复。
- 浏览器验收助手和退出断言已同步改为读取 `sessionStorage`，与当前前端按标签页隔离会话的实现一致。
- 登录与页面巡检用例合计 6 项，5 项通过；剩余一项仍预期统计大屏标题为“数据统计大屏”，实际标题为“人口数据库管理系统数据统计大屏”，页面已正常渲染。
  该轮使用本机已有 Chromium 进行验证，随后已补装项目匹配的 Playwright Chromium Headless Shell。
- 独立浏览器巡检确认操作日志、登录日志、行政区划、数据字典页面可加载；使用项目匹配的浏览器，确认统计大屏切换“真实”后数据来源为人口数据库管理系统、状态为正常，无 HTTP 500 或未捕获页面异常。

统计大屏默认开启“演示”模式，显示内置展示数据；查看本地数据库统计时应切换右上角为“真实”。
工作台截图位于 `logs/home.png`，统计大屏真实模式截图位于 `logs/statistics-live.png`。

完整输出保存在 `logs/frontend-check.log`、`logs/backend-test.log` 和 `logs/browser-check.log`。
上述测试问题保留为后续系统改进基线，不代表已完成全部业务验收。
