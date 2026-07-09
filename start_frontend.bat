@echo off
title PDMS Startup (Frontend & Backend)

cd /d "%~dp0"

echo [Info] Checking port 8080 (Backend)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080') do (
    if not "%%a"=="" (
        echo [Info] Port 8080 is in use by process PID %%a. Terminating...
        taskkill /F /PID %%a >nul 2>&1
    )
)

echo [Info] Checking port 5180 (Frontend)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5180') do (
    if not "%%a"=="" (
        echo [Info] Port 5180 is in use by process PID %%a. Terminating...
        taskkill /F /PID %%a >nul 2>&1
    )
)

echo [Info] Starting Spring Boot Backend in a new window...
start "PDMS Backend" cmd /k ".\mvnw spring-boot:run"

echo [Info] Waiting 10 seconds for Backend to initialize...
timeout /T 10 /NOBREAK >nul

cd frontend

echo [Info] Checking and installing frontend dependencies...
call npm install
if %errorlevel% neq 0 (
    echo [Error] Failed to install dependencies!
    pause
    exit /b %errorlevel%
)
echo [Info] Dependencies checked/installed successfully!
echo.

echo [Info] Starting Vite Dev Server...
echo [Info] Note: The backend is running in a separate pop-up window. Close it when you want to stop the backend.
echo Press Ctrl+C in this window to stop the frontend.
echo.
call npm run dev

pause
