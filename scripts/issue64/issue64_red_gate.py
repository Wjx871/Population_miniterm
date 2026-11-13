"""Executable #64 durability gate (dual JVM + isolated Redis 8.10.3).

Supports two explicitly separated verification stages:
  baseline-red : Runs against legacy SHARED_REDIS_REQUIRED mode to reproduce the
                 unexpired-JWT resurrection after empty Redis recovery (RED = exit 2).
  target-green : Runs against future DURABLE_DB_REQUIRED mode backed by a single shared
                 authoritative persistent database (e.g. standalone MySQL 8.4 test DB).
                 Fails with ERROR (exit 1) if DURABLE_DB_REQUIRED is not implemented yet
                 or if shared DB coordinates are missing / per-JVM in-memory H2 is used.

Classifies post-empty-recovery outcomes strictly into:
  PASS  (exit 0): all prechecks/env ok, fresh JWT returns 200, and revoked old JWT
                  returns 401 on both instances A and B.
  RED   (exit 2): all prechecks/env ok, fresh JWT returns 200, old JWT statuses on A/B
                  are strictly valid {200, 401}, and at least one instance returns 200.
  ERROR (exit 1): environment failure, missing binary/classpath/DB, precheck failure,
                  unverified empty-dir switch, fresh JWT != 200, or 500/503 on old JWT.

No full JWT, signing secret, DB credential, or Redis password is ever logged.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import pathlib
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from typing import Any, Callable

EXIT_PASS = 0
EXIT_ERROR = 1
EXIT_RED = 2

STAGE_BASELINE_RED = "baseline-red"
STAGE_TARGET_GREEN = "target-green"

DEFAULT_REDIS_BIN = pathlib.Path(
    r"C:\Users\jxgm\.codex\worktrees\issue29-64-real-redis\redis-isolated\bin\Redis-8.10.3-Windows-x64-msys2\redis-server.exe"
)


class GateError(RuntimeError):
    """Environment, transport, timeout, or prerequisite failure (maps to ERROR)."""


def sha256_file(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def short_jti_digest(jti: str) -> str:
    return hashlib.sha256(jti.encode("utf-8")).hexdigest()[:12]


def to_cyg_path(path: pathlib.Path) -> str:
    posix = path.resolve().as_posix()
    return "/cygdrive/" + posix[0].lower() + posix[2:]


def free_loopback_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return int(s.getsockname()[1])


def decode_jwt_claims(token: str) -> dict[str, Any]:
    parts = token.split(".")
    if len(parts) != 3:
        raise GateError("malformed JWT structure")
    payload = parts[1]
    padded = payload + "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(padded))


def http_json(
    port: int,
    path: str,
    token: str | None = None,
    body: dict[str, Any] | None = None,
    timeout: float = 12.0,
) -> tuple[int, str]:
    headers: dict[str, str] = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    method = "GET"
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")
        method = "POST"
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}{path}",
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return int(resp.status), resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as err:
        return int(err.code), err.read().decode("utf-8", errors="replace")
    except (OSError, TimeoutError) as err:
        raise GateError(f"HTTP {method} {path} on 127.0.0.1:{port} failed: {type(err).__name__}") from err


def redis_cmd(port: int, password: str, *parts: str) -> Any:
    def encode_wire(cmd: tuple[str, ...]) -> bytes:
        encoded = [str(x).encode("utf-8") for x in cmd]
        chunks = [f"*{len(encoded)}\r\n".encode("ascii")]
        for item in encoded:
            chunks.append(f"${len(item)}\r\n".encode("ascii") + item + b"\r\n")
        return b"".join(chunks)

    with socket.create_connection(("127.0.0.1", port), timeout=3.0) as sock:
        f = sock.makefile("rb")

        def send(cmd: tuple[str, ...]) -> Any:
            sock.sendall(encode_wire(cmd))
            line = f.readline()
            if not line:
                raise GateError("redis disconnected unexpectedly")
            prefix = line[:1]
            if prefix == b"-":
                raise GateError(f"redis error response: {line[1:].decode('utf-8', errors='replace').strip()}")
            if prefix == b":":
                return int(line[1:])
            if prefix == b"$":
                length = int(line[1:])
                if length < 0:
                    return None
                val = f.read(length)
                f.read(2)
                return val
            if prefix == b"*":
                count = int(line[1:])
                return [read_item() for _ in range(count)]
            return line[1:].strip()

        def read_item() -> Any:
            line = f.readline()
            if not line:
                raise GateError("redis disconnected mid-array")
            prefix = line[:1]
            if prefix == b":":
                return int(line[1:])
            if prefix == b"$":
                length = int(line[1:])
                if length < 0:
                    return None
                val = f.read(length)
                f.read(2)
                return val
            return line[1:].strip()

        send(("AUTH", password))
        return send(parts)


@dataclass
class GateResult:
    verdict: str  # PASS | RED | ERROR
    exit_code: int
    reason: str
    a_status: int | None
    b_status: int | None
    fresh_b_status: int | None
    vulnerability_reproduced: bool


def validate_stage_requirements(
    stage: str,
    repo_root: pathlib.Path,
    shared_jdbc_url: str | None,
    shared_db_user: str | None,
) -> str:
    """Validate stage prerequisites and return the required revocation mode.

    BASELINE_RED strictly runs SHARED_REDIS_REQUIRED (isolated H2 per JVM permitted).
    TARGET_GREEN strictly requires DURABLE_DB_REQUIRED implemented in src/main AND a
    single shared authoritative persistent JDBC URL (never per-JVM H2 in-memory DBs).
    """
    if stage == STAGE_BASELINE_RED:
        return "SHARED_REDIS_REQUIRED"
    if stage != STAGE_TARGET_GREEN:
        raise GateError(f"unsupported_stage: {stage}")

    props_file = repo_root / "src" / "main" / "java" / "com" / "wjx871" / "population" / "security" / "RevocationProperties.java"
    if not props_file.exists():
        raise GateError(f"missing RevocationProperties.java: {props_file}")
    props_text = props_file.read_text(encoding="utf-8")
    if "DURABLE_DB_REQUIRED" not in props_text:
        raise GateError("target_green_prereq_unmet: DURABLE_DB_REQUIRED is not implemented yet in RevocationProperties")

    if not shared_jdbc_url or not shared_jdbc_url.strip():
        raise GateError("target_green_prereq_unmet: --shared-jdbc-url is required for target-green stage")
    lower_url = shared_jdbc_url.strip().lower()
    if lower_url.startswith("jdbc:h2:mem:"):
        raise GateError("target_green_prereq_unmet: isolated in-memory H2 cannot stand in for a shared persistent database")
    if not shared_db_user or not shared_db_user.strip():
        raise GateError("target_green_prereq_unmet: --shared-db-user is required for target-green stage")
    return "DURABLE_DB_REQUIRED"


def evaluate_post_empty_recovery(
    prechecks_ok: bool,
    empty_dir_verified: bool,
    redis_up: bool,
    a_old_status: int | None,
    b_old_status: int | None,
    fresh_b_status: int | None,
) -> GateResult:
    """Strictly classify post-empty-recovery observations into PASS, RED, or ERROR.

    Both fresh_b_status == 200 AND valid old-token HTTP statuses in {200, 401} are
    mandatory before emitting either PASS or RED.
    """
    if not prechecks_ok:
        return GateResult("ERROR", EXIT_ERROR, "prechecks_failed", a_old_status, b_old_status, fresh_b_status, False)
    if not empty_dir_verified:
        return GateResult("ERROR", EXIT_ERROR, "empty_dir_switch_unverified", a_old_status, b_old_status, fresh_b_status, False)
    if not redis_up:
        return GateResult("ERROR", EXIT_ERROR, "redis_did_not_reconnect", a_old_status, b_old_status, fresh_b_status, False)
    if a_old_status is None or b_old_status is None or fresh_b_status is None:
        return GateResult("ERROR", EXIT_ERROR, "incomplete_http_observations", a_old_status, b_old_status, fresh_b_status, False)
    if fresh_b_status != 200:
        return GateResult(
            "ERROR",
            EXIT_ERROR,
            f"fresh_token_not_valid(fresh={fresh_b_status},a={a_old_status},b={b_old_status})",
            a_old_status,
            b_old_status,
            fresh_b_status,
            False,
        )
    if a_old_status not in (200, 401) or b_old_status not in (200, 401):
        return GateResult(
            "ERROR",
            EXIT_ERROR,
            f"unexpected_old_token_statuses(a={a_old_status},b={b_old_status},fresh={fresh_b_status})",
            a_old_status,
            b_old_status,
            fresh_b_status,
            False,
        )

    if a_old_status == 401 and b_old_status == 401:
        return GateResult(
            "PASS",
            EXIT_PASS,
            "revoked_jwt_rejected_across_instances_and_fresh_valid",
            a_old_status,
            b_old_status,
            fresh_b_status,
            False,
        )

    return GateResult(
        "RED",
        EXIT_RED,
        "revoked_jwt_accepted_after_empty_redis_recovery",
        a_old_status,
        b_old_status,
        fresh_b_status,
        True,
    )


def verify_post_empty_recovery_over_http(
    aport: int,
    bport: int,
    old_token: str,
    fresh_token: str,
    prechecks_ok: bool,
    empty_dir_verified: bool,
    wait_timeout_s: float = 20.0,
    http_fn: Callable[[int, str, str | None, dict[str, Any] | None], tuple[int, str]] = http_json,
) -> GateResult:
    """Wait for Redis reconnection via /api/health, then evaluate A/B responses."""
    deadline = time.monotonic() + wait_timeout_s
    redis_up = False
    while time.monotonic() < deadline:
        try:
            h_status, h_body = http_fn(bport, "/api/health", None, None)
            if h_status == 200:
                payload = json.loads(h_body)
                if payload.get("data", {}).get("redisStatus") == "UP":
                    redis_up = True
                    break
        except (GateError, json.JSONDecodeError):
            pass
        time.sleep(0.25)

    if not redis_up:
        return evaluate_post_empty_recovery(prechecks_ok, empty_dir_verified, False, None, None, None)

    # Allow Lettuce a short window if an initial request still observes transient 503 right after health UP.
    a_status = None
    b_status = None
    fresh_status = None
    sub_deadline = time.monotonic() + min(10.0, wait_timeout_s)
    while time.monotonic() < sub_deadline:
        try:
            a_status, _ = http_fn(aport, "/api/auth/me", old_token, None)
            b_status, _ = http_fn(bport, "/api/auth/me", old_token, None)
            fresh_status, _ = http_fn(bport, "/api/auth/me", fresh_token, None)
            if 503 not in (a_status, b_status, fresh_status):
                break
        except GateError as err:
            return GateResult("ERROR", EXIT_ERROR, str(err), a_status, b_status, fresh_status, False)
        time.sleep(0.25)

    return evaluate_post_empty_recovery(
        prechecks_ok=prechecks_ok,
        empty_dir_verified=empty_dir_verified,
        redis_up=redis_up,
        a_old_status=a_status,
        b_old_status=b_status,
        fresh_b_status=fresh_status,
    )


def build_classpath(repo_root: pathlib.Path, cp_file: pathlib.Path) -> str:
    if not cp_file.exists():
        raise GateError(f"classpath file missing: {cp_file}")
    cp = cp_file.read_text(encoding="utf-8").strip()
    if not cp:
        raise GateError(f"classpath file is empty: {cp_file}")
    classes_dir = repo_root / "target" / "classes"
    test_classes_dir = repo_root / "target" / "test-classes"
    if not classes_dir.exists():
        raise GateError(f"compiled classes missing: {classes_dir}")
    parts = [str(classes_dir)]
    if test_classes_dir.exists():
        parts.insert(0, str(test_classes_dir))
    parts.append(cp)
    return ";".join(parts)


def run_live_gate(
    repo_root: pathlib.Path,
    redis_bin: pathlib.Path,
    cp_file: pathlib.Path,
    runtime_root: pathlib.Path,
    evidence_out: pathlib.Path | None,
    stage: str = STAGE_BASELINE_RED,
    shared_jdbc_url: str | None = None,
    shared_db_user: str | None = None,
    shared_db_password: str | None = None,
) -> int:
    revocation_mode = validate_stage_requirements(stage, repo_root, shared_jdbc_url, shared_db_user)
    if not redis_bin.exists():
        raise GateError(f"redis-server binary not found: {redis_bin}")
    classpath = build_classpath(repo_root, cp_file)

    run_id = uuid.uuid4().hex[:12]
    run_dir = runtime_root / f"run-{run_id}"
    run_dir.mkdir(parents=True, exist_ok=True)

    rport = free_loopback_port()
    aport = free_loopback_port()
    bport = free_loopback_port()
    admin_password = secrets.token_urlsafe(32)
    app_password = secrets.token_urlsafe(32)
    jwt_secret = secrets.token_urlsafe(48)
    prefix = f"issue64red-{run_id}"

    events: list[dict[str, Any]] = []
    processes: list[subprocess.Popen[bytes]] = []
    open_files: list[Any] = []

    def emit(event_name: str, **fields: Any) -> None:
        entry = {"event": event_name, "ts": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), **fields}
        events.append(entry)
        print(json.dumps(entry, ensure_ascii=False), flush=True)

    def stop_proc(proc: subprocess.Popen[bytes]) -> None:
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=5)

    def start_redis(label: str) -> tuple[subprocess.Popen[bytes], pathlib.Path, pathlib.Path, pathlib.Path]:
        data_dir = run_dir / f"data-{label}"
        data_dir.mkdir(parents=True, exist_ok=True)
        conf_path = run_dir / f"redis-{label}.conf"
        log_path = run_dir / f"redis-{label}.log"
        conf_path.write_text(
            "\n".join(
                [
                    "bind 127.0.0.1",
                    "protected-mode yes",
                    f"port {rport}",
                    f"dir {data_dir.as_posix()}",
                    "dbfilename dump.rdb",
                    'save ""',
                    "appendonly no",
                    f"requirepass {admin_password}",
                    f"user app on >{app_password} ~{prefix}:* +@all",
                    "loglevel notice",
                ]
            )
            + "\n",
            encoding="utf-8",
        )
        fh = log_path.open("wb")
        open_files.append(fh)
        proc = subprocess.Popen(
            [str(redis_bin), to_cyg_path(conf_path)],
            stdout=fh,
            stderr=subprocess.STDOUT,
            cwd=str(run_dir),
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        processes.append(proc)
        for _ in range(100):
            if proc.poll() is not None:
                raise GateError(f"redis-{label} exited early with code {proc.returncode}")
            try:
                if redis_cmd(rport, admin_password, "PING") == b"PONG":
                    break
            except (OSError, GateError):
                time.sleep(0.1)
        else:
            raise GateError(f"redis-{label} startup timed out")
        emit("redis_started", label=label, pid=proc.pid, port=rport, data_dir=str(data_dir))
        return proc, data_dir, conf_path, log_path

    def start_app(name: str, port: int) -> tuple[subprocess.Popen[bytes], pathlib.Path]:
        env = os.environ.copy()
        env.update(
            {
                "POPULATION_REDIS_ENABLED": "true",
                "SPRING_DATA_REDIS_HOST": "127.0.0.1",
                "SPRING_DATA_REDIS_PORT": str(rport),
                "SPRING_DATA_REDIS_PASSWORD": app_password,
                "POPULATION_REDIS_KEY_PREFIX": prefix,
                "SECURITY_JWT_REVOCATION_MODE": revocation_mode,
                "JWT_SECRET": jwt_secret,
            }
        )
        if stage == STAGE_TARGET_GREEN:
            assert shared_jdbc_url is not None and shared_db_user is not None
            env["SPRING_DATASOURCE_URL"] = shared_jdbc_url
            env["SPRING_DATASOURCE_USERNAME"] = shared_db_user
            if shared_db_password is not None:
                env["SPRING_DATASOURCE_PASSWORD"] = shared_db_password

        log_path = run_dir / f"app-{name}.log"
        fh = log_path.open("wb")
        open_files.append(fh)
        args = [
            "java",
            "-cp",
            classpath,
            "com.wjx871.population.PopulationMinitermApplication",
            f"--server.port={port}",
            "--server.address=127.0.0.1",
            "--spring.data.redis.username=app",
            "--spring.data.redis.timeout=1s",
        ]
        if stage == STAGE_BASELINE_RED:
            args.append(
                f"--spring.datasource.url=jdbc:h2:mem:issue64red_{run_id}_{name};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
            )
        proc = subprocess.Popen(
            args,
            env=env,
            cwd=str(repo_root),
            stdout=fh,
            stderr=subprocess.STDOUT,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        processes.append(proc)
        for _ in range(180):
            if proc.poll() is not None:
                raise GateError(f"app-{name} exited early with code {proc.returncode}")
            try:
                code, _ = http_json(port, "/api/health")
                if code == 200:
                    break
            except GateError:
                pass
            time.sleep(0.5)
        else:
            raise GateError(f"app-{name} startup timed out")
        emit("app_started", instance=name, pid=proc.pid, port=port)
        return proc, log_path

    def must_status(step: str, actual: int, expected: int) -> None:
        ok = actual == expected
        emit("check", step=step, actual=actual, expected=expected, passed=ok)
        if not ok:
            raise GateError(f"precheck {step} failed: expected {expected}, got {actual}")

    result = GateResult("ERROR", EXIT_ERROR, "not_completed", None, None, None, False)
    redis_logs: list[pathlib.Path] = []
    app_logs: list[pathlib.Path] = []
    dir_evidence: dict[str, Any] = {}

    try:
        rp, initial_dir, initial_conf, rlog_init = start_redis("initial")
        redis_logs.append(rlog_init)
        app_a, alog_a = start_app("A1", aport)
        app_b, alog_b = start_app("B1", bport)
        app_logs.extend([alog_a, alog_b])

        # 1. Login on A with synthetic account `population` and verify valid before logout on A & B.
        login_status, login_body = http_json(aport, "/api/auth/login", body={"username": "population", "password": "123456"})
        must_status("login_population", login_status, 200)
        old_token = json.loads(login_body)["data"]["token"]
        old_claims = decode_jwt_claims(old_token)
        old_jti = old_claims["jti"]
        old_jti_digest = short_jti_digest(old_jti)
        emit("token_issued", role="old", jti_sha256_prefix=old_jti_digest)

        must_status("A_before_logout", http_json(aport, "/api/auth/me", old_token)[0], 200)
        must_status("B_before_logout", http_json(bport, "/api/auth/me", old_token)[0], 200)

        # 2. Logout on A -> A & B both reject with 401.
        must_status("A_logout", http_json(aport, "/api/auth/logout", old_token, {})[0], 200)
        key_raw = f"{prefix}:security:jwt:blacklist:{old_jti}"
        key_sha = f"{prefix}:security:jwt:blacklist:{hashlib.sha256(old_jti.encode('utf-8')).hexdigest()}"
        key = key_raw
        if stage == STAGE_BASELINE_RED:
            marker_exists = redis_cmd(rport, admin_password, "EXISTS", key_raw)
        else:
            raw_exists = redis_cmd(rport, admin_password, "EXISTS", key_raw)
            sha_exists = redis_cmd(rport, admin_password, "EXISTS", key_sha)
            key = key_sha if sha_exists == 1 else key_raw
            marker_exists = 1 if (raw_exists == 1 or sha_exists == 1) else 0
        if marker_exists != 1:
            raise GateError(f"expected redis marker EXISTS=1, got {marker_exists}")
        must_status("A_after_logout", http_json(aport, "/api/auth/me", old_token)[0], 401)
        must_status("B_after_logout", http_json(bport, "/api/auth/me", old_token)[0], 401)

        # 3. Fresh login issues a distinct JTI isolated from the revoked JTI.
        f_status, f_body = http_json(aport, "/api/auth/login", body={"username": "population", "password": "123456"})
        must_status("login_fresh", f_status, 200)
        fresh_token = json.loads(f_body)["data"]["token"]
        fresh_jti = decode_jwt_claims(fresh_token)["jti"]
        if fresh_jti == old_jti:
            raise GateError("fresh login reused revoked JTI")
        emit("token_issued", role="fresh", jti_sha256_prefix=short_jti_digest(fresh_jti), jti_distinct=True)
        must_status("B_fresh_valid", http_json(bport, "/api/auth/me", fresh_token)[0], 200)

        # 4. Explicit SAVE, shutdown Redis, verify outage behavior per stage and 200 on /api/health.
        if redis_cmd(rport, admin_password, "SAVE") != b"OK":
            raise GateError("explicit SAVE did not return OK")
        initial_dbsize = redis_cmd(rport, admin_password, "DBSIZE")
        try:
            redis_cmd(rport, admin_password, "SHUTDOWN", "NOSAVE")
        except (OSError, GateError):
            pass
        rp.wait(timeout=20)
        rdb_path = initial_dir / "dump.rdb"
        if not rdb_path.exists():
            raise GateError("dump.rdb missing after explicit SAVE")
        dir_evidence["initial"] = {
            "dir": str(initial_dir),
            "dbsize_before_shutdown": initial_dbsize,
            "dump_rdb_exists": True,
            "dump_rdb_bytes": rdb_path.stat().st_size,
            "dump_rdb_sha256": sha256_file(rdb_path),
        }
        emit("redis_down_after_save", **dir_evidence["initial"])

        if stage == STAGE_BASELINE_RED:
            must_status("B_during_outage_fresh", http_json(bport, "/api/auth/me", fresh_token)[0], 503)
        else:
            must_status("B_during_outage_old", http_json(bport, "/api/auth/me", old_token)[0], 401)
            must_status("B_during_outage_fresh", http_json(bport, "/api/auth/me", fresh_token)[0], 200)
        must_status("A_during_outage_health", http_json(aport, "/api/health")[0], 200)

        # 5. Restart Redis on the SAME initial directory; verify preserved RDB rejects old_token (401)
        #    while explicitly marking that clean SAVE+shutdown recovery is NOT durability proof.
        rlog_restart = run_dir / "redis-restart.log"
        redis_logs.append(rlog_restart)
        fh_restart = rlog_restart.open("wb")
        open_files.append(fh_restart)
        rp = subprocess.Popen(
            [str(redis_bin), to_cyg_path(initial_conf)],
            stdout=fh_restart,
            stderr=subprocess.STDOUT,
            cwd=str(run_dir),
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        processes.append(rp)
        for _ in range(100):
            try:
                if redis_cmd(rport, admin_password, "PING") == b"PONG":
                    break
            except (OSError, GateError):
                time.sleep(0.1)
        else:
            raise GateError("redis restart on initial dir timed out")

        # Wait until Lettuce reconnects.
        for _ in range(60):
            code, _ = http_json(aport, "/api/auth/me", old_token)
            if code != 503:
                break
            time.sleep(0.25)
        must_status("A_after_preserved_rdb_old", http_json(aport, "/api/auth/me", old_token)[0], 401)
        must_status("B_after_preserved_rdb_old", http_json(bport, "/api/auth/me", old_token)[0], 401)
        must_status("B_after_preserved_rdb_fresh", http_json(bport, "/api/auth/me", fresh_token)[0], 200)
        emit("preserved_rdb_observation", durable_guarantee_proven=False)

        # 6. Stop Redis and restart on a NEW EMPTY directory to simulate persisted data loss.
        stop_proc(rp)
        rp, empty_dir, _, rlog_empty = start_redis("empty")
        redis_logs.append(rlog_empty)

        cfg_dir_reply = redis_cmd(rport, admin_password, "CONFIG", "GET", "dir")
        reported_dir = cfg_dir_reply[1].decode("utf-8", errors="replace") if isinstance(cfg_dir_reply, list) and len(cfg_dir_reply) >= 2 else ""
        empty_dbsize = redis_cmd(rport, admin_password, "DBSIZE")
        empty_marker_exists = redis_cmd(rport, admin_password, "EXISTS", key)
        empty_rdb_exists = (empty_dir / "dump.rdb").exists()
        expected_posix = empty_dir.resolve().as_posix().lower()
        expected_cyg = to_cyg_path(empty_dir).lower()
        reported_norm = reported_dir.replace("\\", "/").lower()
        empty_dir_verified = (
            str(empty_dir) != str(initial_dir)
            and empty_dbsize == 0
            and empty_marker_exists == 0
            and not empty_rdb_exists
            and reported_norm in (expected_posix, expected_cyg)
        )
        dir_evidence["empty"] = {
            "dir": str(empty_dir),
            "redis_reported_dir": reported_dir,
            "dbsize": empty_dbsize,
            "revocation_key_exists": empty_marker_exists,
            "dump_rdb_exists": empty_rdb_exists,
            "verified_switch": empty_dir_verified,
        }
        emit("redis_empty_dir_switch", **dir_evidence["empty"])

        result = verify_post_empty_recovery_over_http(
            aport=aport,
            bport=bport,
            old_token=old_token,
            fresh_token=fresh_token,
            prechecks_ok=True,
            empty_dir_verified=empty_dir_verified,
            wait_timeout_s=20.0,
        )
        emit(
            "FINAL_VERDICT",
            verdict=result.verdict,
            exit_code=result.exit_code,
            reason=result.reason,
            a_old_status=result.a_status,
            b_old_status=result.b_status,
            fresh_b_status=result.fresh_b_status,
            vulnerability_reproduced=result.vulnerability_reproduced,
        )
    except Exception as exc:
        result = GateResult("ERROR", EXIT_ERROR, f"{type(exc).__name__}: {exc}", None, None, None, False)
        emit("FINAL_VERDICT", verdict=result.verdict, exit_code=result.exit_code, reason=result.reason)
    finally:
        for proc in reversed(processes):
            try:
                stop_proc(proc)
            except Exception as err:
                emit("process_stop_error", pid=proc.pid, error=str(err))
        for fh in open_files:
            try:
                fh.close()
            except Exception:
                pass
        emit("own_processes_stopped", count=len(processes))

        log_hashes: dict[str, str] = {}
        for p in redis_logs + app_logs:
            if p.exists():
                log_hashes[p.name] = sha256_file(p)

        summary = {
            "stage": "BASELINE_RED" if stage == STAGE_BASELINE_RED else "TARGET_GREEN",
            "revocation_mode": revocation_mode,
            "shared_db_configured": stage == STAGE_TARGET_GREEN,
            "run_id": run_id,
            "runtime_dir": str(run_dir),
            "ports": {"redis": rport, "app_a": aport, "app_b": bport},
            "key_prefix": prefix,
            "dir_evidence": dir_evidence,
            "verdict": result.verdict,
            "exit_code": result.exit_code,
            "reason": result.reason,
            "a_old_status": result.a_status,
            "b_old_status": result.b_status,
            "fresh_b_status": result.fresh_b_status,
            "vulnerability_reproduced": result.vulnerability_reproduced,
            "log_sha256": log_hashes,
            "events": events,
        }
        summary_path = run_dir / "gate-summary.json"
        summary_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        if evidence_out is not None:
            evidence_out.parent.mkdir(parents=True, exist_ok=True)
            evidence_out.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    return result.exit_code


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Issue #64 dual-JVM + isolated Redis durability gate")
    parser.add_argument(
        "--stage",
        choices=[STAGE_BASELINE_RED, STAGE_TARGET_GREEN],
        default=STAGE_BASELINE_RED,
        help="Verification stage: baseline-red (SHARED_REDIS_REQUIRED) or target-green (DURABLE_DB_REQUIRED + shared DB)",
    )
    parser.add_argument("--shared-jdbc-url", default=os.environ.get("ISSUE64_SHARED_JDBC_URL"), help="Shared authoritative JDBC URL (target-green only; rejects jdbc:h2:mem:)")
    parser.add_argument("--shared-db-user", default=os.environ.get("ISSUE64_SHARED_DB_USER"), help="Shared DB username (target-green only)")
    parser.add_argument("--shared-db-password-env", default="ISSUE64_SHARED_DB_PASSWORD", help="Env var holding shared DB password (target-green only)")
    parser.add_argument("--repo-root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[2])
    parser.add_argument("--redis-bin", type=pathlib.Path, default=pathlib.Path(os.environ.get("ISSUE64_REDIS_BIN", str(DEFAULT_REDIS_BIN))))
    parser.add_argument("--classpath-file", type=pathlib.Path, default=None)
    parser.add_argument("--runtime-dir", type=pathlib.Path, default=pathlib.Path(tempfile.gettempdir()) / "issue64-durability-red")
    parser.add_argument("--evidence-out", type=pathlib.Path, default=None)
    args = parser.parse_args(argv)

    cp_file = args.classpath_file or (args.repo_root / "target" / "issue64-classpath.txt")
    shared_db_password = os.environ.get(args.shared_db_password_env) if args.stage == STAGE_TARGET_GREEN else None
    try:
        return run_live_gate(
            repo_root=args.repo_root,
            redis_bin=args.redis_bin,
            cp_file=cp_file,
            runtime_root=args.runtime_dir,
            evidence_out=args.evidence_out,
            stage=args.stage,
            shared_jdbc_url=args.shared_jdbc_url,
            shared_db_user=args.shared_db_user,
            shared_db_password=shared_db_password,
        )
    except GateError as exc:
        print(
            json.dumps(
                {
                    "event": "FINAL_VERDICT",
                    "stage": "BASELINE_RED" if args.stage == STAGE_BASELINE_RED else "TARGET_GREEN",
                    "verdict": "ERROR",
                    "exit_code": EXIT_ERROR,
                    "reason": str(exc),
                },
                ensure_ascii=False,
            ),
            flush=True,
        )
        return EXIT_ERROR


if __name__ == "__main__":
    sys.exit(main())
