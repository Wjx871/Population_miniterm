# OCR 服务

根据用户提供的 `D:\ocr-stub\app.py` 整理。保留百度云身份证正面识别协议，原目录和启动脚本未修改；本目录不包含其虚拟环境、凭据、日志或测试材料。

## 当前运行契约

- Python 容器使用 3.11；直接依赖版本沿用原目录的 `requirements.txt`，尚未形成全量传递依赖锁文件。
- `OCR_AK`、`OCR_SK` 从运行环境注入，不写入镜像。
- `OCR_PORT` 默认 8866，`OCR_TIMEOUT` 默认 30 秒，鉴权请求超时为 15 秒。
- `GET /health` 返回进程状态、凭据是否已配置和引擎标识；不访问云服务，不能作为识别成功证据。
- `POST /recognize/idcard`：multipart 字段 `file` 和 `side=front`，返回 `{"result": {...}}`，字段保持 Java 客户端兼容。
- 本轮只支持身份证正面；户口簿、申请表和多模态材料服务仍属后续功能。
- 400 为输入不合法，413 为图片超过 10 MiB，422 为非正常身份证正面，503 为未配凭据，504 为上游超时，502 为其他上游失败。
- `confidence` 沿用旧适配器根据 image_status 设置的启发值，并增加 `_confidence_source` 标记，不代表模型校准后的正确概率。
- 服务调用云端 API，无本地模型、向量索引或 GPU 需求。需要能访问百度接口的外网连接与有效配额。

## 本轮优化

同步云调用在 FastAPI 线程池运行；复用带锁的 token 缓存；删除重复归一化；校验日期是否合法；日志不输出姓名、证件号、原始 OCR 内容或鉴权响应；错误不回传带 token 的请求 URL；透传安全的 `X-Trace-Id`。未保留原先暴露 token 前缀的 `/diagnose` 接口。

健康状态不表示凭据有效或外网可达，真实识别需在部署后用虚构测试材料单独验收。

## 启动和测试

容器启动见 `doc/deployment/data-ops-execution-checklist.md`。若独立运行，安装依赖后设置环境变量并执行 `python app.py`。

在本目录执行 `python -m unittest -v test_app` 可运行离线契约测试，云调用全部由 mock 替代。测试依赖运行依赖和 Python 标准库，不消耗 OCR 配额。

超时链路：容器内上游请求分别使用 15/30 秒，Java OCR 客户端默认 60 秒，前端身份证上传等待 90 秒，Nginx API 读取超时 95 秒。若本机已显式设置 IDCARD_OCR_TIMEOUT_MS，请核对其覆盖值。requests 超时并非严格的总墙钟截止时间，高负载排队仍需运行压测验证。
