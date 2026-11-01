@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1
title 人口数据库管理系统 - 一键启动前后端

REM ============================================================
REM  人口数据库管理系统（Population Miniterm）一键启动
REM  - 加载项目根目录 start.local.env 统一配置端口
REM  - 在独立窗口启动 OCR 子服务（百度智能云 身份证识别）
REM  - 在独立窗口启动 Spring Boot 后端
REM  - 等待后端就绪后启动 Vue/Vite 前端
REM  - 后端端口由 SERVER_PORT 控制，前端端口由 FRONTEND_PORT 控制
REM    OCR 端口由 OCR_PORT 控制，默认 8866
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
if not defined OCR_PORT set "OCR_PORT=8866"
if not defined OCR_STUB_DIR set "OCR_STUB_DIR=D:\ocr-stub"
if not defined OCR_START_BAT set "OCR_START_BAT=%OCR_STUB_DIR%\start.bat"

REM env 加载完成后再开启延迟展开，避免配置值中的 ! 被错误解析
setlocal EnableDelayedExpansion

REM ---------- 选择后端端口，并将同一端口传给后端和前端 ----------
set "SERVER_PORT_BASE=!SERVER_PORT!"
set "SERVER_PORT_SELECTED="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%resolve_backend_port.ps1" -BasePort !SERVER_PORT_BASE! -Mode SelectAvailable`) do set "SERVER_PORT_SELECTED=%%P"
if not defined SERVER_PORT_SELECTED (
    echo [错误] 从端口 !SERVER_PORT_BASE! 开始的 5 个候选端口都不可用。
    echo        请释放端口，或在 start.local.env 中修改 SERVER_PORT 起始端口。
    goto :fail
)
set "SERVER_PORT=!SERVER_PORT_SELECTED!"
set "PDMS_BACKEND_PORT_PRESELECTED=1"
if not "!SERVER_PORT!"=="!SERVER_PORT_BASE!" echo [信息] 起始端口被占用，已自动切换到 !SERVER_PORT!。

set "BACKEND_URL=http://127.0.0.1:!SERVER_PORT!"
set "FRONTEND_URL=http://localhost:!FRONTEND_PORT!"
set "OCR_HEALTH_URL=http://127.0.0.1:!OCR_PORT!/health"
set "FRONTEND_PORT_BASE=!FRONTEND_PORT!"

