# Backend V1 运维指南

- 日志：Log4j2 输出控制台及 `logs/` 滚动文件；运行账户需有写权限。禁止在启动参数或日志中打印密码和 Token。
- 目录：上传和导出位置由 `APP_UPLOAD_DIR`、`EXPORT_DIR` 控制；备份时应与数据库快照保持同一时间点。`data/`、`logs/`、`tmp/`、`cache/` 不进入 Git。
- Redis：故障时系统自动回退 MySQL，健康状态显示 DOWN/MYSQL_FALLBACK。恢复后新请求会重建缓存；无需从 Redis 恢复业务数据。
- JWT：更换 `JWT_SECRET` 会使所有旧 Token 立即失效，应安排维护窗口并通知重新登录。
- 常见错误：数据库 DOWN 检查 URL、账号和防火墙；JWT secret 错误按启动信息补足长度；目录错误检查相对路径基准和权限；Redis DOWN 可先设置 `REDIS_ENABLED=false`。
- 停止：正常终止 Java 进程，确认服务端口释放。卸载时先备份 MySQL 和业务文件，再删除 JAR、日志、上传、导出目录；Redis 数据可直接清理。
- 一键脚本：`scripts/windows/start_all.bat` 读取本地环境文件，不应覆盖用户已配置变量。
