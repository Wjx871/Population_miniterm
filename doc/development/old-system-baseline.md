# 旧系统基线与新旧对照演示

2026-11-01 将当前工作区（原 develop2 及本地启动、编码和单元测试改动）冻结为旧系统。唯一保留分支为 main，固定演示标签为 old-system-v1。后续开发请从该标签创建 codex/new-system，避免在 main 上继续开发。

## 仓库结构

- src/main：Java 17 / Spring Boot 后端与 MyBatis 映射。
- src/test：后端测试；pom.xml、mvnw.cmd：构建入口。
- frontend：Vue 3 / Vite 前端，包含单元测试与端到端测试。
- scripts/windows：Windows 启动、停止和数据库初始化工具。
- doc：接口、数据库初始化与迁移、部署、测试资料。
- docs：助手和集成说明；config：本地配置示例。
- data、logs、target、frontend/node_modules：运行数据、日志或构建依赖，不是可迁移的系统备份。

## 历史保留

清理前各远端分支头以 archive/2026-11-01/<原分支名> 标签保存；部分历史分支具有独有提交，不代表其功能已合入本基线。main 原历史通过仅保留当前系统文件的归档合并接入，旧系统文件以当前工作区为准。

本地完整 Git 备份及清理前引用清单位于 D:/population_miniterm-backups/2026-11-01/。临时数据库排错脚本留在本地，不纳入版本库。

## 后续开发与演示

创建开发分支：`git switch -c codex/new-system old-system-v1`。

并排演示时建议使用两个独立工作目录：

```powershell
git worktree add --detach ..\population-old-demo old-system-v1
# 已创建 codex/new-system 分支后执行：
git worktree add ..\population-new-demo codex/new-system
```

旧系统前端/后端可用 5180/8080，新系统用 5181/8081；两边分别复制 config/start.local.env.example 为 start.local.env，设置 FRONTEND_PORT、SERVER_PORT、DB_URL 和各自凭据。前端代理配置也应指向对应后端。使用两个独立数据库（例如 population_old_demo、population_new_demo）和独立上传/导出目录；按各版本部署说明初始化，使用相同的虚构演示数据和业务操作进行对照，避免新系统迁移改变旧系统数据库。

Git 标签保存代码，不包含现有 MySQL 数据、上传文件、本机凭据或外部 OCR 服务（默认 D:/ocr-stub）。正式演示前需另行备份数据库和上传材料，并在两个环境分别验证启动及业务流程。OCR 相关配置请参考启动脚本。
