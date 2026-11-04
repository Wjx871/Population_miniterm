# S3 演示与验收记录

## 当前实际验证

本轮环境未发现 Docker、Podman、RabbitMQ CLI/Server，也未发现 AMQP/管理端口监听。因此没有启动或安装 Broker，没有修改系统配置。结论：H2/Mock 已验证；真实 RabbitMQ 发布确认、真实消费、DLQ、Broker 停止/恢复均为 `NOT_VERIFIED`。Python OCR/RAG 真实联调同样 `NOT_VERIFIED`，Mock 不作为其替代证据。

| 检查 | 结果 |
|---|---|
| Java 21 运行环境，项目 `--release 17` 编译 | 通过 |
| 定向 H2/Mock（集成、响应校验、default-off 启动） | 18 tests，0 failures/errors/skips |
| 完整 `mvnw.cmd test` | 517 tests，0 failures/errors/skips；包含本轮与既有业务测试 |
| Windows 演示脚本 `-Mode Mock` | 18 tests，0 failures/errors/skips |
| PowerShell 脚本语法解析 | 通过 |
| MySQL 迁移执行 | 未执行；仅静态审查，未访问共享数据库 |
| RabbitMQ 真实生产/消费及 broker 故障恢复 | `NOT_VERIFIED` |
| Python OCR/RAG | `NOT_VERIFIED`；当前客户端为 `mode=MOCK` |
| 审计账本 #3 | 保持 `OPEN` |

H2 集成回归覆盖用户/权限隔离、同键幂等与冲突、并发上传竞争、非法文件签名、上传失败补偿、任务与 Outbox 同事务、强制 Outbox 插入失败回滚、容量并发上限、Mock 成功/重试/永久失败/非法响应、终态重复投递、保留期清理及活动材料保护。Broker 不可用用关闭 Rabbit 集成的 H2 应用状态模拟 Outbox 不发布；此测试不证明 TCP 断连、Rabbit confirm、队列重连或 DLQ 行为。

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
