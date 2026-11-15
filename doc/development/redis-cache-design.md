# Redis 可选增强设计

Redis 默认关闭（`REDIS_ENABLED=false`），仅缓存启用的行政区划树/子节点、启用字典和少量统计结果，并保存已注销 JWT 的随机 jti。禁止缓存完整人口、身份证、手机号、家庭户敏感详情、重点人口详情、材料、审批和日志明文。

配置项包括 `REDIS_HOST/PORT/PASSWORD/DATABASE/TIMEOUT/KEY_PREFIX` 及各类 TTL。键统一以 `population`（可配置）开头。数据使用 JSON，不使用 JDK 原生序列化。读取采用 cache-aside；区划和字典写事务提交后删除对应键，批量失效使用 SCAN 分批删除。Redis 不可达时限频告警并直接查询 MySQL。

共享撤销改造后，JWT 撤销先把 jti 的 SHA-256 和到期时间持久化到主库 `security_token_revocation`，再更新 Redis 正向标记。Redis 未命中时始终查主库，不缓存“尚未撤销”结论；主库校验或持久化失败返回 503。Redis 中不存完整 Token。需要先执行 V4_014 迁移。

字典缓存区分 `dictionary:manage:*` 和 `dictionary:enabled:*`，避免管理员与普通用户混用停用项。统计使用 `statistics:v2:*` 命名空间，包含角色、数据范围、地区/部门及 SELF 用户 ID；旧键自然过期。SCAN 失效不保证并发回填场景下的强一致，统计仍受 TTL 陈旧窗口约束。

健康检查 `GET /api/health` 仅返回 database、redisEnabled、redisStatus 和 cacheMode，不泄露连接串或密钥。
