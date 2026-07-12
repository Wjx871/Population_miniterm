@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul 2>&1
title 人口数据库管理系统 - 后端启动

REM ============================================================
REM  人口数据库管理系统（Population Miniterm）后端一键启动
REM  - 检查 Java 17+ / Maven Wrapper
REM  - 加载 start.local.env（不覆盖外部已设的环境变量）
REM  - 默认 MySQL: localhost:3306/population_miniterm
REM  - 默认端口: 8080（可通过 SERVER_PORT 修改）
REM ============================================================

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

echo.
echo ============================================================
echo   人口数据库管理系统 - 后端一键启动
echo ============================================================
echo   项目根目录 : %ROOT_DIR%
echo ============================================================
echo.

cd /d "%ROOT_DIR%"

REM ---------- 1. 检查 mvnw.cmd ----------
if not exist "%ROOT_DIR%\mvnw.cmd" (
    echo [错误] 未找到 mvnw.cmd，请确认脚本位于项目根目录。
    goto :fail
)

REM ---------- 2. 检查 Java ----------
where java >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 java。请安装 JDK 17+ 并加入 PATH。
    echo        下载: https://adoptium.net/
    goto :fail
)

REM 打印 java -version 第一行
for /f "tokens=*" %%v in ('java -version 2^>^&1') do (
    echo [信息] %%v
    goto :java_version_printed
)
:java_version_printed

REM ---------- 3. 校验 JDK 版本 >= 17 ----------
set "JAVA_MAJOR="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_RAW=%%v"
)

if defined JAVA_VER_RAW (
    REM 去掉引号（JAVA_VER_RAW 类似 "17.0.1" 或 "1.8.0_301"）
    set "JAVA_VER_CLEAN=!JAVA_VER_RAW:"=!"
    REM 取第一个点前的数字
    for /f "tokens=1 delims=." %%v in ("!JAVA_VER_CLEAN!") do set "JAVA_MAJOR=%%v"
    REM 旧版 JDK 格式 1.8，取第二段作为主版本号
    if "!JAVA_MAJOR!"=="1" (
        for /f "tokens=2 delims=." %%v in ("!JAVA_VER_CLEAN!") do set "JAVA_MAJOR=%%v"
    )
)

if not defined JAVA_MAJOR (
    echo [警告] 无法解析 JDK 版本，跳过版本校验。
) else if !JAVA_MAJOR! LSS 17 (
    echo [错误] JDK 版本过低: !JAVA_MAJOR!，需要 17 或更高。
    echo        请安装 JDK 17+，下载: https://adoptium.net/
    goto :fail
) else (
    echo [信息] JDK 主版本: !JAVA_MAJOR!  ^(^>= 17，符合要求^)
)

REM ---------- 4. 加载本地 env 文件（勿提交密码） ----------
REM 加载优先级：
REM   1) 当前进程已设置的变量（最高，不覆盖）
REM   2) start.local.env（推荐）
REM   3) .env.backend（备选）
REM   4) 脚本默认值（最低，后续设置）
if exist "%ROOT_DIR%\start.local.env" (
    echo [信息] 加载 start.local.env ...
    for /f "usebackq tokens=1,* delims== eol=#" %%a in ("%ROOT_DIR%\start.local.env") do (
        if not "%%a"=="" (
            if not defined %%a set "%%a=%%b"
        )
    )
) else if exist "%ROOT_DIR%\.env.backend" (
    echo [信息] 加载 .env.backend ...
    for /f "usebackq tokens=1,* delims== eol=#" %%a in ("%ROOT_DIR%\.env.backend") do (
        if not "%%a"=="" (
            if not defined %%a set "%%a=%%b"
        )
    )
) else (
    echo [提示] 未找到 start.local.env 或 .env.backend。
    echo        若 MySQL 有密码，请先:
    echo          copy start.local.env.example start.local.env
    echo          编辑 start.local.env 填写 DB_PASSWORD
    echo.
)

REM ---------- 5. 默认环境变量（仅设置未定义的变量） ----------
if not defined DB_USERNAME set "DB_USERNAME=root"
if not defined DB_PASSWORD set "DB_PASSWORD="
if not defined DB_URL set "DB_URL=jdbc:mysql://localhost:3306/population_miniterm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true"
if not defined SERVER_PORT set "SERVER_PORT=8080"
if not defined JWT_SECRET set "JWT_SECRET=local-development-jwt-secret-change-before-production-2026"
if not defined JWT_EXPIRE_MINUTES set "JWT_EXPIRE_MINUTES=120"

REM ---------- 6. 密码警告 ----------
if "!DB_PASSWORD!"=="" (
    echo [警告] DB_PASSWORD 为空。
    echo        若 MySQL root 有密码，后端将无法连接数据库。
    echo        请任选其一：
    echo          A. 创建 start.local.env  ^(示例见 start.local.env.example^)
    echo          B. 启动前执行: set DB_PASSWORD=你的密码
    echo.
)

REM ---------- 7. 输出配置信息（不输出密码和密钥） ----------
echo [信息] DB_USERNAME = !DB_USERNAME!
echo [信息] DB_URL      = !DB_URL!
echo [信息] SERVER_PORT = !SERVER_PORT!
echo [信息] JWT 过期    = !JWT_EXPIRE_MINUTES! 分钟
echo.
echo [信息] 后端地址: http://127.0.0.1:!SERVER_PORT!
echo [信息] 按 Ctrl+C 停止后端。
echo ============================================================
echo.

REM ---------- 8. 启动 Spring Boot ----------
call "%ROOT_DIR%\mvnw.cmd" spring-boot:run
set "EXIT_CODE=%errorlevel%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [错误] 后端退出，代码: %EXIT_CODE%
    echo        常见原因:
    echo          1. MySQL 未启动 / 密码错误
    echo          2. 端口 !SERVER_PORT! 被占用
    echo          3. 数据库未初始化 ^(执行 doc\database\population_miniterm.sql^)
    echo          4. JDK 版本不正确 ^(需要 17+^)
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
