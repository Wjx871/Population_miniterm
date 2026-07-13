@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1
title 人口数据库管理系统 - 一键启动前后端

REM ============================================================
REM  人口数据库管理系统（Population Miniterm）前后端一键启动
REM  - 加载项目根目录 start.local.env 统一配置端口
REM  - 在独立窗口启动 Spring Boot 后端
REM  - 等待后端就绪后启动 Vue/Vite 前端
REM  - 后端端口由 SERVER_PORT 控制，前端端口由 FRONTEND_PORT 控制
REM ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT_DIR=%%~fI"
set "MAX_WAIT_SECONDS=180"

REM ---------- 加载本地 env 文件（与其他启动脚本使用同一套配置） ----------
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

REM ---------- 默认端口（仅设置未定义的变量） ----------
if not defined SERVER_PORT set "SERVER_PORT=8080"
if not defined FRONTEND_PORT set "FRONTEND_PORT=5180"

REM env 加载完成后再开启延迟展开，避免配置值中的 ! 被错误解析
setlocal EnableDelayedExpansion

set "BACKEND_URL=http://127.0.0.1:!SERVER_PORT!"
set "FRONTEND_URL=http://localhost:!FRONTEND_PORT!"

echo.
echo ============================================================
echo   人口数据库管理系统 - 一键启动前后端
echo ============================================================
echo   项目根目录 : %ROOT_DIR%
echo   后端       : !BACKEND_URL!
echo   前端       : !FRONTEND_URL!
echo   等待超时   : %MAX_WAIT_SECONDS% 秒
echo ============================================================
echo.

REM ---------- 1. 检查必要文件 ----------
if not exist "%SCRIPT_DIR%start_backend.bat" (
    echo [错误] 未找到 scripts\windows\start_backend.bat
    goto :fail
)
if not exist "%SCRIPT_DIR%start_frontend.bat" (
    echo [错误] 未找到 scripts\windows\start_frontend.bat
    goto :fail
)
if not exist "%ROOT_DIR%\mvnw.cmd" (
    echo [错误] 未找到 mvnw.cmd
    goto :fail
)
if not exist "%ROOT_DIR%\frontend\package.json" (
    echo [错误] 未找到 frontend\package.json
    goto :fail
)

REM ---------- 提示配置密码 ----------
if not exist "%ROOT_DIR%\start.local.env" (
    echo [提示] 未找到 start.local.env。
    echo        若 MySQL 有密码，请先:
    echo          copy config\start.local.env.example start.local.env
    echo          编辑 start.local.env 填写 DB_PASSWORD
    echo.
)

REM ---------- 2. 启动后端（新窗口） ----------
echo [1/3] 正在新窗口启动后端...
start "PDMS-Backend" /D "%ROOT_DIR%" cmd /k call "%SCRIPT_DIR%start_backend.bat"
if errorlevel 1 (
    echo [错误] 无法打开后端窗口。
    goto :fail
)

REM ---------- 3. 等待后端就绪 ----------
echo [2/3] 等待后端就绪（最长 %MAX_WAIT_SECONDS% 秒）...
set /a waited=0
set "BACKEND_READY=0"

:wait_loop
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%check_backend_health.ps1" -BackendBaseUrl "!BACKEND_URL!" -TimeoutSeconds 2 -Quiet >nul 2>&1
if not errorlevel 1 (
    set "BACKEND_READY=1"
    goto :backend_ready
)

set /a waited+=2
if !waited! GEQ %MAX_WAIT_SECONDS% goto :backend_timeout

REM 每 10 秒打印一次进度
set /a mod=waited %% 10
if !mod! EQU 0 (
    echo        ... 已等待 !waited! 秒
)

timeout /t 2 /nobreak >nul
goto :wait_loop

:backend_timeout
echo.
echo [警告] %MAX_WAIT_SECONDS% 秒内后端仍未就绪。
echo        请查看「PDMS-Backend」窗口中的错误日志。
echo        常见原因:
echo          1. MySQL 未启动 / 密码错误
echo          2. 端口 !SERVER_PORT! 被占用
echo          3. 数据库未初始化
echo          4. JDK 版本不正确
echo.
choice /C YN /M "是否仍继续启动前端"
if errorlevel 2 (
    echo [信息] 已取消。后端窗口仍在运行时可手动关闭。
    exit /b 1
)
goto :start_frontend

:backend_ready
echo [信息] 后端已就绪: !BACKEND_URL! （约 !waited! 秒）

REM ---------- 4. 启动前端（当前窗口） ----------
:start_frontend
echo.
echo [3/3] 正在启动前端（当前窗口）...
echo.
echo ============================================================
echo   启动完成后请访问:
echo     !FRONTEND_URL!
echo   演示账号密码均为 123456:
echo     admin / viewer / population / household / approver
echo   关闭本窗口将停止前端；后端在独立窗口，需单独关闭。
echo ============================================================
echo.

REM 已由本脚本等待过后端，前端脚本跳过二次确认
set "SKIP_BACKEND_PROMPT=1"
call "%SCRIPT_DIR%start_frontend.bat"
set "EXIT_CODE=%errorlevel%"
exit /b %EXIT_CODE%

:fail
echo.
pause
exit /b 1
