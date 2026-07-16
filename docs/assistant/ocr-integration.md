# 身份证 OCR 接入说明

PC 端“政策办理知识助手”提供身份证正面图片识别入口。前端图片传至 `POST /api/assistant/policy/ocr/id-card`，Spring Boot 校验 JPEG/PNG 和 5MB 上限后转发给独立 OCR 服务（默认 `http://127.0.0.1:8866/recognize/idcard`）。

后端不保存上传图片或识别结果；返回给浏览器的姓名、身份证号、出生日期和户籍地址均已脱敏，且标记为“需要用户确认”。不会自动写入人口数据库、创建申请或改变审批状态。

## 本机启动

从队友的 OCR 服务目录启动服务，并在**当前 PowerShell 会话**设置凭据（不要写入 `.env`、项目配置或仓库）：

```powershell
$env:OCR_AK = '<your-api-key>'
$env:OCR_SK = '<your-secret-key>'
./start.bat
```

启动后访问 `http://127.0.0.1:8866/health`。若部署地址不同，设置 `POLICY_OCR_BASE_URL`。

## 上线前修正

队友提供的 Stub 当前日志会输出完整姓名和身份证号。演示前必须删除该日志字段，或至少改为脱敏输出；同时不要提交 Stub 的 `.venv`、`login.json`、`server.log` 或任何凭据。
