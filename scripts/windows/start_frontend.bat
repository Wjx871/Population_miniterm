@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1
title 人口数据库管理系统 - 前端启动

REM ============================================================
REM  人口数据库管理系统（Population Miniterm）前端一键启动
REM  - 自动检查 Node 18+ / npm
REM  - 缺失依赖时优先使用 npm ci（仓库已有 package-lock.json）
REM  - 缺失 .env 时从 .env.example 生成
REM  - 加载项目根目录 start.local.env 统一端口配置
REM  - 探测后端，给出明确提示
REM  - 启动 Vite 开发服务器
REM ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT_DIR=%%~fI"
set "FRONTEND_DIR=%ROOT_DIR%\frontend"

echo.
echo ============================================================
echo   人口数据库管理系统 - 前端一键启动
echo ============================================================
echo   项目根目录 : %ROOT_DIR%
echo   前端目录   : %FRONTEND_DIR%
echo ============================================================
echo.

REM ---------- 1. 检查前端目录 ----------
if not exist "%FRONTEND_DIR%\package.json" (
    echo [错误] 未找到前端 package.json:
    echo        %FRONTEND_DIR%\package.json
    echo        请确认仓库内容完整。
    goto :fail
)

if not exist "%FRONTEND_DIR%\vite.config.js" (
    echo [错误] 未找到 vite.config.js，前端工程可能不完整。
    goto :fail
)

REM ---------- 2. 加载本地 env 文件（与后端启动脚本使用同一套配置） ----------
REM 注意：此处不开启 EnableDelayedExpansion，避免配置值中的 ! 被错误解析
if exist "%ROOT_DIR%\start.local.env" (
    for /f "usebackq tokens=1,* delims== eol=#" %%a in ("%ROOT_DIR%\start.local.env") do (
        if not "%%a"=="" (
            if not defined %%a set "%%a=%%b"
        )
    )
) else if exist "%ROOT_DIR%\.env.backend" (
    for /f "usebackq tokens=1,* delims== eol=#" %%a in ("%ROOT_DIR%\.env.backend") do (
        if not "%%a"=="" (
            if not defined %%a set "%%a=%%b"
        )
    )
)

if not defined SERVER_PORT set "SERVER_PORT=8080"
if not defined FRONTEND_PORT set "FRONTEND_PORT=5180"

REM env 加载完成后再开启延迟展开，避免配置值中的 ! 被错误解析
setlocal EnableDelayedExpansion

REM ---------- 3. 检查 Node / npm ----------
where node >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 node。请安装 Node.js 18+（建议 20 LTS），并重新打开终端。
    echo        下载: https://nodejs.org/
    goto :fail
)

where npm >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 npm。请重新安装 Node.js 并确保 npm 已加入 PATH。
    goto :fail
)

for /f "tokens=*" %%v in ('node -v 2^>nul') do set "NODE_VER=%%v"
for /f "tokens=*" %%v in ('npm -v 2^>nul') do set "NPM_VER=%%v"
echo [信息] Node 版本 : !NODE_VER!
echo [信息] npm  版本 : !NPM_VER!

REM 校验 Node 主版本 >= 18
set "NODE_MAJOR="
if defined NODE_VER (
    for /f "tokens=1 delims=v." %%a in ("!NODE_VER!") do set "NODE_MAJOR=%%a"
)

if defined NODE_MAJOR (
    if !NODE_MAJOR! LSS 18 (
        echo [错误] Node 版本过低: !NODE_VER!，需要 18+（建议 20 LTS）。
        echo        下载: https://nodejs.org/
        goto :fail
    )
) else (
    echo [警告] 无法解析 Node 版本，跳过版本校验。
)

REM ---------- 4. 设置 Vite 环境变量（由 vite.config.js 读取） ----------
set "BACKEND_URL=http://127.0.0.1:!SERVER_PORT!"
set "FRONTEND_URL=http://localhost:!FRONTEND_PORT!"
set "VITE_DEV_PORT=!FRONTEND_PORT!"
set "VITE_BACKEND_TARGET=http://127.0.0.1:!SERVER_PORT!"

