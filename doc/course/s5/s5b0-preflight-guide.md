# S5-B0 只读预检说明（冻结候选）

状态：CODE_CANDIDATE。所有未获组员确认的部署字段与接口仍为 PENDING_CONFIRMATION。静态 PASS 不等于真实环境 READY。

## 1. 默认 Offline

使用 PowerShell 7（本轮7.6.9），无需额外模块或管理员权限。在worktree根运行：

```powershell
pwsh -NoProfile -File ./scripts/windows/s5b0-preflight.ps1
```

默认只读显式清单内的源码、示例配置和文档，不读取真实.env、环境凭据、用户材料或凭据管理器，不展开配置值。不联网、不写文件/目录、不改环境、不调用Git/Maven/mysql/Docker/已有演示脚本、不启动或停止进程，stdout仅输出JSON，不自动落盘。

RepoRoot默认由脚本位置定位。显式提供时须为本地Fixed/Ram驱动器上的绝对路径；在文件读取前拒绝UNC、设备路径、网络盘及父目录重解析点，跳过叶子重解析文件。缺文件或无法安全读取报告BLOCKED，不自动修补。

dot-source只注册函数，不运行入口。RepositoryReader/Transport仅为函数内部测试接缝，不是CLI参数；假传输始终标MOCK_TRANSPORT/SIMULATED_HTTP。

## 2. 检查范围与状态

| ID | 实际检查 | 不代表 |
|---|---|---|
| REPO-FILES | 固定清单34个文件存在且可安全读取 | 已部署或已迁移 |
| CONFIG-NAMES | 源码34个环境占位符名称仍存在 | 实际值一致、凭据安全、连接成功 |
| SERVICE-OWNERS | SVC-01—14矩阵第二列有分工主责 | 主责已确认或已完成组件 |
| KNOWN-GAPS | GAP-01—08记录存在 | 自动验证说明与未来源码始终一致 |
| B1-GATES | B1-01—10前置条件存在 | 条件已满足、自动获得部署授权 |
| ENV-CONFIRMATION | 本工具不读取实际部署输入，固定BLOCKED | 真实环境READY |
| REAL-INTEGRATION | 本轮真实联调固定NOT_VERIFIED | 任何真实功能已通过 |
| HTTP-01等 | 显式目标的既有健康响应 | 双实例/复制/撤销历史/消息/文件正确 |

Document Analysis尚不存在的HTTP地址/认证参数和生产路由参数不编造为已支持配置。源码变化后需同步人工复核本工具固定清单及候选契约。

JSON包含schemaVersion、mode、overallStatus、exitCode、evidenceClass、checks；check包含id/category/status/reason。只输出固定原因或已知仓库文件/配置名称，不输出配置值、URL、响应正文/头、异常原文。

| 状态 | 含义 | 退出码 |
|---|---|---|
| FAIL | 参数、安全边界或已检查契约违反 | 1 |
| BLOCKED | 缺文件、不可安全读取或缺环境确认 | 2 |
| NOT_VERIFIED | 尚未完成所需真实验证 | 3 |
| PASS | 仅本项指定范围通过 | 0（全部检查PASS时） |

总体优先级FAIL > BLOCKED > NOT_VERIFIED > PASS。当前正常Offline应为静态项PASS、环境确认BLOCKED、真实集成NOT_VERIFIED，总体BLOCKED/2是预期准备状态，不能改写为READY。CI可逐项检查STATIC，但必须同时保留总体及真实环境状态。

## 3. 显式 ReadOnlyProbe

以下均为EXAMPLE，不在本轮执行。目标和端口必须由主责提供；非本机还需明确授权并提供独立开关。

```powershell
# EXAMPLE：明确本机地址，不猜端口
pwsh -NoProfile -File ./scripts/windows/s5b0-preflight.ps1 -Mode ReadOnlyProbe -HealthUrl 'http://127.0.0.1:8080/api/health' -TimeoutSeconds 5

# EXAMPLE：非本机明确授权；example.invalid不代表实际部署
pwsh -NoProfile -File ./scripts/windows/s5b0-preflight.ps1 -Mode ReadOnlyProbe -HealthUrl 'https://example.invalid:8443/api/health' -AllowNonLocal -TimeoutSeconds 5
```

最多8个显式目标，单目标超时1—30秒、默认5秒，无重试、扫描、端口猜测或自动发现。整组URL先验证，任一非法则全组不发请求；Offline携带探测选项直接FAIL。

只允许小写http/https及原始精确/api/health路径。先验证原字符串，再解析URI，拒绝百分号编码、userinfo、query、fragment、控制字符、非ASCII、反斜线、dot segment、尾斜线、IPv6 scope及非法端口。业务GET不是只读探测白名单。

本机判定：
- IPv4要求严格点分十进制，按IPAddress.IsLoopback识别127/8；拒绝缩写、整数、十六进制、前导零等表示。
- IPv6要求方括号；IPv4-mapped先映射IPv4后判断。例如[::ffff:127.0.0.1]为loopback，[::ffff:192.0.2.20]不是。
- localhost固定改写为127.0.0.1，不进行DNS；HTTPS仍验证改写后IP的证书，仅localhost证书可能不通过，应提供匹配证书及明确地址。
- 其他地址/DNS名均需-AllowNonLocal；开关不放宽其余规则。

HTTP使用.NET自带HttpClient做受限GET，不实现通用客户端，复用既有健康脚本的HTTP200、业务code200、database=UP判定。禁止代理、自动凭据、Cookie、重定向和重试；保持平台TLS证书验证，不安装绕过回调。

取消期限覆盖连接、响应头、正文读取；成功正文限16KiB，响应头限16KiB。3xx不跟随、不读Location及错误正文；401/403不自动获取认证。输出不含实际URL、body、headers或异常Message。

服务端既有/api/health仍会执行数据库SELECT 1及可选Redis PING。客户端不发送数据库命令，但不能称服务器零操作。它不验证RabbitMQ/Python/共享文件、严格撤销写权限、撤销历史或复制追平。Python健康接口未确认，保持PENDING_CONFIRMATION，不根据base-url猜/health。

## 4. 自身测试与证据限制

```powershell
pwsh -NoProfile -File ./scripts/windows/tests/s5b0-preflight.tests.ps1
```

测试只读一次真实源码清单，然后在内存克隆夹具；缺文件、恶意URL、重定向、健康响应、异常和超时都由假transport模拟。无临时文件、监听器、测试服务器或进程，无删除/覆盖/清理动作，不连接团队服务。

超时测试只证明错误映射和有界参数传递；无真实DNS/TCP/TLS、证书或慢正文验证。MOCK_TRANSPORT不能复制进真实验收PASS栏。本轮不启用真实ReadOnlyProbe。文件/进程/数据库禁写另通过实际脚本操作清单静态核对。

实际命令和结果见[验证记录](s5b0-verification.md)，成员确认和十项B1门槛见[对接清单](s5b0-team-handoff.md)。任何静态检查通过均不触发部署或S5-B1。
