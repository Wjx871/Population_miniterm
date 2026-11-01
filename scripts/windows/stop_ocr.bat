@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1
title 人口数据库管理系统 - OCR 子服务停止

REM 关闭由 PDMS-OCR 标题标识的 OCR 进程；端口 !OCR_PORT! 默认 8866

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT_DIR=%%~fI"

if exist "%ROOT_DIR%\start.local.env" (
    for /f "usebackq tokens=1,* delims== eol=#" %%a in ("%ROOT_DIR%\start.local.env") do (
        if not "%%a"=="" (
            if not defined %%a set "%%a=%%b"
        )
    )
)
if not defined OCR_PORT set "OCR_PORT=8866"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Get-Process cmd -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq '人口数据库管理系统 - OCR 子服务启动' } | ForEach-Object { Stop-Process -Id $_.Id -Force }"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Get-NetTCPConnection -LocalPort !OCR_PORT! -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue | Stop-Process -Id $_.Id -Force }"

echo [stop-ocr] 已尝试关闭 OCR 子服务端口 !OCR_PORT!。
exit /b 0