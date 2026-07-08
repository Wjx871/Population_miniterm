@echo off
title PDMS Frontend Startup

cd /d "%~dp0frontend"

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
