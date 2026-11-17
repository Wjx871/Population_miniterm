"""End-to-end checks for the disposable GitHub Actions Compose stack."""
import gzip
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:8088"
OUT = Path("target/cloud-evidence")
DC = ["docker", "compose", "-p", os.environ["OPS_PROJECT_NAME"], "--env-file", os.environ["OPS_ENV_FILE"],
      "-f", "config/docker/compose.yaml", "-f", "config/docker/compose.mysql.yaml", "-f", "config/docker/compose.dual.yaml",
      "-f", "config/docker/compose.rabbitmq.yaml", "--profile", "dual"]
results = []


def command(*args, data=None):
    result = subprocess.run(DC + list(args), input=data, capture_output=True)
    if result.returncode:
        # Arguments may contain ephemeral auth headers. Never include them in a traceback.
        raise RuntimeError("Compose command failed: " + result.stderr.decode(errors="replace")[-1500:])
    return result.stdout


def sql(node, statement):
    return command("exec", "-T", node, "sh", "-c",
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names population_ops',
                   data=(statement + "\n").encode()).decode().strip()


def record(name, **evidence):
    results.append({"check": name, "passed": True, **evidence})
    (OUT / "checks.json").write_text(json.dumps(results, ensure_ascii=False, indent=2))
    print("[PASS]", name, flush=True)


def request(path, token=None, method="GET", body=None, headers=None):
    extra = dict(headers or {})
    if token:
        extra["Authorization"] = "Bearer " + token
    if isinstance(body, dict):
        extra["Content-Type"] = "application/json"
        body = json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=body, headers=extra, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read()


def api(path, token=None, method="GET", body=None):
    status, headers, raw = request(path, token, method, body)
    if status not in (200, 201):
        raise RuntimeError(f"API failed: {method} {path} HTTP {status}")
    payload = json.loads(raw)
    if payload.get("code") not in (200, 201):
        raise RuntimeError("Business response failed: " + path)
    return payload["data"], headers


def login(user="admin"):
    return api("/api/auth/login", method="POST", body={"username": user, "password": "123456"})[0]["token"]


def internal(node, path, token=None):
    args = ["exec", "-T", node, "curl", "-sS", "--max-time", "10", "-w", "\n%{http_code}"]
    if token:
        args += ["-H", "Authorization: Bearer " + token]
    raw = command(*args, "http://127.0.0.1:8080" + path)
    body, status = raw.rsplit(b"\n", 1)
    return int(status), body


def wait_backend(node):
    for _ in range(90):
        try:
            status, raw = internal(node, "/api/health")
            if status == 200 and json.loads(raw)["data"]["database"] == "UP":
                return
        except (RuntimeError, ValueError, KeyError):
            pass
        time.sleep(1)
    raise RuntimeError(node + " did not become healthy")


def routes(token):
    return {node: json.loads(internal(node, "/api/ops/database-routing", token)[1])["data"] for node in ("backend", "backend2")}


def flush_cache():
    command("exec", "-T", "redis", "redis-cli", "FLUSHDB")


def proxy_config(upstream, listen):
    config = Path("config/nginx/population.dual.conf").read_text()
    config = re.sub(r"^log_format.*?;\s*", "", config, count=1, flags=re.S)
    return config.replace("population_backend", upstream).replace("listen 80;", f"listen {listen};")


def check_nginx_strategies():
    evidence = OUT / "nginx-strategies"
    evidence.mkdir(exist_ok=True)
    policies = ("round_robin", "least_conn", "ip_hash", "least_conn")
    installed = []
    try:
        for port, policy in enumerate(policies, start=18180):
            config = proxy_config(f"population_probe_{port}", f"127.0.0.1:{port}")
            include = "/etc/nginx/upstream/population-strategy.conf"
            fragment = Path(f"config/nginx/strategies/{policy}.conf").read_text().strip()
            config = config.replace(f"include {include};", fragment)
            if port == 18183:
                # Use controlled upstreams to keep exactly one connection active.
                config = config.replace("backend:8080", "127.0.0.1:18184")
                config = config.replace("backend2:8080", "127.0.0.1:18185")
            local = evidence / f"{policy}-{port}.conf"
            local.write_text(config)
            remote = f"/etc/nginx/conf.d/proof-strategy-{port}.conf"
            installed.append(remote)
            command("cp", str(local), "web:" + remote)
        command("exec", "-T", "web", "nginx", "-t")
        command("exec", "-T", "web", "nginx", "-s", "reload")
        web = command("ps", "-q", "web").decode().strip()
        probe = Path("scripts/ops/nginx-strategy-probe.py").resolve()
        result = subprocess.run(
            ["docker", "run", "--rm", "--network", f"container:{web}",
             "-v", f"{probe}:/app/nginx-strategy-probe.py:ro", "--entrypoint", "python",
             "population-miniterm-ocr:local", "/app/nginx-strategy-probe.py"],
            capture_output=True, text=True, timeout=60,
        )
        if result.returncode:
            raise RuntimeError(f"Nginx strategy probe failed: {result.stderr[-2000:]}")
        observations = json.loads(result.stdout)
        (evidence / "routes.json").write_text(json.dumps(observations, indent=2) + "\n")
        record("nginx_round_robin_least_conn_and_ip_hash", **observations)
    finally:
        if installed:
            command("exec", "-T", "web", "rm", "-f", *installed)
            command("exec", "-T", "web", "nginx", "-s", "reload")


