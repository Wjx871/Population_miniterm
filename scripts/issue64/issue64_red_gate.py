"""Executable #64 durability gate (dual JVM + isolated Redis 8.10.3).

Supports two explicitly separated verification stages:
  baseline-red : Runs against legacy SHARED_REDIS_REQUIRED mode to reproduce the
                 unexpired-JWT resurrection after empty Redis recovery (RED = exit 2).
  target-green : Runs against future DURABLE_DB_REQUIRED mode backed by a single shared
                 authoritative persistent database (isolated MySQL 8.4 test DB).

Distinguishes three strictly separate verification levels (neither of the first two
can ever substitute for the global TARGET_GREEN_PASS):
  1. TARGET_PREREQUISITE_CHECK : Static/preflight DB & mode configuration checks.
  2. TARGET_SCENARIO_PASS      : HTTP scenario pass (e.g. post-empty-Redis 401/200).
  3. TARGET_GREEN_PASS         : Global PASS (exit 0) ONLY when ALL 7 independent
                                 criteria pass dynamically without any FAIL or NOT_RUN.

Classifies outcomes strictly into:
  PASS       (exit 0): Under baseline-red, post-empty-recovery rejects old JWT (401/401)
                       with fresh JWT 200; under target-green, ALL 7 independent
                       criteria are dynamically observed as PASS.
  RED        (exit 2): Under baseline-red, prechecks/env ok, fresh JWT returns 200,
                       old JWT statuses on A/B are in {200, 401}, at least one is 200.
  INCOMPLETE (exit 1): Under target-green, zero criteria failed but >=1 required
                       criterion remains NOT_RUN (e.g. MySQL fault or checkpoint checks).
  ERROR      (exit 1): Environment/harness failure, missing opt-in/prereq, DB isolation
                       or identity failure, unexpected HTTP 500/503, or failed criterion.

No full JWT, signing secret, DB credential, or Redis password is ever logged.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import pathlib
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from typing import Any, Callable

EXIT_PASS = 0
EXIT_ERROR = 1
EXIT_RED = 2

STAGE_BASELINE_RED = "baseline-red"
STAGE_TARGET_GREEN = "target-green"

ISOLATED_TEST_DB_PATTERN = re.compile(r"^issue64_test_[a-z0-9_]{4,40}$")
FORBIDDEN_PROD_DB_NAMES = frozenset({"population_miniterm", "mysql", "information_schema", "performance_schema", "sys"})
FORBIDDEN_PRIVILEGED_DB_USERS = frozenset({"root", "admin", "sa", "rdsadmin"})
LOOPBACK_HOSTS = frozenset({"127.0.0.1", "localhost", "::1"})

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
    verdict: str  # PASS | RED | INCOMPLETE | ERROR
    exit_code: int
    reason: str
    a_status: int | None
    b_status: int | None
    fresh_b_status: int | None
    vulnerability_reproduced: bool


TARGET_GREEN_CRITERIA_ORDER = (
    "durable_db_mode_active",
    "shared_authoritative_db",
    "mysql_revocation_committed",
    "post_empty_redis_old_jwt_401",
    "post_empty_redis_fresh_jwt_200",
    "mysql_fault_fail_closed",
    "checkpoint_invalid_fail_closed",
)

LEVEL_PREREQUISITE_CHECK = "TARGET_PREREQUISITE_CHECK"
LEVEL_SCENARIO_PASS = "TARGET_SCENARIO_PASS"
LEVEL_GREEN_PASS = "TARGET_GREEN_PASS"

VALID_CRITERION_STATES = frozenset({"PASS", "FAIL", "NOT_RUN"})


@dataclass
class TargetGreenContractResult:
    verdict: str  # PASS | INCOMPLETE | ERROR
    exit_code: int
    reason: str
    levels: dict[str, str]
    criteria: dict[str, str]
    incomplete_criteria: list[str]
    failed_criteria: list[str]
    target_green_pass: bool


def evaluate_mysql_fault_observation(observation: dict[str, Any] | None) -> str:
    """Evaluate MySQL fault injection evidence strictly: missing/unexecuted -> NOT_RUN; non-503 -> FAIL."""
    if not observation or not observation.get("executed"):
        return "NOT_RUN"
    auth_status = observation.get("auth_status")
    logout_status = observation.get("logout_status")
    login_status = observation.get("login_status")
    if auth_status != 503 or logout_status != 503:
        return "FAIL"
    if login_status is not None and login_status != 503:
        return "FAIL"
    if observation.get("passed") is False:
        return "FAIL"
    return "PASS"


def evaluate_checkpoint_fault_observation(observation: dict[str, Any] | None) -> str:
    """Evaluate checkpoint missing/UNINITIALIZED/INVALID fail-closed evidence: non-503 -> FAIL."""
    if not observation or not observation.get("executed"):
        return "NOT_RUN"
    missing_status = observation.get("missing_checkpoint_status")
    invalid_status = observation.get("invalid_checkpoint_status")
    if missing_status != 503 or invalid_status != 503:
        return "FAIL"
    uninit_status = observation.get("uninitialized_checkpoint_status")
    if uninit_status is not None and uninit_status != 503:
        return "FAIL"
    if observation.get("passed") is False:
        return "FAIL"
    return "PASS"


def evaluate_target_green_contract(
    criteria: dict[str, str],
    *,
    prerequisite_passed: bool = True,
    scenario_result: GateResult | None = None,
) -> TargetGreenContractResult:
    """Strict 7-criterion contract evaluator separating PREREQUISITE, SCENARIO_PASS, and TARGET_GREEN_PASS.

    Global PASS (exit 0) requires ALL 7 independent criteria == 'PASS' plus
    prerequisite_passed == True and (if provided) scenario_result.verdict == 'PASS'.
    Any NOT_RUN criterion yields verdict='INCOMPLETE' (exit 1); any FAIL yields 'ERROR' (exit 1).
    """
    normalized: dict[str, str] = {}
    for key in TARGET_GREEN_CRITERIA_ORDER:
        raw = str(criteria.get(key, "NOT_RUN")).strip().upper()
        normalized[key] = raw if raw in VALID_CRITERION_STATES else "NOT_RUN"

    failed = [key for key in TARGET_GREEN_CRITERIA_ORDER if normalized[key] == "FAIL"]
    incomplete = [key for key in TARGET_GREEN_CRITERIA_ORDER if normalized[key] == "NOT_RUN"]

    scenario_core_keys = (
        "durable_db_mode_active",
        "shared_authoritative_db",
        "mysql_revocation_committed",
        "post_empty_redis_old_jwt_401",
        "post_empty_redis_fresh_jwt_200",
    )
    if scenario_result is not None and scenario_result.verdict != "PASS":
        scenario_level = "FAIL"
    elif any(normalized[k] == "FAIL" for k in scenario_core_keys):
        scenario_level = "FAIL"
    elif all(normalized[k] == "PASS" for k in scenario_core_keys):
        scenario_level = "PASS"
    else:
        scenario_level = "NOT_RUN"

    prereq_level = "PASS" if prerequisite_passed else "FAIL"

    if not prerequisite_passed:
        return TargetGreenContractResult(
            verdict="ERROR",
            exit_code=EXIT_ERROR,
            reason="target_green_prerequisite_failed",
            levels={
                LEVEL_PREREQUISITE_CHECK: prereq_level,
                LEVEL_SCENARIO_PASS: scenario_level,
                LEVEL_GREEN_PASS: "FAIL",
            },
            criteria=normalized,
            incomplete_criteria=incomplete,
            failed_criteria=failed,
            target_green_pass=False,
        )

    if failed or (scenario_result is not None and scenario_result.verdict != "PASS"):
        reason_detail = ",".join(failed) if failed else (scenario_result.reason if scenario_result else "scenario_failed")
        return TargetGreenContractResult(
            verdict="ERROR",
            exit_code=EXIT_ERROR,
            reason=f"target_green_criteria_failed:{reason_detail}",
            levels={
                LEVEL_PREREQUISITE_CHECK: prereq_level,
                LEVEL_SCENARIO_PASS: scenario_level,
                LEVEL_GREEN_PASS: "FAIL",
            },
            criteria=normalized,
            incomplete_criteria=incomplete,
            failed_criteria=failed,
            target_green_pass=False,
        )

    if incomplete:
        return TargetGreenContractResult(
            verdict="INCOMPLETE",
            exit_code=EXIT_ERROR,
            reason=f"target_green_incomplete:missing[{','.join(incomplete)}]",
            levels={
                LEVEL_PREREQUISITE_CHECK: prereq_level,
                LEVEL_SCENARIO_PASS: scenario_level,
                LEVEL_GREEN_PASS: "INCOMPLETE",
            },
            criteria=normalized,
            incomplete_criteria=incomplete,
            failed_criteria=failed,
            target_green_pass=False,
        )

    return TargetGreenContractResult(
        verdict="PASS",
        exit_code=EXIT_PASS,
        reason="all_seven_target_green_criteria_passed",
        levels={
            LEVEL_PREREQUISITE_CHECK: prereq_level,
            LEVEL_SCENARIO_PASS: scenario_level,
            LEVEL_GREEN_PASS: "PASS",
        },
        criteria=normalized,
        incomplete_criteria=[],
        failed_criteria=[],
        target_green_pass=True,
    )


def observe_target_redis_marker(
    redis_cmd_fn: Callable[..., Any],
    rport: int,
    admin_password: str,
    key_raw: str,
    key_sha: str,
) -> dict[str, Any]:
    """Non-blocking cache marker observation under TARGET_GREEN.

    Redis EXISTS absence, connection error, or timeout must NEVER directly fail
    durable revocation or MySQL verification.
    """
    try:
        raw_exists = redis_cmd_fn(rport, admin_password, "EXISTS", key_raw)
        sha_exists = redis_cmd_fn(rport, admin_password, "EXISTS", key_sha)
    except (GateError, OSError, TimeoutError) as exc:
        return {
            "key": key_raw,
            "redis_marker_exists": None,
            "observed_state": f"UNAVAILABLE:{type(exc).__name__}",
            "observation_error": str(exc),
            "required_for_green": False,
        }
    exists_flag = 1 if (raw_exists == 1 or sha_exists == 1) else 0
    return {
        "key": key_sha if sha_exists == 1 else key_raw,
        "redis_marker_exists": exists_flag,
        "observed_state": "PRESENT" if exists_flag == 1 else "ABSENT",
        "observation_error": None,
        "required_for_green": False,
    }


def parse_isolated_mysql_jdbc_url(
    jdbc_url: str | None,
    approved_remote_hosts: set[str] | frozenset[str] | None = None,
) -> tuple[str, int, str]:
    """Validate and parse a target-green JDBC URL into (host, port, database_name).

    Preflight guardrails only (NEVER a substitute for physical process/container isolation):
      - Rejects jdbc:h2:mem: and any non-jdbc:mysql:// URL.
      - Explicitly forbids production database name `population_miniterm` and system schemas.
      - Enforces dedicated test schema pattern `^issue64_test_[a-z0-9_]{4,40}$`.
      - Restricts target host to loopback unless explicitly listed in approved_remote_hosts.
    """
    if not jdbc_url or not jdbc_url.strip():
        raise GateError("target_green_prereq_unmet: --shared-jdbc-url is required for target-green stage")
    raw = jdbc_url.strip()
    lower = raw.lower()
    if lower.startswith("jdbc:h2:mem:"):
        raise GateError("target_db_isolation_error: isolated in-memory H2 cannot stand in for a shared persistent database")
    if not lower.startswith("jdbc:mysql://"):
        raise GateError("target_db_isolation_error: only jdbc:mysql:// URLs targeting an isolated MySQL 8.4 test DB are permitted")

    parsed = urllib.parse.urlsplit(raw[len("jdbc:"):])
    host = (parsed.hostname or "").strip().lower()
    if not host:
        raise GateError("target_db_isolation_error: missing host in target JDBC URL")
    port = parsed.port or 3306

    approved = {h.strip().lower() for h in (approved_remote_hosts or ()) if h and h.strip()}
    if host not in LOOPBACK_HOSTS and host not in approved:
        raise GateError(
            f"target_db_isolation_error: non-loopback host '{host}' is not in approved isolated test DB hosts"
        )

    db_name = parsed.path.lstrip("/").split("/", 1)[0].strip()
    if not db_name:
        raise GateError("target_db_isolation_error: missing database name in target JDBC URL")
    if db_name.lower() in FORBIDDEN_PROD_DB_NAMES or "population_miniterm" in lower:
        raise GateError(f"target_db_isolation_error: forbidden production/system database name '{db_name}'")
    if not ISOLATED_TEST_DB_PATTERN.fullmatch(db_name):
        raise GateError(
            f"target_db_isolation_error: database name '{db_name}' must match dedicated pattern issue64_test_<id>"
        )
    return host, port, db_name


def default_db_identity_verifier(
    jdbc_url: str,
    db_user: str,
    db_password: str | None,
    expected_db_name: str,
) -> dict[str, Any]:
    """Fail-closed placeholder until real MySQL target probe is executed on a live test instance.

    Checks target DB identity via pymysql if installed; otherwise fails closed with ERROR so
    unverified target databases can never launch test JVMs or claim TARGET_GREEN.
    """
    try:
        import pymysql  # type: ignore[import-not-found]
    except ImportError as exc:
        raise GateError(
            "target_db_identity_unverified: cannot verify live MySQL test DB identity (pymysql driver unavailable)"
        ) from exc

    if not db_password:
        raise GateError("target_db_identity_unverified: DB password env var is empty")
    host, port, db_name = parse_isolated_mysql_jdbc_url(jdbc_url, approved_remote_hosts={urllib.parse.urlsplit(jdbc_url[len("jdbc:"):]).hostname or ""})
    if db_name != expected_db_name:
        raise GateError(f"target_db_identity_mismatch: expected {expected_db_name}, parsed {db_name}")
    try:
        conn = pymysql.connect(host=host, port=port, user=db_user, password=db_password, database=db_name, connect_timeout=5, read_timeout=5)
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT DATABASE(), @@hostname, @@port, @@read_only")
                row = cur.fetchone()
        finally:
            conn.close()
    except Exception as exc:
        raise GateError(f"target_db_identity_unverified: failed connecting to verify target DB: {type(exc).__name__}") from exc

    if not row or row[0] != expected_db_name or (row[0] or "").lower() in FORBIDDEN_PROD_DB_NAMES:
        raise GateError(f"target_db_identity_mismatch: server reported database={row[0] if row else None}")
    if row[3] == 1:
        raise GateError("target_db_identity_error: target database is read_only=1, expected primary RW test instance")
    return {"database": row[0], "port": row[2], "verified": True}


def default_db_revocation_verifier(
    jdbc_url: str,
    db_user: str,
    db_password: str | None,
    expected_db_name: str,
    jti_sha256: str,
) -> bool:
    """Verify authoritative MySQL jwt_revocation row exists for sha256(jti) under TARGET_GREEN."""
    try:
        import pymysql  # type: ignore[import-not-found]
    except ImportError as exc:
        raise GateError(
            "target_db_revocation_unverified: cannot query MySQL jwt_revocation (pymysql driver unavailable)"
        ) from exc

    if not db_password:
        raise GateError("target_db_revocation_unverified: DB password env var is empty")
    host, port, db_name = parse_isolated_mysql_jdbc_url(jdbc_url, approved_remote_hosts={urllib.parse.urlsplit(jdbc_url[len("jdbc:"):]).hostname or ""})
    try:
        conn = pymysql.connect(host=host, port=port, user=db_user, password=db_password, database=db_name, connect_timeout=5, read_timeout=5)
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = %s AND expires_at_epoch_ms > (UNIX_TIMESTAMP() * 1000)",
                    (jti_sha256,),
                )
                row = cur.fetchone()
        finally:
            conn.close()
    except Exception as exc:
        raise GateError(f"target_db_revocation_unverified: authoritative DB query failed: {type(exc).__name__}") from exc
    return bool(row and int(row[0]) >= 1)


def validate_stage_requirements(
    stage: str,
    repo_root: pathlib.Path,
    shared_jdbc_url: str | None,
    shared_db_user: str | None,
    *,
    allow_target_green: bool = False,
    approved_remote_hosts: set[str] | frozenset[str] | None = None,
    shared_db_password: str | None = None,
    db_identity_verifier: Callable[[str, str, str | None, str], dict[str, Any]] | None = None,
    verify_identity: bool = True,
) -> str:
    """Validate stage prerequisites and return the required revocation mode.

    BASELINE_RED strictly runs SHARED_REDIS_REQUIRED (isolated H2 per JVM permitted).
    TARGET_GREEN enforces explicit opt-in, isolated test DB naming/host guardrails,
    pre-launch DB identity verification, and DURABLE_DB_REQUIRED runtime implementation.
    Static source checks are ONLY fast preflight checks (TARGET_PREREQUISITE_CHECK),
    never proof that TARGET_GREEN passes.
    """
    if stage == STAGE_BASELINE_RED:
        return "SHARED_REDIS_REQUIRED"
    if stage != STAGE_TARGET_GREEN:
        raise GateError(f"unsupported_stage: {stage}")

    if not allow_target_green:
        raise GateError(
            "target_green_opt_in_required: target-green requires explicit --allow-target-green (or ISSUE64_ALLOW_TARGET_GREEN=true)"
        )

    props_file = repo_root / "src" / "main" / "java" / "com" / "wjx871" / "population" / "security" / "RevocationProperties.java"
    if not props_file.exists():
        raise GateError(f"missing RevocationProperties.java: {props_file}")
    props_text = props_file.read_text(encoding="utf-8")
    if "DURABLE_DB_REQUIRED" not in props_text:
        raise GateError("target_green_prereq_unmet: DURABLE_DB_REQUIRED is not implemented yet in RevocationProperties")

    _, _, db_name = parse_isolated_mysql_jdbc_url(shared_jdbc_url, approved_remote_hosts=approved_remote_hosts)

    if not shared_db_user or not shared_db_user.strip():
        raise GateError("target_db_isolation_error: --shared-db-user is required for target-green stage")
    if shared_db_user.strip().lower() in FORBIDDEN_PRIVILEGED_DB_USERS:
        raise GateError("target_db_isolation_error: dedicated isolated test DB account required (privileged user forbidden)")

    if verify_identity:
        verifier = db_identity_verifier or default_db_identity_verifier
        assert shared_jdbc_url is not None
        identity = verifier(shared_jdbc_url.strip(), shared_db_user.strip(), shared_db_password, db_name)
        if not identity or not identity.get("verified") or identity.get("database") != db_name:
            raise GateError("target_db_identity_unverified: pre-launch DB identity verification did not confirm test DB")

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
    allow_target_green: bool = False,
    approved_remote_hosts: set[str] | frozenset[str] | None = None,
    db_identity_verifier: Callable[[str, str, str | None, str], dict[str, Any]] | None = None,
    db_revocation_verifier: Callable[[str, str, str | None, str, str], bool] | None = None,
    mysql_fault_verifier: Callable[[dict[str, Any]], dict[str, Any]] | None = None,
    checkpoint_fault_verifier: Callable[[dict[str, Any]], dict[str, Any]] | None = None,
) -> int:
    revocation_mode = validate_stage_requirements(
        stage,
        repo_root,
        shared_jdbc_url,
        shared_db_user,
        allow_target_green=allow_target_green,
        approved_remote_hosts=approved_remote_hosts,
        shared_db_password=shared_db_password,
        db_identity_verifier=db_identity_verifier,
        verify_identity=True,
    )
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
    db_identity_verified = False
    db_revocation_verified = False
    redis_marker_observed: int | str = "NOT_CHECKED"
    target_criteria: dict[str, str] = {key: "NOT_RUN" for key in TARGET_GREEN_CRITERIA_ORDER}
    target_contract: TargetGreenContractResult | None = None

    try:
        rp, initial_dir, initial_conf, rlog_init = start_redis("initial")
        redis_logs.append(rlog_init)
        app_a, alog_a = start_app("A1", aport)
        app_b, alog_b = start_app("B1", bport)
        app_logs.extend([alog_a, alog_b])

        expected_db_name = ""
        if stage == STAGE_TARGET_GREEN:
            assert shared_jdbc_url is not None and shared_db_user is not None
            _, _, expected_db_name = parse_isolated_mysql_jdbc_url(
                shared_jdbc_url, approved_remote_hosts=approved_remote_hosts
            )
            db_identity_verified = True
            target_criteria["shared_authoritative_db"] = "PASS"
            for inst, port in (("A1", aport), ("B1", bport)):
                h_code, h_body = http_json(port, "/api/health")
                must_status(f"{inst}_target_health", h_code, 200)
                h_data = json.loads(h_body).get("data", {})
                if h_data.get("revocationMode") != "DURABLE_DB_REQUIRED":
                    target_criteria["durable_db_mode_active"] = "FAIL"
                    raise GateError(
                        f"target_mode_not_active_at_runtime: {inst} reported revocationMode={h_data.get('revocationMode')}"
                    )
            target_criteria["durable_db_mode_active"] = "PASS"

        # 1. Login on A with synthetic account `population` and verify valid before logout on A & B.
        login_status, login_body = http_json(aport, "/api/auth/login", body={"username": "population", "password": "123456"})
        must_status("login_population", login_status, 200)
        old_token = json.loads(login_body)["data"]["token"]
        old_claims = decode_jwt_claims(old_token)
        old_jti = old_claims["jti"]
        old_jti_sha256 = hashlib.sha256(old_jti.encode("utf-8")).hexdigest()
        old_jti_digest = short_jti_digest(old_jti)
        emit("token_issued", role="old", jti_sha256_prefix=old_jti_digest)

        must_status("A_before_logout", http_json(aport, "/api/auth/me", old_token)[0], 200)
        must_status("B_before_logout", http_json(bport, "/api/auth/me", old_token)[0], 200)

        # 2. Logout on A -> A & B both reject with 401.
        must_status("A_logout", http_json(aport, "/api/auth/logout", old_token, {})[0], 200)
        key_raw = f"{prefix}:security:jwt:blacklist:{old_jti}"
        key_sha = f"{prefix}:security:jwt:blacklist:{old_jti_sha256}"
        key = key_raw
        if stage == STAGE_BASELINE_RED:
            marker_exists = redis_cmd(rport, admin_password, "EXISTS", key_raw)
            redis_marker_observed = marker_exists
            if marker_exists != 1:
                raise GateError(f"expected redis marker EXISTS=1, got {marker_exists}")
        else:
            # Non-blocking cache observation under TARGET_GREEN: missing OR EXISTS failure never fails DB verification.
            marker_obs = observe_target_redis_marker(redis_cmd, rport, admin_password, key_raw, key_sha)
            key = marker_obs["key"]
            redis_marker_observed = (
                marker_obs["redis_marker_exists"]
                if marker_obs["redis_marker_exists"] is not None
                else marker_obs["observed_state"]
            )
            emit(
                "target_cache_observation",
                redis_marker_exists=marker_obs["redis_marker_exists"],
                observed_state=marker_obs["observed_state"],
                required_for_green=False,
            )

            rev_checker = db_revocation_verifier or default_db_revocation_verifier
            assert shared_jdbc_url is not None and shared_db_user is not None
            db_revocation_verified = rev_checker(
                shared_jdbc_url.strip(), shared_db_user.strip(), shared_db_password, expected_db_name, old_jti_sha256
            )
            if not db_revocation_verified:
                target_criteria["mysql_revocation_committed"] = "FAIL"
                raise GateError("target_db_revocation_missing: logout returned 200 but jwt_revocation row is absent")
            target_criteria["mysql_revocation_committed"] = "PASS"
            emit("target_db_revocation_verified", jti_sha256_prefix=old_jti_digest, verified=True)
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
            # Under TARGET_GREEN (DURABLE_DB_REQUIRED), Redis outage must NOT block valid tokens or logout.
            must_status("B_during_outage_old", http_json(bport, "/api/auth/me", old_token)[0], 401)
            must_status("B_during_outage_fresh", http_json(bport, "/api/auth/me", fresh_token)[0], 200)
            outage_login_status, outage_login_body = http_json(aport, "/api/auth/login", body={"username": "population", "password": "123456"})
            must_status("A_during_outage_login", outage_login_status, 200)
            outage_token = json.loads(outage_login_body)["data"]["token"]
            outage_jti_sha256 = hashlib.sha256(decode_jwt_claims(outage_token)["jti"].encode("utf-8")).hexdigest()
            must_status("A_during_outage_logout", http_json(aport, "/api/auth/logout", outage_token, {})[0], 200)
            rev_checker = db_revocation_verifier or default_db_revocation_verifier
            assert shared_jdbc_url is not None and shared_db_user is not None
            if not rev_checker(shared_jdbc_url.strip(), shared_db_user.strip(), shared_db_password, expected_db_name, outage_jti_sha256):
                raise GateError("target_db_revocation_missing: outage logout returned 200 without DB commit")
            must_status("B_during_outage_revoked_without_redis", http_json(bport, "/api/auth/me", outage_token)[0], 401)
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

        if stage == STAGE_TARGET_GREEN:
            rev_checker = db_revocation_verifier or default_db_revocation_verifier
            assert shared_jdbc_url is not None and shared_db_user is not None
            db_revocation_verified = rev_checker(
                shared_jdbc_url.strip(), shared_db_user.strip(), shared_db_password, expected_db_name, old_jti_sha256
            )
            if not db_revocation_verified:
                target_criteria["mysql_revocation_committed"] = "FAIL"
                raise GateError("target_db_revocation_missing: cannot verify authoritative DB record post empty recovery")
            target_criteria["mysql_revocation_committed"] = "PASS"

        scenario_result = verify_post_empty_recovery_over_http(
            aport=aport,
            bport=bport,
            old_token=old_token,
            fresh_token=fresh_token,
            prechecks_ok=True,
            empty_dir_verified=empty_dir_verified,
            wait_timeout_s=20.0,
        )

        if stage == STAGE_BASELINE_RED:
            result = scenario_result
            emit(
                "FINAL_VERDICT",
                verdict=result.verdict,
                exit_code=result.exit_code,
                reason=result.reason,
                a_old_status=result.a_status,
                b_old_status=result.b_status,
                fresh_b_status=result.fresh_b_status,
                vulnerability_reproduced=result.vulnerability_reproduced,
                verification_levels={
                    LEVEL_PREREQUISITE_CHECK: "NOT_RUN",
                    LEVEL_SCENARIO_PASS: "NOT_RUN",
                    LEVEL_GREEN_PASS: "NOT_RUN",
                },
            )
        else:
            if scenario_result.verdict == "PASS":
                target_criteria["post_empty_redis_old_jwt_401"] = "PASS"
                target_criteria["post_empty_redis_fresh_jwt_200"] = "PASS"
            else:
                target_criteria["post_empty_redis_old_jwt_401"] = "FAIL"
                target_criteria["post_empty_redis_fresh_jwt_200"] = (
                    "PASS" if scenario_result.fresh_b_status == 200 else "FAIL"
                )

            if mysql_fault_verifier is not None:
                mysql_fault_obs = mysql_fault_verifier(
                    {"aport": aport, "bport": bport, "old_token": old_token, "fresh_token": fresh_token, "expected_db_name": expected_db_name}
                )
                target_criteria["mysql_fault_fail_closed"] = evaluate_mysql_fault_observation(mysql_fault_obs)

            if checkpoint_fault_verifier is not None:
                cp_fault_obs = checkpoint_fault_verifier(
                    {"aport": aport, "bport": bport, "old_token": old_token, "fresh_token": fresh_token, "expected_db_name": expected_db_name}
                )
                target_criteria["checkpoint_invalid_fail_closed"] = evaluate_checkpoint_fault_observation(cp_fault_obs)

            target_contract = evaluate_target_green_contract(
                target_criteria,
                prerequisite_passed=True,
                scenario_result=scenario_result,
            )
            result = GateResult(
                verdict=target_contract.verdict,
                exit_code=target_contract.exit_code,
                reason=target_contract.reason,
                a_status=scenario_result.a_status,
                b_status=scenario_result.b_status,
                fresh_b_status=scenario_result.fresh_b_status,
                vulnerability_reproduced=(scenario_result.verdict == "RED"),
            )
            emit(
                "FINAL_VERDICT",
                verdict=result.verdict,
                exit_code=result.exit_code,
                reason=result.reason,
                target_green_pass=target_contract.target_green_pass,
                verification_levels=target_contract.levels,
                criteria=target_contract.criteria,
                incomplete_criteria=target_contract.incomplete_criteria,
                failed_criteria=target_contract.failed_criteria,
                a_old_status=result.a_status,
                b_old_status=result.b_status,
                fresh_b_status=result.fresh_b_status,
                vulnerability_reproduced=result.vulnerability_reproduced,
            )
    except Exception as exc:
        result = GateResult("ERROR", EXIT_ERROR, f"{type(exc).__name__}: {exc}", None, None, None, False)
        if stage == STAGE_TARGET_GREEN:
            target_contract = TargetGreenContractResult(
                verdict="ERROR",
                exit_code=EXIT_ERROR,
                reason=result.reason,
                levels={
                    LEVEL_PREREQUISITE_CHECK: "PASS",
                    LEVEL_SCENARIO_PASS: "FAIL",
                    LEVEL_GREEN_PASS: "FAIL",
                },
                criteria=target_criteria,
                incomplete_criteria=[k for k in TARGET_GREEN_CRITERIA_ORDER if target_criteria.get(k) == "NOT_RUN"],
                failed_criteria=[k for k in TARGET_GREEN_CRITERIA_ORDER if target_criteria.get(k) == "FAIL"],
                target_green_pass=False,
            )
            emit(
                "FINAL_VERDICT",
                verdict=result.verdict,
                exit_code=result.exit_code,
                reason=result.reason,
                target_green_pass=False,
                verification_levels=target_contract.levels,
                incomplete_criteria=target_contract.incomplete_criteria,
            )
        else:
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

        default_levels = (
            {LEVEL_PREREQUISITE_CHECK: "NOT_RUN", LEVEL_SCENARIO_PASS: "NOT_RUN", LEVEL_GREEN_PASS: "NOT_RUN"}
            if stage == STAGE_BASELINE_RED
            else {LEVEL_PREREQUISITE_CHECK: "FAIL", LEVEL_SCENARIO_PASS: "NOT_RUN", LEVEL_GREEN_PASS: "FAIL"}
        )
        summary = {
            "stage": "BASELINE_RED" if stage == STAGE_BASELINE_RED else "TARGET_GREEN",
            "revocation_mode": revocation_mode,
            "shared_db_configured": stage == STAGE_TARGET_GREEN,
            "db_identity_verified": db_identity_verified,
            "db_authoritative_revocation_verified": db_revocation_verified,
            "redis_marker_required_for_stage": stage == STAGE_BASELINE_RED,
            "redis_marker_observed": redis_marker_observed,
            "jwt_secret_ephemeral": True,
            "isolation_guardrail_note": "Preflight naming/host/identity checks only; physical process/instance/container isolation is still required.",
            "verification_levels": target_contract.levels if target_contract else default_levels,
            "target_green_pass": target_contract.target_green_pass if target_contract else False,
            "target_green_criteria": target_contract.criteria if target_contract else target_criteria,
            "incomplete_criteria": target_contract.incomplete_criteria if target_contract else list(TARGET_GREEN_CRITERIA_ORDER),
            "failed_criteria": target_contract.failed_criteria if target_contract else [],
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
    parser.add_argument(
        "--allow-target-green",
        action="store_true",
        default=os.environ.get("ISSUE64_ALLOW_TARGET_GREEN", "").strip().lower() == "true",
        help="Explicit opt-in required to execute target-green stage",
    )
    parser.add_argument("--shared-jdbc-url", default=os.environ.get("ISSUE64_SHARED_JDBC_URL"), help="Isolated shared MySQL JDBC URL (issue64_test_<id> only)")
    parser.add_argument("--shared-db-user", default=os.environ.get("ISSUE64_SHARED_DB_USER"), help="Dedicated isolated test DB username")
    parser.add_argument("--shared-db-password-env", default="ISSUE64_SHARED_DB_PASSWORD", help="Env var holding test DB password (never logged)")
    parser.add_argument(
        "--approved-test-db-hosts",
        default=os.environ.get("ISSUE64_APPROVED_TEST_DB_HOSTS", ""),
        help="Comma-separated approved isolated test DB hosts beyond loopback",
    )
    parser.add_argument("--repo-root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[2])
    parser.add_argument("--redis-bin", type=pathlib.Path, default=pathlib.Path(os.environ.get("ISSUE64_REDIS_BIN", str(DEFAULT_REDIS_BIN))))
    parser.add_argument("--classpath-file", type=pathlib.Path, default=None)
    parser.add_argument("--runtime-dir", type=pathlib.Path, default=pathlib.Path(tempfile.gettempdir()) / "issue64-durability-red")
    parser.add_argument("--evidence-out", type=pathlib.Path, default=None)
    args = parser.parse_args(argv)

    cp_file = args.classpath_file or (args.repo_root / "target" / "issue64-classpath.txt")
    shared_db_password = os.environ.get(args.shared_db_password_env) if args.stage == STAGE_TARGET_GREEN else None
    approved_hosts = {h.strip().lower() for h in args.approved_test_db_hosts.split(",") if h.strip()}
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
            allow_target_green=args.allow_target_green,
            approved_remote_hosts=approved_hosts,
        )
    except GateError as exc:
        err_levels = (
            {LEVEL_PREREQUISITE_CHECK: "NOT_RUN", LEVEL_SCENARIO_PASS: "NOT_RUN", LEVEL_GREEN_PASS: "NOT_RUN"}
            if args.stage == STAGE_BASELINE_RED
            else {LEVEL_PREREQUISITE_CHECK: "FAIL", LEVEL_SCENARIO_PASS: "NOT_RUN", LEVEL_GREEN_PASS: "NOT_RUN"}
        )
        print(
            json.dumps(
                {
                    "event": "FINAL_VERDICT",
                    "stage": "BASELINE_RED" if args.stage == STAGE_BASELINE_RED else "TARGET_GREEN",
                    "verdict": "ERROR",
                    "exit_code": EXIT_ERROR,
                    "reason": str(exc),
                    "verification_levels": err_levels,
                },
                ensure_ascii=False,
            ),
            flush=True,
        )
        return EXIT_ERROR


if __name__ == "__main__":
    sys.exit(main())
