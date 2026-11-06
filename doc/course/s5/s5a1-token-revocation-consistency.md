# S5-A1：JWT 跨实例撤销与 Redis 故障安全

日期：2026-11-06。基线：`16da441e99aa7827a7da2169f85cfa38a311a3e8`。

本轮是审计 #64 的 Java `CODE_CANDIDATE`，提供隔离 H2、Mock Redis 和两独立服务实例的验证。真实 Redis + Nginx 双 Java 实例仍为 **NOT_VERIFIED**，不得据此关闭 #64 或宣称 S5 部署验收完成。

## 1. 根因与职责

原实现先写 JVM 本地 Map，再调用会吞异常的 `putMarker()`；另一 JVM 查询 Redis 失败时，`contains()` 返回 false，无法区分未撤销和存储不可用。退出因本地保存成功而可能记录虚假的 `LOGOUT SUCCESS`。本地 Map 又只有按 JTI 查询时的被动过期删除。

本轮仅负责 Java 撤销、过滤链、登录退出、配置门禁及测试，不修改赵书亚的 Docker/Nginx/MySQL 主从配置。S1 架构决策文档的共享 Redis 对接事项在这里细化，其他 S1 待确认接口不因此冻结。

## 2. 模式与生产门禁

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| `security.jwt.revocation.mode` | `SINGLE_INSTANCE_LOCAL` | `JWT_REVOCATION_MODE`，只保证单 JVM 本地撤销 |
| `security.jwt.revocation.production` | `false` | `JWT_REVOCATION_PRODUCTION`，显式声明生产环境 |
| `security.jwt.revocation.allow-production-single-instance` | `false` | 生产单实例例外开关 |
| `security.jwt.revocation.production-single-instance-approval` | 空 | 例外审计编号，不得放凭据或自由文本 |
| `security.jwt.revocation.cleanup-interval` | `PT30S` | 本地清理间隔 |
| `security.jwt.revocation.cleanup-batch-size` | `256` | 每次最多检查记录数 |

`prod` 或 `production` Spring profile 自动启用生产门禁，不能通过将 production 标志设为 false 绕过。使用其他生产 profile 时，部署必须设置 `JWT_REVOCATION_PRODUCTION=true`。程序不能从机器所在地或副本数自行推断生产环境；未声明环境的默认本地模式绝不构成双实例安全保证。

生产默认本地模式会启动失败。允许生产单实例例外时，必须同时设置 `JWT_REVOCATION_ALLOW_PRODUCTION_SINGLE_INSTANCE=true` 和 `JWT_REVOCATION_PRODUCTION_SINGLE_INSTANCE_APPROVAL=<审批编号>`；编号只允许 3–128 个 ASCII 字母、数字和 `._:/-`，启动时记录 `REVOCATION_PRODUCTION_LOCAL_EXCEPTION`，仍不保证跨实例一致性。例外仅用于确实只有一个 JVM 的部署，不能用于双实例。

生产双实例必须显式配置以下变量（凭据从部署密钥注入，不提交仓库）：

```properties
SPRING_PROFILES_ACTIVE=prod
JWT_REVOCATION_PRODUCTION=true
JWT_REVOCATION_MODE=SHARED_REDIS_REQUIRED
REDIS_ENABLED=true
REDIS_HOST=<shared-redis-host>
REDIS_PORT=6379
REDIS_DATABASE=<same-database-for-both-instances>
REDIS_KEY_PREFIX=<same-prefix-for-both-instances>
REDIS_TIMEOUT=2s
JWT_SECRET=<same-random-secret-at-least-32-UTF8-bytes>
REDIS_PASSWORD=<injected-secret>
```

严格模式未开启 Redis、非法模式、非正清理配置均失败，不静默回退。本轮不在启动时连接 Redis；运行中故障通过 503 表达，以便保持原公开健康检查边界。

## 3. Redis 接口、Key 和 TTL