def check_proxy_retry_policy():
    names = [os.environ["OPS_PROJECT_NAME"] + "-proxy-proof-" + suffix for suffix in ("a", "b")]
    proof = Path("target/cloud-evidence/proxy-proof.conf")
    config = proxy_config("proxy_proof", 81)
    config = config.replace("backend:8080", "retry-fixture-a:8080").replace("backend2:8080", "retry-fixture-b:8080")
    config = config.replace("max_fails=1 fail_timeout=5s", "max_fails=0")
    proof.write_text(config)
    try:
        for index, name in enumerate(names):
            completed = subprocess.run(["docker", "run", "-d", "--name", name, "--network", os.environ["OPS_PROJECT_NAME"] + "_edge",
                "--network-alias", "retry-fixture-" + ("a" if index == 0 else "b"),
                "-e", "FIXTURE_MODE=" + ("unavailable" if index == 0 else "healthy"),
                "-v", str(Path("scripts/ops/proxy-fixture.py").resolve()) + ":/app/proxy-fixture.py:ro",
                "--entrypoint", "python", "population-miniterm-ocr:local", "/app/proxy-fixture.py"], capture_output=True)
            if completed.returncode:
                raise RuntimeError("Cannot start isolated proxy fixture")
        time.sleep(1)
        command("cp", str(proof), "web:/etc/nginx/conf.d/proxy-proof.conf")
        command("exec", "-T", "web", "nginx", "-t")
        command("exec", "-T", "web", "nginx", "-s", "reload")
        time.sleep(1)
        def call(url, method="GET"):
            args = ["exec", "-T", "backend", "curl", "-sS", "--max-time", "10", "-w", "\n%{http_code}", "-X", method]
            if method != "GET":
                args += ["-H", "Content-Type: application/json", "--data", '{"proof":"preserve-body"}']
            payload = command(*args, url)
            body, status = payload.rsplit(b"\n", 1)
            return int(status), json.loads(body)
        for _ in range(4):
            assert call("http://web:81/api/proof")[0] == 200
        for method in ("POST", "PUT", "PATCH", "DELETE"):
            assert call("http://web:81/api/proof", method)[0] == 503
        counts = [call("http://retry-fixture-" + suffix + ":8080/counts")[1] for suffix in ("a", "b")]
        assert len(counts[0]["GET"]) >= 1
        for method in ("POST", "PUT", "PATCH", "DELETE"):
            bodies = counts[0][method] + counts[1][method]
            assert bodies == ['{"proof":"preserve-body"}'], (method, bodies)
        record("proxy_get_retry_and_mutation_exactly_once", fixture_counts=counts)
    finally:
        try:
            command("exec", "-T", "web", "rm", "-f", "/etc/nginx/conf.d/proxy-proof.conf")
            command("exec", "-T", "web", "nginx", "-s", "reload")
        finally:
            for name in names:
                subprocess.run(["docker", "rm", "-f", name], capture_output=True)


def redis(*arguments):
    return command("exec", "-T", "redis", "redis-cli", "--raw", *arguments).decode().strip()


def cache_counts(token):
    return {node: json.loads(internal(node, "/api/ops/cache", token)[1])["data"]["counters"] for node in ("backend", "backend2")}


