# #29 / #64 证据索引（2026-11-12）

基线 `5e0daaea59da7f616dd6c71bff0e57c5513cf6e9`。日志均保留在 feature worktree 的 `doc/development/evidence/issue29-64-20261112/`；仅挑选不含令牌的简洁 HTTP 结果进入 Git。以下哈希是本机完整文件的 SHA256。

首次全量原始日志意外包含隔离测试 JWT，保留在本机且不得提交或分享；同目录脱敏副本仅替换 JWT，哈希另列。原始日志不符合“原始日志不含完整 JWT”的要求，不能将脱敏副本称为原始日志。其他详细 Maven 日志和工作数据保留本机，不假定其适合公开。

## 本轮日志

| 文件 | SHA256 |
|---|---|
| `classpath.log` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `full-maven-redacted.log` | `7cceafd025ff3dc2be5d5773a47a4c2526fcf83394cb1fe9f95b27625b45a083` |
| `full-maven-retry.log` | `7b11b4822be5f116d28be1472dbd9afa87d622deabffbe34c9456ec20e14b4d4` |
| `full-maven.log` | `f16279034ae6ca29f19e8eff3087a7f3dfc7f139043db68173029a3678682fd2` |
| `initial-state.txt` | `407800f6d6d971e15c1fda51eff3dda0ed6be7665e1dc0da4f77c9f63c58168a` |
| `issue29-characterization-maven.log` | `c3212a8c204225db12d533d1095d6dca9e62ef88a792ca677beb656407ee23c0` |
| `real-redis-http-attempt1.log` | `a1308d224eaa789a8b36efee2560ca75be6c4804cf76ebaf016f9b43f3502b5c` |
| `real-redis-http-attempt2.log` | `293e5d35a56ed653e3b543401a6d4aa48e1a5b1c96703f60a737a38bc066a2e3` |
| `real-redis-http-attempt3.log` | `8a446f5a004d56f993e85f427ab21ca8c00fb3d4cdd2fe8183d5b8a155b4e114` |
| `real-redis-http-attempt4.log` | `52181b0c1515a5f30ea16874831e6eeb8e920ce400a01f04c01f5c48da19a848` |
| `real-redis-http-attempt5.log` | `07286b8a480a3ecf436afd8a29a51ba32c03107c151c3f2e7cea7511ee313cce` |
| `targeted-maven.log` | `504ddbc82b85944d57e255ac8bd1907596023849cf566852f169cf7e5cffd688` |

## 最终真实 Redis 运行

便携包：[redis-windows 8.10.3 发布资产](https://github.com/redis-windows/redis-windows/releases/tag/8.10.3)，非官方 Windows 构建；ZIP SHA256 `af16df340525b431e9ec8dabfd625d5e7a233f82805b5418819904ea909c447f`。

本机目录 `C:\Users\jxgm\.codex\worktrees\issue29-64-real-redis\redis-isolated\run-e0172888e4b74b7689e2ff4fe5f1ac69`；密码保存在该目录的配置中，不提交。Redis 仅绑定 127.0.0.1:3500，PID 18320（初次）、39020（恢复）、39604（空目录）；A/B 初始 PID 37388/31640，重启 PID 39548/39260，HTTP 端口 3501/3502。程序退出前均停止本轮进程。RDB 自动保存关闭，AOF 关闭；通过 SAVE 后 SHUTDOWN NOSAVE 正常停止，使用同目录恢复。空目录用于单独数据丢失表征。

| 保留的进程日志 | SHA256 |
|---|---|
| `app-A1.log` | `dfeb02f531a0624cca923b3e4877628930b66f1fb00c380a218de5ce6bf6daab` |
| `app-A2.log` | `504d57990a6618f4c9fa44c845be14d1baf3a4dc9dd859b7b6d5e6055e64b0b0` |
| `app-B1.log` | `36900d9ec8cea746fd8075bb44fb6d594e9450d0466ab9928e4426cc7d68205d` |
| `app-B2.log` | `ce0cb68f975a4128db7ac31f913ccc610bf9cc439fe916234c8af5f4fc381c54` |
| `redis-empty.log` | `80ccce1465fa68f0d575393e417d650c3baae0d188b4d1473c5210a62125fd5e` |
| `redis-initial.log` | `6fa30813fb2bcbbf292565bb094e2c3a5dc963525a8c65813cc359992ca62293` |
| `redis-restart.log` | `579f79fc375905ee8bd50744ef0c3f5a93f99b1fc9ccc291ad3718d75e64065c` |

H2 使用各 JVM 独立内存库；测试 JWT 和凭据不写入以上 HTTP 事件日志。没有进行真实 MySQL 验证。`real-redis-http-attempt1` / `attempt2` 是配置路径启动失败，`attempt3` 在重启后立即得到 503 而停止；`attempt4` 等待重连并完成但采用显式 SAVE 后进程终止；`attempt5` 补测正常关闭，重连后完成。

本轮测试脚本为 `scripts/issue29-64/real_redis_http_v6.py`，需独立准备索引中的便携 Redis 和 Maven 测试 classpath；运行会启动并停止自己创建的进程，保留数据目录。其他脚本初稿和失败日志仅在本机保留。
