@echo off
setlocal
title PDMS Frontend

set "FRONTEND_DIR=%~dp0frontend"

if not exist "%FRONTEND_DIR%\package.json" (
    echo [Error] Frontend directory or package.json was not found:
    echo         %FRONTEND_DIR%
    pause
    exit /b 1
)

where npm >nul 2>&1
if errorlevel 1 (
    echo [Error] npm was not found. Install Node.js 20 or later and reopen this terminal.
    pause
    exit /b 1
)

cd /d "%FRONTEND_DIR%"

if not exist "node_modules" (
    echo [Info] node_modules is missing. Installing frontend dependencies...
    call npm install
    if errorlevel 1 (
        echo [Error] Failed to install frontend dependencies.
        pause
        exit /b 1
    )
) else (
    echo [Info] Frontend dependencies are available.
)

echo.
echo [Info] Starting the Vite frontend only...
echo [Info] The backend is not started or stopped by this script.
echo [Info] Press Ctrl+C to stop the frontend server.
echo.
call npm run dev
set "EXIT_CODE=%errorlevel%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [Error] The frontend server exited with code %EXIT_CODE%.
    pause
)

exit /b %EXIT_CODE%
