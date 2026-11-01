@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1
title 人口数据库管理系统 - OCR 子服务启动

REM ============================================================
REM  人口数据库管理系统 - OCR 子服务（百度智能云 身份证识别）启动
REM  - 加载项目根目录 start.local.env 中的 OCR_AK / OCR_SK 等配置
REM  - 默认调用 D:\ocr-stub\start.bat；可通过 OCR_STUB_DIR / OCR_START_BAT 自定义
REM  - OCR 进程独立窗口运行；该窗口关闭即停止服务
REM ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT_DIR=%%~fI"

REM 加载 start.local.env（已设置的变量不覆盖）
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

setlocal EnableDelayedExpansion

if not defined OCR_STUB_DIR set "OCR_STUB_DIR=D:\ocr-stub"
if not defined OCR_PORT set "OCR_PORT=8866"
if not defined OCR_TIMEOUT set "OCR_TIMEOUT=30"
if not defined OCR_START_BAT set "OCR_START_BAT=!OCR_STUB_DIR!\start.bat"

echo.
echo ============================================================
echo   人口数据库管理系统 - OCR 子服务启动
echo ============================================================
echo   OCR 工作目录 : !OCR_STUB_DIR!
echo   启动脚本     : !OCR_START_BAT!
echo   监听端口     : !OCR_PORT!
echo   OCR_AK       : !OCR_AK!
echo   OCR_SK       : ******** ^(已设置^)
echo ============================================================
echo.

if not exist "!OCR_START_BAT!" (
    echo [错误] 未找到 OCR 启动脚本: !OCR_START_BAT!
    echo        请确认 OCR_STUB_DIR 指向包含 start.bat 的目录。
    goto :fail
)

if "!OCR_AK!"=="" (
    echo [警告] OCR_AK 未配置，OCR 调用百度接口时会失败。
)
if "!OCR_SK!"=="" (
    echo [警告] OCR_SK 未配置，OCR 调用百度接口时会失败。
)

echo [信息] 正在独立窗口启动 OCR 子服务...
start "PDMS-OCR" /D "!OCR_STUB_DIR!" cmd /k call "!OCR_START_BAT!"
if errorlevel 1 (
    echo [错误] 无法打开 OCR 窗口。
    goto :fail
)

REM 等待 OCR 端口可用
echo [信息] 等待 OCR 健康检查 ^(最长 60 秒^)...
set /a ocr_waited=0

:ocr_wait_loop
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:!OCR_PORT!/health' -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 2 }" >nul 2>&1
if not errorlevel 1 (
    echo [信息] OCR 子服务已就绪: http://127.0.0.1:!OCR_PORT!/health ^(约 !ocr_waited! 秒^)
    exit /b 0
)

set /a ocr_waited+=3
if !ocr_waited! GEQ 60 (
    echo.
    echo [警告] 60 秒内 OCR 端口仍未就绪。
    echo        请查看「PDMS-OCR」窗口中的启动日志。
    echo        常见原因:
    echo          1. Python venv 未安装或依赖缺失
    echo          2. 端口 !OCR_PORT! 被占用
    echo          3. OCR_AK / OCR_SK 配置错误
    exit /b 1
)

set /a ocr_mod=ocr_waited %% 6
if !ocr_mod! EQU 0 (
    echo        ... 已等待 !ocr_waited! 秒
)
timeout /t 3 /nobreak >nul
goto :ocr_wait_loop

:fail
echo.
pause
exit /b 1