普通缓存的 `get/put/contains/putMarker/ping` 继续允许失败回源。安全路径新增 `strictContains(key)` 和 `strictPutMarker(key, expiresAt)`：前者成功返回明确的存在/不存在，异常或 null 结果失败；后者只有收到确认才完成，false/null/异常全部失败。异常保留内部原因，不将原始消息或堆栈输出到客户端和安全日志。

撤销键为 `<prefix>:security:jwt:blacklist:<jti>`，值固定为 `1`，不存完整 JWT、用户密码或声明集合。严格写入使用单条原子 `SET ... PXAT <expiresAtMillis>`，绝对到期时间等于 Token 的到期时间，不采用分开的 SET/EXPIRE，也不截断相对 TTL。要求 Redis >=6.2、命令 ACL 支持 SET/PXAT 和 EXISTS，并保持 Java/Redis 时钟同步。本轮 Mock 仅验证发送参数和确认语义，真实命令兼容及服务端 TTL 未验证。

严格鉴权每次查询共享状态，既不使用本地撤销结果替代共享查询，也不缓存 KEY_ABSENT。单实例模式保留本地 Map 和可选 Redis 辅助，Redis 中断不影响本地撤销。

## 4. HTTP、登录与退出

- 已明确撤销、过期、签名或必要声明无效：401。
- 无 Token：维持原有公开接口及受保护接口边界。
- 无法确认共享撤销状态：503，`code=503`，`message` 包含稳定标识 `REVOCATION_STORE_UNAVAILABLE`，`data=null`，沿用原响应结构。
- Filter 拒绝前清理 SecurityContext，不加载后续业务、不进入 Controller，不执行受保护业务写入。JWT 签名格式、白名单、RBAC、DataScope、账号/角色启停不变。

登录依旧跳过 JWT Filter，因此在 AuthService 内完成预检：先检查正确密码和账号/角色启用状态，然后写并读 `<prefix>:security:jwt:login-probe:<随机UUID>`，绝对 TTL 为 5 秒。读不到、写失败或查询异常均 503；不会签发成功响应、更新 last_login 或记录 LOGIN_SUCCESS。错误密码仍 401，停用账号/角色仍 403，且不访问预检存储。预检无 Token 内容、不占本地 Map，Redis 到期自动清理；它仅反映当次可用性，不保证未来请求或历史数据完整。

退出经真实 Filter 验证后，重新核验 JTI/有效期。严格模式必须共享写入确认，才记录 `LOGOUT SUCCESS` 并返回 200。查询阶段或写入阶段失败均不报告退出成功；写入结果不明确时也 503，即使 Redis 可能已经接收写入。客户端清除 Token 不代表服务端已完成跨实例撤销。

## 5. 本地清理与并发

已核实并复用 `FloatingResidenceConfiguration.businessClock()` 和现有 Spring `@EnableScheduling`，不新建 Clock Bean 或线程。Map 改为锁保护的插入有序 Map；查询、写入、清理均持有同一短锁。重复 JTI 保留较晚到期时间。

每次调度只取前 batch 条记录，删除过期项，将仍有效项轮转至末尾，保证有限批次内公平扫描；迭代器只在本次锁内使用，不跨调用或跨并发保留。未到期项的移除和重新插入都在锁内，鉴权线程不能观察到中间空隙。按键读取仍立即淘汰自身过期项。应用关闭由 Spring 结束调度，无自建轮询、无额外线程池。

## 6. 安全事件与观测

事件：`REVOCATION_READ_UNAVAILABLE`、`REVOCATION_WRITE_UNAVAILABLE`、`REVOCATION_REJECTED`、`REVOCATION_CONFIRMED`。记录安全 traceId、instanceId、操作类别、模式、安全错误类别、耗时和计数。沿用 S2TraceContext 的安全 trace 校验与 MDC 作用域恢复；实例号可通过 `-Ds2.instanceId=<safe-id>` 区分。

