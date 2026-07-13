# Redis 可选增强设计

Redis 默认关闭（`REDIS_ENABLED=false`），仅缓存启用的行政区划树/子节点、启用字典和少量统计结果，并保存已注销 JWT 的随机 jti。禁止缓存完整人口、身份证、手机号、家庭户敏感详情、重点人口详情、材料、审批和日志明文。

配置项包括 `REDIS_HOST/PORT/PASSWORD/DATABASE/TIMEOUT/KEY_PREFIX` 及各类 TTL。键统一以 `population`（可配置）开头。数据使用 JSON，不使用 JDK 原生序列化。读取采用 cache-aside；区划和字典写事务提交后删除对应键。Redis 不可达时限频告警并直接查询 MySQL；JWT 撤销使用进程内、按到期时间清理的短期后备表。Redis 中只存 jti 标记，不存完整 Token。

健康检查 `GET /api/health` 仅返回 database、redisEnabled、redisStatus 和 cacheMode，不泄露连接串或密钥。
