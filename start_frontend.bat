@echo off
title PDMS Frontend Startup

cd /d "%~dp0"
cd frontend

echo [Info] Checking port 5180...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5180') do (
    if not "%%a"=="" (
        echo [Info] Port 5180 is in use by process PID %%a. Terminating...
        taskkill /F /PID %%a >nul 2>&1
    )
)

if not exist node_modules (
    echo [Info] node_modules not found, installing dependencies...
    call npm install
    if %errorlevel% neq 0 (
        echo [Error] Failed to install dependencies!
        pause
        exit /b %errorlevel%
    )
    echo [Info] Dependencies installed successfully!
    echo.
) else (
    echo [Info] Dependencies are ready.
)

echo [Info] Starting Vite Dev Server...
echo Press Ctrl+C to stop.
echo.
call npm run dev

pause