每种故障事件/操作最多每 60 秒输出一条告警；进程内计数仍记录全部发生次数，不新增匿名诊断端点或监控平台。正常拒绝和确认记录事件，不打印 JTI、键、Token、密码、Cookie、敏感请求头、Redis URL、异常原文或堆栈。进程重启会重置本地计数，后续采集侧接入不在本轮范围内。

## 7. Redis 数据丢失：本轮保证的严格边界

**严格模式仅在共享 Redis 数据状态完整且读写正常时，证明撤销状态可跨实例传播。KEY_ABSENT 只说明当前键不存在，绝不是撤销历史完整性的证明。**

Redis 重启、主从切换、数据回滚、内存驱逐、FLUSH、备份恢复和持久化丢失，都可能使已确认撤销的记录消失。重新恢复连接或 PING 成功不能识别和补回这些记录；本实现没有撤销历史数据库、全局 epoch 或数据丢失检测机制，不能宣称数据丢失后仍有全局安全保证。

生产运维要求（由部署主责落实，本轮未修改部署文件）：

1. 撤销 Redis 使用 `maxmemory-policy noeviction`，容量覆盖全部有效撤销键和登录预检峰值；内存耗尽使写失败时，Java 返回 503。
2. 明确配置 AOF、刷盘策略、备份、重启恢复与复制/故障切换策略，并书面记录可接受的数据丢失窗口；AOF everysec、复制及普通 SET 确认均不等于零丢失或副本持久化确认。
3. 限制 FLUSH、删除和配置变更权限，撤销命名空间不得作为可随意清空的普通缓存。
4. 故障恢复不能只恢复网络后直接宣布安全：先隔离认证流量，验证撤销数据完整性及持久化/复制状态。无法确认历史完整时，由负责人执行使旧会话失效的运维措施，例如协调双实例统一轮换 JWT_SECRET，或等全部旧 Token 最长生命周期结束；本轮不自动执行这些动作。
5. 两 JVM 与 Redis 校时；Redis 与客户端连接、命令超时及实例号在联调时核对。

## 8. 赵书亚真实联调清单（全部 NOT_VERIFIED）

- 两 Java 实例共享 Redis 主写/一致读端点、database、key-prefix、JWT_SECRET，均显式启用严格模式及生产门禁，不读可能落后的 Redis 副本。
- Nginx 证明请求实际落到 A/B 不同实例，记录安全 instanceId/traceId；不能只测同一 JVM。
- A 登录，B 能使用；A 退出确认后 B 立即 401；不同 JTI 不相互污染。
- Redis 断网/停机/连接拒绝、错误凭据、命令超时：鉴权和正确凭据登录 503，退出不虚假成功；业务写入和成功审计不发生。
- 恢复原完整数据后验证新请求、登录与撤销正常；故障前后的撤销仍有效。
- 核验服务端 PXAT 实际到期、临界时间和时钟偏差；预检键在短 TTL 后消失。
- 分别演练 Redis 重启、主从切换、回滚、FLUSH、持久化缺失及恢复旧备份，记录撤销键是否丢失和旧会话表现，不把 KEY_ABSENT 当完整性证明。
- 核验 noeviction 与内存耗尽写失败；确认恢复隔离、会话失效操作与重新开放流量的责任和证据。
- 普通业务缓存失败仍可回源；健康检查边界、RBAC、DataScope 和原业务流程不变。

审计 #3、#33 继续 OPEN，#21 继续 FIXED_VERIFIED。真实 Python/RabbitMQ/MySQL/前端联调保留原 NOT_VERIFIED。完成本轮后停止，不进入 S5-A2 或 S6。

## 参考

- [Redis SET：PXAT、确认返回值和版本要求](https://redis.io/docs/latest/commands/set/)
- [Spring Data Redis Expiration：绝对 Unix 时间](https://docs.spring.io/spring-data/redis/docs/3.5.3/api/org/springframework/data/redis/core/types/Expiration.html)
- 仓库 `doc/course/s1/architecture-decisions-and-confirmations.md`：Redis 双实例撤销对接与职责。
