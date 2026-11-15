#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -f .env.local ]]; then
    set -a
    source .env.local
    set +a
fi
if [[ ! -x .venv/bin/python ]]; then
    echo '请先在 ai-service 下执行 uv sync --locked --python 3.12。' >&2
    exit 1
fi
# 默认仅监听本机；内部鉴权密钥仍由应用启动时强制检查。
exec "$PWD/.venv/bin/python" -m uvicorn yixun_ai.app:create_app --factory \
    --host 127.0.0.1 --port "${YIXUN_AI_PORT:-8091}" --no-access-log
