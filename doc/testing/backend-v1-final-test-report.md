# Backend V1 最终测试报告

## 环境与结果

- Windows / Java 17 / Spring Boot 3.5.3 / 普通 MyBatis。
- 自动化：321 项，Failures 0、Errors 0、Skipped 0，`clean package` 成功。
- MySQL Community Server 8.4.10，独立端口 3310。
- Redis 7.0.15，独立端口 6388。
- 可执行 JAR：`population-miniterm-1.0.0.jar`。

## 数据库

全新路径依次执行主脚本和两个 demo 脚本：33 张表、114 个索引名、0 个视图、13 个人口、7 个家庭户、63 项权限、11 项字典、7 个区划。五角色均能登录。`check_backend_v1.sql` 的 10 项异常计数全部为 0。

升级路径从 `phase-01-auth-rbac^` 的最早支持基线开始，V4_001—V4_010 顺序成功，并全部重复执行成功。最终同为 33 张表、114 个索引名、63 项权限、11 项字典、7 个区划；全新与升级索引集合双向差异为 0，基线数据未丢失。

## 安全、事务和业务

Controller 元测试覆盖全部映射；匿名入口仅登录和健康检查。viewer 合法写请求为 403，approver 调用专业 execute 为 403，admin 为 ALL。既有测试覆盖区域隔离、默认脱敏、导出白名单、日志清洗、专业申请审批后显式执行、重复执行冲突、并发防线和异常回滚。生产代码无 `/api/residents`，person/household 无普通物理删除入口。

## Redis 与 JAR

- 正常：健康 UP；区划、字典、统计缓存生成；区划写后缓存失效；TTL 正确；logout 后旧 JWT 为 401；Redis 仅保存聚合 JSON 和 jti。
- 禁用：健康 DISABLED/MYSQL_FALLBACK，登录和统计正常。
- 不可达：健康 DOWN/MYSQL_FALLBACK，统计正常，验证窗口告警 1 次，无无限重试。
- 短 JWT_SECRET 明确拒绝启动。数据库不可达时 JAR 启动但健康明确为 database=DOWN。
- 上传、导出、日志目录可配置并创建；JAR 不含这些目录、临时数据库、Redis 数据或测试运行产物；停止后测试端口释放。

## 已知限制

V1 不含用户/角色/部门在线维护、多级审批、政务联网、实体制卡、消息队列、短信邮件、AI 风险预测和全国完整区划。数据库连接采用健康检查暴露 DOWN，而非启动期强制退出。测试账号密码仅用于课程演示，部署必须替换。

真实环境完成后已停止 Java、MySQL、Redis并删除临时数据库、安装包、数据、上传、导出和日志目录。
