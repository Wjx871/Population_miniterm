#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
KEYDIR="$ROOT/tmp/ssh-yixun"
[[ -f "$KEYDIR/id_ed25519" && -f "$KEYDIR/known_hosts" ]] || { echo 'RAG 隧道尚未完成授权或主机公钥校验。' >&2; exit 1; }
python3 - "$ROOT" <<'PY'
from pathlib import Path
from ipaddress import ip_address
import json, os, re, socket, subprocess, sys, tempfile, time
from urllib.request import urlopen
root = Path(sys.argv[1]); keys = root / 'tmp/ssh-yixun'
known_hosts = keys / 'known_hosts'
hosts = {line.split()[0] for line in known_hosts.read_text().splitlines()
         if line.strip() and not line.lstrip().startswith('#')}
if len(hosts) != 1:
    raise SystemExit('The project known_hosts file must identify exactly one RAG server')
try:
    host = ip_address(next(iter(hosts)))
except ValueError:
    raise SystemExit('Use the verified RAG server IP in the project known_hosts file')
common = ['/usr/bin/ssh', '-F', '/dev/null', '-i', str(keys/'id_ed25519'), '-o', 'IdentitiesOnly=yes',
          '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'UserKnownHostsFile="'+str(known_hosts)+'"',
          '-o', 'ConnectTimeout=8', f'yixun-tunnel@{host}']
pidfile = root/'tmp/yixun-tunnel.pid'
running = False
if pidfile.exists():
    pid = int(pidfile.read_text())
    result = subprocess.run(['ps', '-p', str(pid), '-o', 'command='], capture_output=True, text=True)
    running = result.returncode == 0 and str(keys/'id_ed25519') in result.stdout and '127.0.0.1:18091:127.0.0.1:8091' in result.stdout
if not running:
    with socket.socket() as probe:
        if probe.connect_ex(('127.0.0.1',18091)) == 0:
            raise SystemExit('Port 18091 is occupied; no process was replaced')
    # 只取 RAG 内部调用密钥，经 SSH 加密传输；不取 DeepSeek Key，不打印凭证。
    key = subprocess.check_output(common+['get-service-key'], text=True, timeout=15).strip()
    if not re.fullmatch(r'[A-Za-z0-9_-]{32,256}', key):
        raise SystemExit('Invalid internal service key; local configuration unchanged')
    envfile = root/'.env.local'
    lines = envfile.read_text().splitlines()
    updates = {'POLICY_RAG_ENABLED':'true','POLICY_RAG_URL':'http://127.0.0.1:18091','POLICY_RAG_SERVICE_KEY':key,'POLICY_AI_ENABLED':'false'}
    lines = [line for line in lines if line.split('=',1)[0] not in updates]
    lines.extend(name+'='+value for name,value in updates.items())
    with tempfile.NamedTemporaryFile(mode='w',dir=root/'tmp',delete=False) as output:
        output.write('\n'.join(lines)+'\n'); temporary=Path(output.name)
    temporary.chmod(0o600);temporary.replace(envfile)
    command=common[:-1]+['-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=30','-o','ServerAliveCountMax=3','-N','-L','127.0.0.1:18091:127.0.0.1:8091',common[-1]]
    with (root/'logs/yixun-tunnel.log').open('ab') as log:
        process=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=log,stderr=log,start_new_session=True)
    pidfile.write_text(str(process.pid)+'\n')
for _ in range(30):
    try:
        with urlopen('http://127.0.0.1:18091/health',timeout=3) as response:health=json.load(response)
        if health.get('generationProvider')=='deepseek':break
    except (OSError,ValueError):pass
    time.sleep(.2)
else:raise SystemExit('RAG tunnel not ready; inspect logs/yixun-tunnel.log')
print('RAG encrypted tunnel ready at 127.0.0.1:18091; provider='+str(health.get('generationProvider')))
PY
