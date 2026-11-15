#!/bin/bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Run this isolated cloud harness in GitHub Actions.' >&2; exit 1; }
export OPS_PROJECT_NAME="population-cloud-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
export OPS_ENV_FILE="${RUNNER_TEMP}/population-cloud.env"
mkdir -p target/cloud-evidence
python3 - <<'PY' > target/cloud-evidence/environment.json
import json,os,platform
from pathlib import Path
memory=next(line.split(':',1)[1].strip() for line in Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemTotal:'))
print(json.dumps({'kernel':platform.release(),'architecture':platform.machine(),'logical_cpus':os.cpu_count(),'memory_total':memory,'run_id':os.environ['GITHUB_RUN_ID'],'source_commit':os.environ['GITHUB_SHA']},indent=2))
PY
python3 - <<'PY'
import os,secrets,pathlib
values={"DB_URL":"jdbc:mysql://mysql-primary:3306/population_ops", "DB_USERNAME":"population",
        "DB_PASSWORD":secrets.token_hex(20), "JWT_SECRET":secrets.token_hex(32),
        "MYSQL_ROOT_PASSWORD":secrets.token_hex(24), "REPLICATION_PASSWORD":secrets.token_hex(16),
        "WEB_PORT":"8088", "WEB_BIND_IP":"127.0.0.1", "IDCARD_OCR_URL":"",
        "REDIS_DICTIONARY_TTL":"3s", "RABBITMQ_USERNAME":"population", "RABBITMQ_PASSWORD":secrets.token_hex(20)}
pathlib.Path(os.environ['OPS_ENV_FILE']).write_text(''.join(f'{k}={v}\n' for k,v in values.items()))
for key in ('DB_PASSWORD','JWT_SECRET','MYSQL_ROOT_PASSWORD','REPLICATION_PASSWORD','RABBITMQ_PASSWORD'):
    print('::add-mask::'+values[key])
PY
dc=(docker compose -p "$OPS_PROJECT_NAME" --env-file "$OPS_ENV_FILE" -f config/docker/compose.yaml -f config/docker/compose.mysql.yaml -f config/docker/compose.dual.yaml -f config/docker/compose.rabbitmq.yaml --profile dual)
cleanup() {
  "${dc[@]}" ps -a > target/cloud-evidence/containers.txt 2>&1 || true
  "${dc[@]}" logs --no-color > target/cloud-evidence/containers.log 2>&1 || true
  docker stats --no-stream > target/cloud-evidence/resources.txt 2>&1 || true
  python3 scripts/ops/diagnose-logs.py target/cloud-evidence/containers.log --output target/cloud-evidence/diagnosis.json || true
  "${dc[@]}" down --volumes --remove-orphans > target/cloud-evidence/cleanup.log 2>&1 || true
}
trap cleanup EXIT
"${dc[@]}" config --quiet
"${dc[@]}" build backend web
"${dc[@]}" --profile ocr build ocr
docker run --rm -v "$PWD/services/ocr/test_app.py:/app/test_app.py:ro" population-miniterm-ocr:local python -m unittest -v test_app
"${dc[@]}" --profile dbinit run --rm db-bootstrap
"${dc[@]}" up -d --wait --wait-timeout 240 redis rabbitmq backend backend2 web
"${dc[@]}" exec -T web nginx -t
python3 scripts/ops/verify-cloud-stack.py
