#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
if [[ ! -f .env.local ]]; then
    echo '缺少 .env.local，请按 doc/deployment/macos-local.md 配置本机环境。' >&2
    exit 1
fi
set -a
source .env.local
set +a
export PATH="$JAVA_HOME/bin:$MYSQL_HOME/bin:$PATH"
mkdir -p tmp logs data/uploads data/exports

if [[ "${POLICY_RAG_ENABLED:-false}" == 'true' && "${POLICY_RAG_URL:-}" == 'http://127.0.0.1:18091' ]]; then
    bash "$ROOT/scripts/macos/start-rag-tunnel.sh"
fi

# 只启动已初始化的本项目数据库，绝不隐式建库或迁移。
if [[ ! -d tmp/mysql/mysql || ! -f tmp/mysql-client.cnf ]]; then
    echo '缺少本项目 MySQL 数据目录或管理配置，请先完成数据库初始化。' >&2
    exit 1
fi
if ! mysqladmin --defaults-file="$ROOT/tmp/mysql-client.cnf" ping --silent >/dev/null 2>&1; then
    mysqld --no-defaults --basedir="$MYSQL_HOME" --datadir="$ROOT/tmp/mysql" \
        --bind-address=127.0.0.1 --port="$MYSQL_PORT" --mysqlx=OFF \
        --socket="$ROOT/tmp/mysql.sock" --pid-file="$ROOT/tmp/mysql.pid" \
        --log-error="$ROOT/logs/mysql.log" --daemonize
fi
mysql --defaults-file="$ROOT/tmp/mysql-client.cnf" \
    --default-character-set=utf8mb4 population_miniterm_local \
    --execute='SELECT 1' >/dev/null

running() {
    local pid_file="$1" expected="$2" pid command
    [[ -f "$pid_file" ]] || return 1
    pid="$(cat "$pid_file")"
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    command="$(ps -p "$pid" -o command= 2>/dev/null)" || return 1
    [[ "$command" == *"$expected"* ]]
}

free_port() {
    if lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "端口 $1 已被其他进程占用，请检查 .env.local。" >&2
        exit 1
    fi
}

# 独立会话保证关闭启动终端后服务仍可运行；PID 只记录本脚本启动的进程。
launch() {
    python3 - "$@" <<'PY'
import subprocess
import sys
from pathlib import Path

pid_file, log_file, *command = sys.argv[1:]
with open(log_file, 'ab', buffering=0) as log:
    process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=log,
                               stderr=subprocess.STDOUT, start_new_session=True)
Path(pid_file).write_text(str(process.pid) + '\n')
PY
}

JAR="$ROOT/target/population-miniterm-1.0.0.jar"
if ! running tmp/backend.pid "$JAR"; then
    free_port "$SERVER_PORT"
    if [[ "${1:-}" == '--build' || ! -f "$JAR" ]]; then
        bash ./mvnw -B -DskipTests package > logs/build.log 2>&1
    fi
    launch tmp/backend.pid logs/backend.log "$JAVA_HOME/bin/java" -Xms128m -Xmx512m -jar "$JAR" \
        --server.address=127.0.0.1
fi

# 必须等到数据库健康检查通过才启动前端。
python3 - "$SERVER_PORT" <<'PY'
import json
import sys
import time
import urllib.request

url = f'http://127.0.0.1:{sys.argv[1]}/api/health'
for attempt in range(60):
    try:
        with urllib.request.urlopen(url, timeout=2) as response:
            if json.load(response).get('data', {}).get('database') == 'UP':
                break
    except (OSError, ValueError):
        pass
    time.sleep(1)
else:
    sys.exit('后端或数据库未就绪，请查看 logs/backend.log 和 logs/mysql.log。')
PY

VITE="$ROOT/frontend/node_modules/vite/bin/vite.js"
if ! running tmp/frontend.pid "$VITE"; then
    free_port "$VITE_DEV_PORT"
    if [[ ! -f "$VITE" ]]; then
        (cd frontend && npm ci --no-audit --no-fund)
    fi
    export VITE_BACKEND_TARGET="http://127.0.0.1:$SERVER_PORT"
    export NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=512}"
    cd frontend
    launch "$ROOT/tmp/frontend.pid" "$ROOT/logs/frontend.log" \
        "$(command -v node)" "$VITE" --host 127.0.0.1
fi
echo "前端：http://127.0.0.1:$VITE_DEV_PORT"
echo "后端：http://127.0.0.1:$SERVER_PORT/api/health"
echo "日志：$ROOT/logs"
