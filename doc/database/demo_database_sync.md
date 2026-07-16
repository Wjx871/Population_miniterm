# 演示数据库同步说明

本项目不提交 MySQL 数据库文件，也不提交账号、密码或 `start.local.env`。队友通过仓库中的初始化 SQL 与批量生成器，在各自电脑上创建**相同口径的独立演示库**。

以下示例使用 `population_miniterm_demo`；不要对 `population_miniterm` 或任何真实业务库执行这些操作。

## 1. 拉取包含演示数据与 OCR 的分支

```powershell
git fetch origin
git switch develop2
git pull --ff-only origin develop2
```

## 2. 配置本机数据库连接

在仓库根目录创建或调整本机 `start.local.env`（该文件已被 Git 忽略）：

```properties
DB_URL=jdbc:mysql://localhost:3306/population_miniterm_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=你的本机MySQL密码
SERVER_PORT=8080
```

不要把该文件、密码或 MySQL 数据目录提交到 Git。

## 3. 初始化基础结构

确认目标库不存在或为空后，在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/windows/init_database.ps1 -Mode Fresh -Database population_miniterm_demo
```

初始化脚本会拒绝非空数据库，避免误覆盖已有数据。若命令提示目标库非空，请更换库名；不要自行删除数据库。

## 4. 生成并导入大屏批量演示数据

生成固定口径的虚构数据：1,200 名人口、720 条户籍、480 条流动人口、350 张居住证、685 条业务申请/审批以及 30 天趋势数据。

```powershell
python scripts/data/generate_dashboard_demo_data.py --database population_miniterm_demo --output tmp/dashboard_demo_data.sql --people 1200
python scripts/data/create_dashboard_demo_material_files.py --output-dir data/uploads
```

第二条命令会创建 5 个可下载的虚构 PDF 演示材料；它们与生成 SQL 中的材料元数据一一对应，位于已被 Git 忽略的 `data/uploads`，不包含真实证件或业务数据。

导入前，仅在当前 PowerShell 会话设置 MySQL 密码；关闭终端后变量自动失效：

```powershell
$env:MYSQL_PWD = '你的本机MySQL密码'
Get-Content -Raw -Encoding utf8 tmp/dashboard_demo_data.sql | mysql --protocol=TCP --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 population_miniterm_demo
Remove-Item Env:MYSQL_PWD
```

`tmp/dashboard_demo_data.sql` 是可重新生成的临时文件，已被 Git 忽略。

### 已导入旧版演示数据时的补全

若已经导入过旧版生成器的数据，不需要重建数据库。生成并导入补全 SQL 即可：它只会修改 `DEMO-*` 虚构数据，补齐审批材料与轨迹，并将旧版待审批通用业务修正为可审批状态；可重复执行。

```powershell
python scripts/data/generate_dashboard_demo_data.py --database population_miniterm_demo --output tmp/dashboard_demo_data.sql --repair-output tmp/dashboard_demo_repair.sql --people 1200
Get-Content -Raw -Encoding utf8 tmp/dashboard_demo_repair.sql | mysql --protocol=TCP --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 population_miniterm_demo
```

## 5. 应用 OCR 数据库迁移

OCR 功能需要 `person_idcard_image` 表和权限。迁移文件默认指向课程库，因此必须在导入前改写为演示库目标：

```powershell
$env:MYSQL_PWD = '你的本机MySQL密码'
$target = 'population_miniterm_demo'
$migration = Get-Content -Raw -Encoding utf8 doc/database/migrations/V4_013_person_idcard_image.sql
$migration = [regex]::Replace($migration, '(?m)^USE\s+population_miniterm\s*;', "USE $target;", 1)
$migration | mysql --protocol=TCP --host=localhost --port=3306 --user=root --default-character-set=utf8mb4
Remove-Item Env:MYSQL_PWD
```

该迁移可重复执行；它会新增 OCR 影印本表和 `person:create-with-idcard` 权限。

## 6. 验证数据库

```powershell
powershell -ExecutionPolicy Bypass -File scripts/windows/init_database.ps1 -Mode Verify -Database population_miniterm_demo
```

成功时会输出 `[PASS] 数据库 Verify`。启动或重启后端后，前端切到数据大屏“真实模式”，应能看到 720 户籍人口、480 流动人口、350 有效居住证及完整趋势数据。

## 7. OCR 识别服务说明

上传身份证影印本、落库和“跳过 OCR”可在没有 OCR 子服务时使用。自动识别成功还需要可访问的 OCR HTTP 服务：

```properties
IDCARD_OCR_URL=http://127.0.0.1:8866
IDCARD_OCR_PATH=/recognize/idcard
```

若该服务未启动，上传接口会返回 `ocrStatus: FAILED`，但不会阻断新增人口流程。请勿将第三方 OCR 的密钥写入仓库。

## 常见问题

- **初始化拒绝非空数据库**：换用新的演示库名；不要用 `Fresh` 覆盖现有库。
- **大屏仍显示旧数据**：确认后端已重启，并检查 `start.local.env` 的 `DB_URL` 是否指向演示库。
- **OCR 状态为 FAILED**：检查 OCR 服务地址、端口和第三方识别服务凭据；数据库和上传接口本身仍可正常工作。
- **队友想要完全一致的数据**：始终使用同一分支、同一个 `--people 1200` 参数；生成器使用固定数据规则。