echo.
echo ============================================================
echo   人口数据库管理系统 - 一键启动前后端 + OCR
echo ============================================================
echo   项目根目录 : %ROOT_DIR%
echo   后端       : !BACKEND_URL!
echo   前端候选起点: !FRONTEND_URL!（最多轮询 5 个端口）
echo   OCR 健康检查: !OCR_HEALTH_URL!
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
if not exist "%SCRIPT_DIR%start_ocr.bat" (
    echo [错误] 未找到 scripts\windows\start_ocr.bat
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

REM ---------- 2. 启动 OCR 子服务（独立窗口） ----------
if not exist "!OCR_STUB_DIR!\." (
    echo [警告] 未找到 OCR 工作目录: !OCR_STUB_DIR!
    echo        OCR 是可选子服务，本次跳过并继续启动后端与前端。
    goto :skip_ocr
)
if not exist "!OCR_START_BAT!" (
    echo [警告] 未找到 OCR 启动脚本: !OCR_START_BAT!
    echo        OCR 是可选子服务，本次跳过并继续启动后端与前端。
    goto :skip_ocr
)

echo [1/4] 正在新窗口启动 OCR 子服务...
start "PDMS-OCR" /D "%ROOT_DIR%" cmd /k call "%SCRIPT_DIR%start_ocr.bat"
if errorlevel 1 (
    echo [警告] 无法打开 OCR 窗口，将跳过 OCR 启动。
    goto :skip_ocr
)

echo [信息] 等待 OCR 健康检查 ^(最长 60 秒^)...
set /a ocr_waited=0

:ocr_wait_loop
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { $r = Invoke-WebRequest -Uri '!OCR_HEALTH_URL!' -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 2 }" >nul 2>&1
if not errorlevel 1 (
    echo [信息] OCR 子服务已就绪: !OCR_HEALTH_URL! ^(约 !ocr_waited! 秒^)
    goto :skip_ocr
)

set /a ocr_waited+=3
if !ocr_waited! GEQ 60 (
    echo.
    echo [警告] 60 秒内 OCR 子服务未就绪。
    echo        请查看「PDMS-OCR」窗口中的启动日志。
    echo        后端将继续启动，但身份证识别会回退为 FAILED。
    goto :skip_ocr
)

set /a ocr_mod=ocr_waited %% 6
if !ocr_mod! EQU 0 (
    echo        ... 已等待 !ocr_waited! 秒
)
timeout /t 3 /nobreak >nul
goto :ocr_wait_loop

:skip_ocr
echo.

REM OCR 启动可能等待较久，启动后端前重新选一次，缩短端口被抢占的窗口。
set "SERVER_PORT_SELECTED="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%resolve_backend_port.ps1" -BasePort !SERVER_PORT_BASE! -Mode SelectAvailable`) do set "SERVER_PORT_SELECTED=%%P"
if not defined SERVER_PORT_SELECTED (
    echo [错误] 从端口 !SERVER_PORT_BASE! 开始的 5 个候选端口都不可用。
    echo        请释放端口，或在 start.local.env 中修改 SERVER_PORT 起始端口。
    goto :fail
)
set "SERVER_PORT=!SERVER_PORT_SELECTED!"
set "BACKEND_URL=http://127.0.0.1:!SERVER_PORT!"
set "PDMS_BACKEND_PORT_PRESELECTED=1"
if not "!SERVER_PORT!"=="!SERVER_PORT_BASE!" echo [信息] 起始端口被占用，已自动切换到 !SERVER_PORT!。

REM ---------- 3. 启动后端（新窗口） ----------
echo [2/4] 正在新窗口启动后端...
start "PDMS-Backend" /D "%ROOT_DIR%" cmd /k call "%SCRIPT_DIR%start_backend.bat"
if errorlevel 1 (
    echo [错误] 无法打开后端窗口。
    goto :fail
)

REM ---------- 4. 等待后端就绪 ----------
echo [3/4] 等待后端就绪（最长 %MAX_WAIT_SECONDS% 秒）...
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

REM 后端等待期间前端端口可能发生变化，启动前再选一次并传给 Vite。
set "FRONTEND_PORT_SELECTED="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%resolve_frontend_port.ps1" -BasePort !FRONTEND_PORT_BASE!`) do set "FRONTEND_PORT_SELECTED=%%P"
if not defined FRONTEND_PORT_SELECTED (
    echo [错误] 从端口 !FRONTEND_PORT_BASE! 开始的 5 个候选前端端口都不可用。
    echo        请释放端口，或在 start.local.env 中修改 FRONTEND_PORT 起始端口。
    goto :fail
)
set "FRONTEND_PORT=!FRONTEND_PORT_SELECTED!"
set "FRONTEND_URL=http://localhost:!FRONTEND_PORT!"
set "PDMS_FRONTEND_PORT_PRESELECTED=1"
if not "!FRONTEND_PORT!"=="!FRONTEND_PORT_BASE!" echo [信息] 前端起始端口被占用，已自动切换到 !FRONTEND_PORT!。

REM ---------- 5. 启动前端（当前窗口） ----------
:start_frontend
echo.
echo [4/4] 正在启动前端（当前窗口）...
echo.
echo ============================================================
echo   启动完成后请访问:
echo     !FRONTEND_URL!
echo   演示账号密码均为 123456:
echo     admin / viewer / population / household / approver
echo   关闭本窗口将停止前端；后端/OCR 在独立窗口，需单独关闭。
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
