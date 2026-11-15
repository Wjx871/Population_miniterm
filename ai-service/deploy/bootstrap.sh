#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
RELEASE_SOURCE="$PWD"

# 该脚本只为闲置的 2 核 2GB 宝塔机准备；先检查再执行任何安装或写入。
python3 - <<'PY'
from pathlib import Path
import os
import subprocess
assert os.geteuid() == 0, 'Root required'
assert 'VERSION_ID="24.04"' in Path('/etc/os-release').read_text(), 'Unexpected OS'
memory_kb = int(next(line.split()[1] for line in Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemTotal:')))
assert memory_kb < 3 * 1024 * 1024, 'Refusing a larger host: never install on the treehole server'
assert subprocess.check_output(['nproc'], text=True).strip() == '2', 'Unexpected CPU count'
expected_ip = os.environ.get('YIXUN_RAG_EXPECTED_PRIVATE_IP')
assert expected_ip and expected_ip in subprocess.check_output(['hostname', '-I'], text=True).split(), 'Set YIXUN_RAG_EXPECTED_PRIVATE_IP to the verified target address'
target = Path('/opt/yixun-ai')
assert not target.exists() or (target / '.yixun-managed').is_file(), 'Unmanaged directory already exists'
print('Target checks passed for the isolated 2GB host.', flush=True)
PY

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y --no-install-recommends python3-venv
if ! id yixun-ai >/dev/null 2>&1; then
    useradd --system --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin yixun-ai
fi
install -d -m 755 /opt/yixun-ai /opt/yixun-ai/releases
touch /opt/yixun-ai/.yixun-managed
install -d -m 700 /etc/yixun-ai
if [[ ! -x /opt/yixun-ai/venv/bin/python ]]; then
    python3 -m venv /opt/yixun-ai/venv
fi
/opt/yixun-ai/venv/bin/python -m pip install --disable-pip-version-check --no-cache-dir --only-binary=:all: --require-hashes --timeout 20 --retries 1 -r "$RELEASE_SOURCE/requirements-api.lock"

RELEASE_ID="$(cat "$RELEASE_SOURCE/release-id")"
[[ "$RELEASE_ID" =~ ^[a-f0-9]{16}$ ]] || { echo 'Invalid release ID' >&2; exit 1; }
RELEASE_TARGET="/opt/yixun-ai/releases/$RELEASE_ID"
if [[ ! -d "$RELEASE_TARGET" ]]; then
    install -d -m 755 "$RELEASE_TARGET"
    cp -R "$RELEASE_SOURCE/yixun_ai" "$RELEASE_SOURCE/knowledge" "$RELEASE_TARGET/"
    cp "$RELEASE_SOURCE/release-manifest.json" "$RELEASE_TARGET/"
    find "$RELEASE_TARGET" -type d -exec chmod 755 {} +
    find "$RELEASE_TARGET" -type f -exec chmod 644 {} +
fi
ln -sfn "$RELEASE_TARGET" /opt/yixun-ai/current
install -m 755 "$RELEASE_SOURCE/deploy/set-deepseek-key.py" /opt/yixun-ai/set-deepseek-key.py

python3 - <<'PY'
from pathlib import Path
import secrets
directory = Path('/etc/yixun-ai')
service = directory / 'service.env'
if not service.exists():
    service.write_text('\n'.join([
        'YIXUN_AI_API_KEY=' + secrets.token_urlsafe(32),
        'YIXUN_AI_KNOWLEDGE_DIR=/opt/yixun-ai/current/knowledge',
        'YIXUN_AI_RETRIEVAL=lexical',
        'YIXUN_AI_RERANK=false',
        'YIXUN_AI_GENERATION_PROVIDER=deepseek',
        'YIXUN_AI_GENERATION_URL=https://api.deepseek.com',
        'YIXUN_AI_GENERATION_MODEL=deepseek-flash',
        'YIXUN_AI_GENERATION_TIMEOUT=40',
        'YIXUN_AI_QUERY_EXPANSION=true',
    ]) + '\n', encoding='utf-8')
    service.chmod(0o600)
provider = directory / 'provider.env'
if not provider.exists():
    provider.write_text('YIXUN_AI_GENERATION_KEY=\n', encoding='utf-8')
    provider.chmod(0o600)
print('Private configuration ready; DeepSeek key was not supplied or displayed.', flush=True)
PY

install -m 644 "$RELEASE_SOURCE/deploy/yixun-ai.service" /etc/systemd/system/yixun-ai.service
systemd-analyze verify /etc/systemd/system/yixun-ai.service
systemctl daemon-reload
systemctl enable yixun-ai
systemctl restart yixun-ai
python3 - <<'PY'
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import json
import time
base = 'http://127.0.0.1:8091'
for _ in range(30):
    try:
        with urlopen(base + '/health', timeout=2) as response:
            health = json.load(response)
        break
    except OSError:
        time.sleep(.5)
else:
    raise SystemExit('Service did not become ready')
assert health['generationProvider'] == 'deepseek'
assert not health['capabilities']['semanticRetrieval']
print(json.dumps({'health': health}, ensure_ascii=False))
key = dict(line.split('=', 1) for line in Path('/etc/yixun-ai/service.env').read_text().splitlines())['YIXUN_AI_API_KEY']
body = json.dumps({'question': '如何办理户籍迁入？'}).encode()
try:
    urlopen(Request(base + '/internal/policy-query', data=body, headers={'Content-Type': 'application/json'}), timeout=3)
except HTTPError as error:
    assert error.code == 401
else:
    raise AssertionError('Internal authentication is not enforced')
with urlopen(Request(base + '/internal/policy-query', data=body, headers={'Content-Type': 'application/json', 'X-AI-Service-Key': key}), timeout=55) as response:
    answer = json.load(response)
assert answer['citations']
print(json.dumps({'queryMode': answer['mode'], 'generationMode': answer['generationMode'], 'citations': len(answer['citations']), 'warnings': answer['warnings']}, ensure_ascii=False))
PY
systemctl show yixun-ai --property=ActiveState,MemoryCurrent,MemoryMax,CPUQuotaPerSecUSec
ss -lnt '( sport = :8091 )'
