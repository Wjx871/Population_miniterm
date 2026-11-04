# S3 演示与验收记录

## 当前实际验证

本轮环境未发现 Docker、Podman、RabbitMQ CLI/Server，也未发现 AMQP/管理端口监听。因此没有启动或安装 Broker，没有修改系统配置。结论：H2/Mock 已验证；真实 RabbitMQ 发布确认、真实消费、DLQ、Broker 停止/恢复均为 `NOT_VERIFIED`。Python OCR/RAG 真实联调同样 `NOT_VERIFIED`，Mock 不作为其替代证据。

| 检查 | 结果 |
|---|---|
| Java 21 运行环境，项目 `--release 17` 编译 | 通过 |
| S3 定向 H2/Mock（21 项集成、2 项响应校验） | 23 tests，0 failures/errors/skips |
| S2 定向 API/RMI/聚合回归 | 初次 15 tests 全通过；随后 RMI 子套件 3 tests 中 1 项重复失败 |
| S3 阶段最终完整 `mvnw.cmd test` 报告 | 527 tests，0 failures，0 errors，0 skipped；这是 S3 阶段结果，不是本次 S4-A 回归计数 |
| Windows 演示脚本 `-Mode Mock`（另含默认关闭启动测试） | 24 tests，0 failures/errors/skips |
| PowerShell 脚本语法解析 | 通过 |
| MySQL 迁移执行 | 未执行；仅静态审查，未访问共享数据库 |
| RabbitMQ 真实生产/消费及 broker 故障恢复 | `NOT_VERIFIED` |
| Python OCR/RAG | `NOT_VERIFIED`；当前客户端为 `mode=MOCK` |
| 审计账本 #3 | 保持 `OPEN` |

H2 集成回归用 `CountDownLatch` 暂停首个请求在数据库 `UPLOADING` 已提交而物理对象尚未 promote 的位置。第二个同键请求稳定得到 409；任务创建在同一窗口得到 `MATERIAL_NOT_READY` 且事务回滚；reaper 不删除仍被 UPLOADING 行引用的临时对象。释放首请求后它返回 201/READY，同键重试返回相同 ID/200。另一项 latch 测试让首请求最终上传失败，期间第二个请求仍只能得到 409，失败后重试也不能成功。回归还覆盖同文件不同 key、跨用户 key 隔离、业务/角色/文件名差异、NFC 文件名归一化、路径及控制字符拒绝、取消/过期/IN_USE/BOUND 状态、READY 对象缺失，以及已 promote 文件的陈旧 UPLOADING 崩溃恢复。

较早的中间全量运行曾报告 523 项并有一项 S2 RMI 停服分类失败；随后已修复并在 S3 阶段最终报告 527 项全通过。该 527 项数字属于 S3 阶段，不与 S4-A 的回归结果混用。S3 的 RabbitMQ 和 Python 真实联调仍为 `NOT_VERIFIED`，因此 S3 尚不能宣称完整整合验收。

其余 H2 集成回归覆盖权限隔离、非法文件签名、任务与 Outbox 同事务、强制 Outbox 插入失败回滚、容量并发上限、Mock 成功/重试/永久失败/非法响应、终态重复投递、保留期清理及活动材料保护。Broker 不可用用关闭 Rabbit 集成的 H2 应用状态模拟 Outbox 不发布；此测试不证明 TCP 断连、Rabbit confirm、队列重连或 DLQ 行为。

## Windows 演示

脚本路径：`scripts/windows/s3-rabbitmq-demo.ps1`。

H2/Mock 只跑模式：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\s3-rabbitmq-demo.ps1 -Mode Mock
```

若已有可用 Docker Engine 且允许该用户运行容器，可选运行隔离 RabbitMQ 演示：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\s3-rabbitmq-demo.ps1 -Mode Rabbit
```

Rabbit 模式只创建脚本内随机命名的容器与命名数据卷，只将 AMQP 动态端口绑定到 `127.0.0.1`，不开放管理端口。它通过 test classpath 启动使用 H2 和种子账户的后端，使用虚构 PDF 字节上传和创建任务，注入一次 Mock 可恢复错误后等待成功；随后停止本脚本自己的 Broker，创建第二个任务以观察数据库 `202/PENDING` 受理，再启动原容器并等 Outbox 发布、worker 重试完成。退出时只停止该脚本的后端进程树并删除这次创建的容器和命名卷。该流程未在本轮运行，因为本机没有 Docker/RabbitMQ；静态脚本解析不等于运行验收。

Rabbit 镜像使用 Docker Official Image 的固定 tag `rabbitmq:4.3.7-alpine`；执行前可能需要联网拉取镜像。脚本不安装 Docker、不创建管理端口、不触碰其他容器，也不执行全局清理命令。

## 数据与服务边界

测试文件是脚本内存生成的合成 PDF 内容，不含真实个人资料。H2 每次使用随机内存库；Rabbit 演示使用随机命名临时卷。不得通过演示连接共享 MySQL。不要把 Mock 响应记录成真实识别结果。
