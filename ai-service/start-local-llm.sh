#!/usr/bin/env bash
set -euo pipefail
if [[ "$(uname -s)" == "Darwin" ]]; then
    echo '用户已禁止本机模型推理，此启动入口已停用。请使用远程 API 或独立远程服务器。' >&2
    exit 1
fi
cd "$(dirname "$0")"
LLAMA_SERVER="${YIXUN_LLAMA_SERVER:-$(command -v llama-server || true)}"
if [[ -z "$LLAMA_SERVER" ]]; then
    echo '需要 llama.cpp 的 llama-server；请按 RAG 运行说明安装。' >&2
    exit 1
fi
.venv/bin/python - <<'PY'
from pathlib import Path
from hashlib import sha256
import json
spec=json.loads(Path('model-manifest.json').read_text())['generation']
path=Path('cache/models/generation')/spec['filename']
digest=sha256()
with path.open('rb') as stream:
    for block in iter(lambda:stream.read(1048576),b''):
        digest.update(block)
if digest.hexdigest()!=spec['files'][spec['filename']]:
    raise SystemExit('Generation model checksum mismatch')
PY
MODEL="$(.venv/bin/python -c "import json;print(json.load(open('model-manifest.json'))['generation']['filename'])")"
exec "$LLAMA_SERVER" --model "$PWD/cache/models/generation/$MODEL" \
    --host 127.0.0.1 --port "${YIXUN_LLM_PORT:-8092}" --alias yixun-local \
    --ctx-size "${YIXUN_LLM_CONTEXT:-2048}" --n-gpu-layers "${YIXUN_LLM_GPU_LAYERS:-0}" \
    --parallel 1 --threads 2 --threads-batch 2 \
    --batch-size 128 --ubatch-size 64 --no-webui
