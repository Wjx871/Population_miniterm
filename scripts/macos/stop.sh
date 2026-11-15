#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
set -a
source .env.local
set +a

stop_process() {
    local pid_file="$1" expected="$2" pid command
    [[ -f "$pid_file" ]] || return 0
    pid="$(cat "$pid_file")"
    if [[ "$pid" =~ ^[0-9]+$ ]]; then
        command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
        if [[ "$command" == *"$expected"* ]]; then
            kill "$pid"
            for ((i=0; i<30; i++)); do
                kill -0 "$pid" 2>/dev/null || break
                sleep 1
            done
            if kill -0 "$pid" 2>/dev/null; then
                echo "进程 $pid 尚未退出，保留 PID 文件，请检查日志。" >&2
                return 1
            fi
        fi
    fi
    rm -f "$pid_file"
}

stop_process tmp/frontend.pid "$ROOT/frontend/node_modules/vite/bin/vite.js"
stop_process tmp/backend.pid "$ROOT/target/population-miniterm-1.0.0.jar"
stop_process tmp/yixun-tunnel.pid '127.0.0.1:18091:127.0.0.1:8091'
# 只有明确指定 --all 才停止项目独立数据库。
if [[ "${1:-}" == '--all' ]]; then
    if [[ -f tmp/mysql.pid ]]; then
        pid="$(cat tmp/mysql.pid)"
        command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
        if [[ "$command" == *"--datadir=$ROOT/tmp/mysql"* ]]; then
            "$MYSQL_HOME/bin/mysqladmin" --defaults-file="$ROOT/tmp/mysql-client.cnf" shutdown
        fi
    fi
fi
echo '项目服务已停止，数据库文件保留。'