def check_cache(token, viewer):
    kind = "OPS_CACHE_" + uuid.uuid4().hex[:8].upper()
    item, _ = api("/api/dictionaries", token, "POST", {"dictionaryType": kind, "dictionaryCode": "ONE", "displayName": "initial", "sortNo": 0})
    path = "/api/dictionaries/" + kind
    key = "population:dictionary:v2:MANAGE:" + kind
    before = cache_counts(token)
    assert api(path, token)[0][0]["dictName"] == "initial"
    assert api(path, token)[0][0]["dictName"] == "initial"
    after = cache_counts(token)
    assert sum(value["hits"] for value in after.values()) > sum(value["hits"] for value in before.values())
    ttl_ms = int(redis("PTTL", key))
    assert 0 < ttl_ms <= 3000, ttl_ms
    time.sleep(3.2)
    assert redis("EXISTS", key) == "0"
    assert api(path, token)[0][0]["dictName"] == "initial"
    cached = redis("GET", key)
    status, _, _ = request("/api/dictionaries/" + str(item["dictId"]), token, "PUT", {"displayName": "must-not-commit", "sortNo": 0, "version": 999})
    assert status == 409
    assert redis("GET", key) == cached
    assert sql("mysql-primary", f"SELECT dict_name FROM data_dictionary WHERE dict_id={item['dictId']};") == "initial"
    api("/api/dictionaries/" + str(item["dictId"]), token, "PUT", {"displayName": "updated", "sortNo": 0, "version": 0})
    assert redis("EXISTS", key) == "0"
    assert api(path, token)[0][0]["dictName"] == "updated"
    assert sql("mysql-primary", f"SELECT dict_name FROM data_dictionary WHERE dict_id={item['dictId']};") == "updated"
    api("/api/dictionaries/" + str(item["dictId"]) + "/disable", token, "POST", {"version": 1})
    assert api(path, viewer)[0] == []
    assert api(path, token)[0][0]["status"] == "DISABLED"
    assert api(path, viewer)[0] == []
    record("redis_real_hit_expiry_invalidation_rollback_and_scope", ttl_ms=ttl_ms, before=before, after=after)


def broker_fixture(method, path, body):
    code = """import os,json,urllib.request,base64,sys
x=json.load(sys.stdin)
auth=base64.b64encode((os.environ['RABBITMQ_USERNAME']+':'+os.environ['RABBITMQ_PASSWORD']).encode()).decode()
r=urllib.request.Request('http://rabbitmq:15672'+x['path'],data=json.dumps(x['body']).encode(),method=x['method'],headers={'Authorization':'Basic '+auth,'Content-Type':'application/json'})
with urllib.request.urlopen(r,timeout=10) as response:
 raw=response.read();print(raw.decode() if raw else '{}')
"""
    completed = subprocess.run(["docker", "run", "--rm", "--network", os.environ["OPS_PROJECT_NAME"] + "_cache",
                                "--env-file", os.environ["OPS_ENV_FILE"], "-i", "--entrypoint", "python",
                                "population-miniterm-ocr:local", "-c", code],
                               input=json.dumps({"method": method, "path": path, "body": body}).encode(), capture_output=True)
    if completed.returncode:
        raise RuntimeError("Broker fixture operation failed")
    return json.loads(completed.stdout)


def check_broker(token, viewer):
    assert request("/api/ops/rabbitmq")[0] == 401
    assert request("/api/ops/rabbitmq", viewer)[0] == 403
    queue = "ops.validation." + uuid.uuid4().hex[:8]
    broker_fixture("PUT", "/api/queues/%2F/" + queue, {"durable": True, "auto_delete": False, "arguments": {}})
    assert broker_fixture("POST", "/api/exchanges/%2F/amq.default/publish", {
        "properties": {"delivery_mode": 2}, "routing_key": queue,
        "payload": "synthetic-task", "payload_encoding": "string"})["routed"]
    def wait_ready():
        for _ in range(60):
            metrics, _ = api("/api/ops/rabbitmq", token)
            if metrics["status"] == "UP" and any(row["name"] == queue and row["messagesReady"] >= 1 for row in metrics["queues"]):
                return metrics
            time.sleep(1)
        raise RuntimeError("Broker metrics did not report durable fixture message")
    before = wait_ready()
    command("restart", "rabbitmq")
    after = wait_ready()
    record("rabbitmq_private_metrics_durable_queue_and_message", before=before, after=after)
    command("stop", "rabbitmq")
    try:
        metrics, _ = api("/api/ops/rabbitmq", token)
        assert metrics["status"] == "DOWN"
        assert api("/api/auth/me", token)[0]
        record("rabbitmq_failure_is_reported_without_breaking_business")
    finally:
        command("start", "rabbitmq")
    wait_ready()