REM ---------- 5. 检查本地 API 配置（不创建或覆盖 .env） ----------
cd /d "%FRONTEND_DIR%"
if exist ".env" (
    for /f "usebackq tokens=1,* delims== eol=#" %%a in (".env") do (
        if /I "%%a"=="VITE_API_BASE_URL" if not defined VITE_API_BASE_URL set "VITE_API_BASE_URL=%%b"
    )
)
if not defined VITE_API_BASE_URL set "VITE_API_BASE_URL=/api"
echo [信息] API 基址   : !VITE_API_BASE_URL!
echo [信息] 代理目标   : !VITE_BACKEND_TARGET!
if not "!VITE_API_BASE_URL!"=="/api" (
    echo [警告] 当前前端将绕过 Vite /api 代理，请确认这是有意配置。
)

REM ---------- 6. 安装依赖 ----------
if not exist "node_modules\" (
    if exist "package-lock.json" (
        echo [信息] node_modules 不存在，使用 npm ci 安装依赖（基于 package-lock.json）...
        call npm ci
        if errorlevel 1 (
            echo [错误] npm ci 失败。请检查 package-lock.json 是否完整。
            echo        如需重新生成锁定文件，可手动执行: npm install
            goto :fail
        )
    ) else (
        echo [信息] node_modules 不存在且无 package-lock.json，使用 npm install...
        call npm install
        if errorlevel 1 (
            echo [错误] npm install 失败。
            goto :fail
        )
    )
    echo [信息] 依赖安装完成。
) else (
    echo [信息] 前端依赖目录已存在 ^(node_modules^)。
    echo [信息] 若依赖异常，可手动执行: cd frontend ^&^& npm ci
)

REM ---------- 7. 探测后端 ----------
echo.
echo [信息] 正在探测后端 !BACKEND_URL! ...
set "BACKEND_OK=0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%check_backend_health.ps1" -BackendBaseUrl "!BACKEND_URL!" -TimeoutSeconds 3 -Quiet >nul 2>&1
if not errorlevel 1 set "BACKEND_OK=1"

if "!BACKEND_OK!"=="1" (
    echo [信息] 后端已就绪: !BACKEND_URL!
) else (
    echo [警告] 未检测到后端 !BACKEND_URL!
    echo        前端可通过 Vite 代理访问 /api，但登录和接口调用会失败。
    echo.
    echo        请运行:
    echo          scripts\windows\start_backend.bat
    echo        或从项目根目录双击:
    echo          start.bat
    echo.
    if /I "!SKIP_BACKEND_PROMPT!"=="1" (
        echo [信息] 已设置 SKIP_BACKEND_PROMPT=1，继续启动前端...
    ) else (
        choice /C YN /M "后端未就绪，是否仍要继续启动前端"
        if errorlevel 2 (
            echo [信息] 已取消启动。
            exit /b 1
        )
        if errorlevel 1 (
            echo [信息] 继续仅启动前端...
        )
    )
)

REM ---------- 8. 启动说明 ----------
echo.
echo ============================================================
echo   启动信息
echo ============================================================
echo   前端地址 : !FRONTEND_URL!
echo   API 代理 : /api  -^>  !BACKEND_URL!
echo   工作台   : !FRONTEND_URL!/home
echo   综合查询 : !FRONTEND_URL!/queries/comprehensive
echo   数据大屏 : !FRONTEND_URL!/statistics/dashboard
echo.
echo   演示账号 ^(密码均为 123456^):
echo     admin      SYSTEM_ADMIN
echo     viewer     QUERY_VIEWER
echo     population POPULATION_MANAGER
echo     household  HOUSEHOLD_MANAGER
echo     approver   APPROVER
echo.
echo   按 Ctrl+C 可停止前端开发服务器。
echo   关闭本窗口仅停止前端；后端需单独关闭。
echo ============================================================
echo.

REM ---------- 9. 启动 Vite ----------
echo [信息] 正在启动 Vite 开发服务器...
echo.
call npm run dev
set "EXIT_CODE=%errorlevel%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [错误] 前端进程退出，代码: %EXIT_CODE%
    echo        常见原因:
    echo          1. 端口 !FRONTEND_PORT! 被占用
    echo          2. 依赖损坏，可执行: npm ci
    echo          3. Node 版本过低
    goto :fail_with_code
)

exit /b 0

:fail
echo.
pause
exit /b 1

:fail_with_code
echo.
pause
exit /b %EXIT_CODE%
