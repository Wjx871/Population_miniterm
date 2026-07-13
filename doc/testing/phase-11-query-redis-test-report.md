# Phase 11 验收记录

基线为 Phase 10 的 302 项测试。Phase 11 最终自动化测试为 313 项，Failures/Errors/Skipped 均为 0。

真实验收使用独立 MySQL 8.4.10（端口 3308、临时库 `population_miniterm_phase11_verify`）和 Redis 7.0.15（端口 6387）。主初始化脚本生成 33 张表；V4_010 连续执行三次成功，四组查询索引存在。

Redis 启用时健康为 UP，综合查询和统计返回 200，统计 JSON 键具有约 5 分钟 TTL，注销 jti 键 TTL 等于 Token 剩余有效期，旧 Token 返回 401，缓存值未包含身份证、手机号、密码或完整 Token。Redis 禁用时应用、登录和查询正常并返回 `MYSQL_FALLBACK`。Redis 配置为不可达时应用仍启动、统计返回 200、状态为 DOWN，并在验证窗口只产生一条限频降级警告。