def main():
    token = login()
    seen = set()
    for _ in range(20):
        data, headers = api("/api/health")
        assert data["database"] == "UP"
        assert re.fullmatch(r"[A-Za-z0-9_-]{1,64}", headers["X-Trace-Id"])
        seen.add(headers["X-Instance-Id"])
    assert seen == {"backend-1", "backend-2"}, seen
    record("nginx_two_instances", instances=sorted(seen))
    check_nginx_strategies()
    check_proxy_retry_policy()

    assert request("/api/ops/runtime")[0] == 401
    viewer = login("viewer")
    assert request("/api/ops/runtime", viewer)[0] == 403
    runtime, _ = api("/api/ops/runtime", token)
    assert runtime["heapUsedBytes"] > 0
    assert "maximum" in runtime["connectionPools"]["primary"]
    (OUT / "runtime-start.json").write_text(json.dumps(runtime, indent=2))
    record("runtime_metrics_rbac_and_units")
    check_cache(token, viewer)
    check_broker(token, viewer)

    status, headers, html = request("/index.html")
    assert status == 200 and "no-cache" in headers.get("Cache-Control", "")
    assert request("/persons")[2] == html
    asset = re.search(rb'(?:src|href)="(/assets/[^\"]+\.js)"', html).group(1).decode()
    status, headers, raw = request(asset, headers={"Accept-Encoding": "gzip"})
    assert status == 200 and headers.get("Content-Encoding") == "gzip"
    assert "immutable" in headers.get("Cache-Control", "")
    identity_status, identity_headers, identity_body = request(asset, headers={"Accept-Encoding": "identity"})
    assert identity_status == 200 and not identity_headers.get("Content-Encoding")
    assert gzip.decompress(raw) == identity_body
    assert len(raw) < len(identity_body)
    assert "no-store" in request("/api/health")[1].get("Cache-Control", "")
    record("gzip_static_cache_spa_and_api_cache", identity_bytes=len(identity_body), gzip_bytes=len(raw),
           byte_reduction_percent=round((1 - len(raw) / len(identity_body)) * 100, 3))

    before = routes(token)
    flush_cache()
    api("/api/statistics/overview", token)
    api("/api/statistics/overview", token)
    after = routes(token)
    assert sum(x["replicaConnections"] for x in after.values()) > sum(x["replicaConnections"] for x in before.values())
    assert sql("mysql-primary", "SELECT @@server_id;") != sql("mysql-replica", "SELECT @@server_id;")
    record("actual_mysql_replica_routing", before=before, after=after)

    sql("mysql-primary", "CREATE TABLE IF NOT EXISTS ops_replication_probe(id VARCHAR(64) PRIMARY KEY);")
    gtid = sql("mysql-primary", "SELECT @@GLOBAL.gtid_executed;")
    assert sql("mysql-replica", f"SELECT WAIT_FOR_EXECUTED_GTID_SET('{gtid}',30);") == "0"
    marker = uuid.uuid4().hex
    sql("mysql-replica", "STOP REPLICA SQL_THREAD;")
    try:
        sql("mysql-primary", f"INSERT INTO ops_replication_probe VALUES ('{marker}');")
        assert sql("mysql-replica", f"SELECT COUNT(*) FROM ops_replication_probe WHERE id='{marker}';") == "0"
        draft, _ = api("/api/applications", token, "POST", {"businessType": "GENERAL_SERVICE", "title": "Cloud fixture", "reason": "Isolated cloud validation"})
        assert api(f"/api/applications/{draft['applicationId']}", token)[0]["title"] == "Cloud fixture"
        record("replication_delay_and_primary_read_after_write")
    finally:
        sql("mysql-replica", "START REPLICA SQL_THREAD;")
    gtid = sql("mysql-primary", "SELECT @@GLOBAL.gtid_executed;")
    assert sql("mysql-replica", f"SELECT WAIT_FOR_EXECUTED_GTID_SET('{gtid}',30);") == "0"
    record("replica_caught_up_after_resume")

    image = b"Cloud-only synthetic material; not a real identity document."
    boundary = uuid.uuid4().hex
    form = b""
    for key, value in (("materialType", "APPLICATION_FORM"), ("materialName", "Cloud fixture")):
        form += f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
    form += f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="cloud-fixture.png"\r\nContent-Type: image/png\r\n\r\n'.encode() + image + f'\r\n--{boundary}--\r\n'.encode()
    status, _, raw = request(f"/api/applications/{draft['applicationId']}/materials", token, "POST", form,
                            {"Content-Type": "multipart/form-data; boundary=" + boundary})
    assert status == 201, status
    material_id = json.loads(raw)["data"]["materialId"]
    for node in ("backend", "backend2"):
        assert internal(node, f"/api/materials/{material_id}/download", token) == (200, image)
    record("shared_volume_authorized_file_download")

    before = routes(token)
    command("stop", "mysql-replica")
    try:
        time.sleep(2)
        flush_cache()
        api("/api/statistics/overview", token)
        after = routes(token)
        assert sum(x["fallbacks"] for x in after.values()) > sum(x["fallbacks"] for x in before.values())
        record("replica_down_primary_fallback", before=before, after=after)
    finally:
        command("start", "mysql-replica")
    for _ in range(60):
        try:
            sql("mysql-replica", "SELECT 1;")
            break
        except RuntimeError:
            time.sleep(1)
    else:
        raise RuntimeError("Replica did not restart")

    command("stop", "redis")
    try:
        api("/api/statistics/overview", token)
        api("/api/auth/logout", token, "POST")
        for node in ("backend", "backend2"):
            assert internal(node, "/api/auth/me", token)[0] == 401
        record("redis_down_mysql_fallback_and_shared_revocation")
        # Preserve fault evidence BEFORE restart/recreate replaces container logs.
        fault_log = OUT / "fault-window.log"
        fault_log.write_bytes(command("logs", "--no-color"))
        diagnosis_path = OUT / "fault-diagnosis.json"
        subprocess.run(["python3", "scripts/ops/diagnose-logs.py", str(fault_log), "--output", str(diagnosis_path)], check=True)
        diagnosis = json.loads(diagnosis_path.read_text())
        rules = {item["rule"] for item in diagnosis["findings"]}
        assert {"redis_fallback", "replica_fallback"} <= rules, rules
        assert diagnosis["request_count"] >= 10
        record("real_fault_diagnosis_before_container_recreation", rules=sorted(rules),
               request_count=diagnosis["request_count"], time_range=diagnosis["time_range"])
        command("restart", "backend2")
        wait_backend("backend2")
        assert internal("backend2", "/api/auth/me", token)[0] == 401
        record("revocation_survives_instance_restart_without_redis")
    finally:
        command("start", "redis")
    token = login()
    assert internal("backend2", f"/api/materials/{material_id}/download", token) == (200, image)
    record("file_persistence_after_container_restart")

    command("stop", "backend2")
    try:
        observations = []
        for _ in range(20):
            started = time.perf_counter()
            observations.append({"status": request("/api/health")[0], "elapsed_ms": round((time.perf_counter() - started) * 1000, 3)})
        assert all(row["status"] == 200 for row in observations), observations
        record("single_instance_failure_read_retry", observations=observations,
               note="GET/HEAD may try one alternate upstream; mutation requests use a no-retry named location.")
    finally:
        command("start", "backend2")
    wait_backend("backend2")
    time.sleep(6)
    env = dict(os.environ, OPS_BEARER_TOKEN=token)
    subprocess.run(["python3", "scripts/perf/read-load.py", "--url", BASE + "/api/statistics/overview",
                    "--requests", "100", "--concurrency", "4", "--warmup", "10", "--output", str(OUT / "dual-baseline.json")], check=True, env=env)
    runtime, _ = api("/api/ops/runtime", token)
    (OUT / "runtime-end.json").write_text(json.dumps(runtime, indent=2))
    record("small_cloud_baseline_completed", capacity_claim=False)
    # Manual dispatch is the short operational regression; source pushes run the full experiment.
    manual_smoke = os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
    if not manual_smoke:
        subprocess.run(["python3", "scripts/perf/compare-cloud.py"], check=True)
        record("controlled_performance_comparison_completed", capacity_claim=False)
    (OUT / "summary.md").write_text("# Cloud container checks\n\n" + "\n".join("- PASS: " + entry["check"] for entry in results) +
                                   "\n\nSmall synthetic data only; baseline is not a capacity or optimization claim.\n" +
                                   ("Manual smoke mode: full comparison skipped; use the prior full-run evidence.\n" if manual_smoke else ""))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        (OUT / "failure.txt").write_text(type(error).__name__ + ": " + str(error))
        raise
