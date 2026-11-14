"""Positive, negative, and error self-tests for issue64_red_gate.py."""
from __future__ import annotations

import http.server
import io
import json
import pathlib
import sys
import tempfile
import threading
import unittest
from contextlib import redirect_stdout
from typing import Any

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import issue64_red_gate as gate  # noqa: E402


class _MockInstance:
    def __init__(self, routes: dict[tuple[str, str], list[tuple[int, dict[str, Any]]]]):
        self.routes = routes
        self.lock = threading.Lock()
        parent = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, format: str, *args: Any) -> None:
                return

            def do_GET(self) -> None:
                auth = self.headers.get("Authorization", "")
                key = (self.path, auth)
                with parent.lock:
                    queue = parent.routes.get(key)
                    if not queue:
                        status, payload = 500, {"code": 500, "message": "unconfigured"}
                    elif len(queue) > 1:
                        status, payload = queue.pop(0)
                    else:
                        status, payload = queue[0]
                body = json.dumps(payload).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.port = self.server.server_address[1]
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self) -> "_MockInstance":
        self.thread.start()
        return self

    def __exit__(self, *args: Any) -> None:
        self.server.shutdown()
        self.server.server_close()


class Issue64RedGateSelfTest(unittest.TestCase):
    OLD_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJvbGQtanRpIn0.sig"
    FRESH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJmcmVzaC1qdGkifQ.sig"

    def test_positive_control_when_vulnerability_fixed_must_report_pass_not_red(self) -> None:
        # old A=401, old B=401, fresh=200 -> PASS (exit 0)
        res_direct = gate.evaluate_post_empty_recovery(True, True, True, 401, 401, 200)
        self.assertEqual(res_direct.verdict, "PASS")
        self.assertEqual(res_direct.exit_code, gate.EXIT_PASS)
        self.assertFalse(res_direct.vulnerability_reproduced)

        routes_a = {
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(401, {"code": 401, "message": "身份令牌已撤销"})],
        }
        routes_b = {
            ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})],
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(401, {"code": 401, "message": "身份令牌已撤销"})],
            ("/api/auth/me", f"Bearer {self.FRESH_TOKEN}"): [(200, {"code": 200, "data": {"username": "population"}})],
        }
        with _MockInstance(routes_a) as a, _MockInstance(routes_b) as b:
            res = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=2.0,
            )
        self.assertEqual(res.verdict, "PASS")
        self.assertEqual(res.exit_code, gate.EXIT_PASS)
        self.assertFalse(res.vulnerability_reproduced)
        self.assertEqual(res.a_status, 401)
        self.assertEqual(res.b_status, 401)
        self.assertEqual(res.fresh_b_status, 200)

    def test_negative_controls_when_old_token_returns_200_with_fresh_200_must_report_red(self) -> None:
        # old A=200, old B=401, fresh=200 -> RED (exit 2)
        res_partial = gate.evaluate_post_empty_recovery(True, True, True, 200, 401, 200)
        self.assertEqual(res_partial.verdict, "RED")
        self.assertEqual(res_partial.exit_code, gate.EXIT_RED)
        self.assertTrue(res_partial.vulnerability_reproduced)

        # HTTP end-to-end: old A=200, old B=401, fresh=200 -> RED (exit 2)
        routes_a_partial = {
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200, "data": {"username": "population"}})],
        }
        routes_b_partial = {
            ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})],
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(401, {"code": 401, "message": "身份令牌已撤销"})],
            ("/api/auth/me", f"Bearer {self.FRESH_TOKEN}"): [(200, {"code": 200, "data": {"username": "population"}})],
        }
        with _MockInstance(routes_a_partial) as a, _MockInstance(routes_b_partial) as b:
            res_http_partial = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=2.0,
            )
        self.assertEqual(res_http_partial.verdict, "RED")
        self.assertEqual(res_http_partial.exit_code, gate.EXIT_RED)
        self.assertTrue(res_http_partial.vulnerability_reproduced)

        # HTTP end-to-end: old A=200, old B=200, fresh=200 -> RED (exit 2)
        routes_a = {
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200, "data": {"username": "population"}})],
        }
        routes_b = {
            ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})],
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200, "data": {"username": "population"}})],
            ("/api/auth/me", f"Bearer {self.FRESH_TOKEN}"): [(200, {"code": 200, "data": {"username": "population"}})],
        }
        with _MockInstance(routes_a) as a, _MockInstance(routes_b) as b:
            res = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=2.0,
            )
        self.assertEqual(res.verdict, "RED")
        self.assertEqual(res.exit_code, gate.EXIT_RED)
        self.assertTrue(res.vulnerability_reproduced)

    def test_transient_503_before_200_still_classifies_red(self) -> None:
        routes_a = {
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [
                (503, {"code": 503}),
                (200, {"code": 200}),
            ],
        }
        routes_b = {
            ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})],
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200})],
            ("/api/auth/me", f"Bearer {self.FRESH_TOKEN}"): [(200, {"code": 200})],
        }
        with _MockInstance(routes_a) as a, _MockInstance(routes_b) as b:
            res = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=2.0,
            )
        self.assertEqual(res.verdict, "RED")
        self.assertEqual(res.exit_code, gate.EXIT_RED)

    def test_error_controls_never_report_red_or_pass(self) -> None:
        # 1. old A=200, old B=200, fresh=500 -> ERROR (direct + HTTP)
        res_fresh_500 = gate.evaluate_post_empty_recovery(True, True, True, 200, 200, 500)
        self.assertEqual(res_fresh_500.verdict, "ERROR")
        self.assertEqual(res_fresh_500.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res_fresh_500.vulnerability_reproduced)

        with (
            _MockInstance({("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200})]}) as a,
            _MockInstance(
                {
                    ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})],
                    ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200})],
                    ("/api/auth/me", f"Bearer {self.FRESH_TOKEN}"): [(500, {"code": 500})],
                }
            ) as b,
        ):
            res_fresh_500_http = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=2.0,
            )
        self.assertEqual(res_fresh_500_http.verdict, "ERROR")
        self.assertEqual(res_fresh_500_http.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res_fresh_500_http.vulnerability_reproduced)

        # 2. old A=503, old B=401, fresh=200 -> ERROR (direct + persistent HTTP 503 timeout)
        res_old_503 = gate.evaluate_post_empty_recovery(True, True, True, 503, 401, 200)
        self.assertEqual(res_old_503.verdict, "ERROR")
        self.assertEqual(res_old_503.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res_old_503.vulnerability_reproduced)

        with (
            _MockInstance({("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(503, {"code": 503})]}) as a,
            _MockInstance(
                {
                    ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})],
                    ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(401, {"code": 401})],
                    ("/api/auth/me", f"Bearer {self.FRESH_TOKEN}"): [(200, {"code": 200})],
                }
            ) as b,
        ):
            res_old_503_http = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=0.6,
            )
        self.assertEqual(res_old_503_http.verdict, "ERROR")
        self.assertEqual(res_old_503_http.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res_old_503_http.vulnerability_reproduced)

        # 3. old A=200, old B=500, fresh=200 -> ERROR (direct + HTTP)
        res_old_500 = gate.evaluate_post_empty_recovery(True, True, True, 200, 500, 200)
        self.assertEqual(res_old_500.verdict, "ERROR")
        self.assertEqual(res_old_500.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res_old_500.vulnerability_reproduced)

        with (
            _MockInstance({("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200})]}) as a,
            _MockInstance(
                {
                    ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})],
                    ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(500, {"code": 500})],
                    ("/api/auth/me", f"Bearer {self.FRESH_TOKEN}"): [(200, {"code": 200})],
                }
            ) as b,
        ):
            res_old_500_http = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=2.0,
            )
        self.assertEqual(res_old_500_http.verdict, "ERROR")
        self.assertEqual(res_old_500_http.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res_old_500_http.vulnerability_reproduced)

        # 4. Empty dir switch unverified -> ERROR regardless of HTTP 200/401
        for statuses in ((200, 200, 200), (401, 401, 200)):
            res_dir = gate.evaluate_post_empty_recovery(True, False, True, *statuses)
            self.assertEqual(res_dir.verdict, "ERROR")
            self.assertEqual(res_dir.exit_code, gate.EXIT_ERROR)
            self.assertFalse(res_dir.vulnerability_reproduced)

        # 5. Redis stays DOWN until timeout -> ERROR
        routes_down = {
            ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "DOWN"}})],
        }
        with _MockInstance({}) as a, _MockInstance(routes_down) as b:
            res_down = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=0.6,
            )
        self.assertEqual(res_down.verdict, "ERROR")
        self.assertEqual(res_down.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res_down.vulnerability_reproduced)

        # 6. Prechecks failed -> ERROR even if 200 observed
        res_pre = gate.evaluate_post_empty_recovery(False, True, True, 200, 200, 200)
        self.assertEqual(res_pre.verdict, "ERROR")
        self.assertEqual(res_pre.exit_code, gate.EXIT_ERROR)

        # 7. Connection failure on instance A -> ERROR
        dead_port = gate.free_loopback_port()
        routes_up = {("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})]}
        with _MockInstance(routes_up) as b:
            res_conn = gate.verify_post_empty_recovery_over_http(
                dead_port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=1.0,
            )
        self.assertEqual(res_conn.verdict, "ERROR")
        self.assertEqual(res_conn.exit_code, gate.EXIT_ERROR)

        # 8. Fresh token failed with 500 while old returned 401 -> ERROR
        res_inc = gate.evaluate_post_empty_recovery(True, True, True, 401, 401, 500)
        self.assertEqual(res_inc.verdict, "ERROR")
        self.assertEqual(res_inc.exit_code, gate.EXIT_ERROR)

    def test_target_green_prerequisites_reject_unimplemented_or_unsafe_dbs(self) -> None:
        # 1. Real repository currently has no DURABLE_DB_REQUIRED -> CLI returns ERROR (exit 1)
        repo_root = pathlib.Path(__file__).resolve().parents[2]
        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = gate.main(["--repo-root", str(repo_root), "--gate-stage", "TARGET_GREEN"])
        self.assertEqual(rc, gate.EXIT_ERROR)
        verdict_payload = json.loads(buf.getvalue().strip().splitlines()[-1])
        self.assertEqual(verdict_payload["verdict"], "ERROR")
        self.assertEqual(verdict_payload["exit_code"], gate.EXIT_ERROR)
        self.assertIn("target_mode_not_implemented", verdict_payload["reason"])

        # 2. Simulated repo containing DURABLE_DB_REQUIRED still rejects missing/h2-mem/non-test URLs
        with tempfile.TemporaryDirectory() as tmp:
            fake_repo = pathlib.Path(tmp)
            props = (
                fake_repo
                / "src"
                / "main"
                / "java"
                / "com"
                / "wjx871"
                / "population"
                / "security"
                / "RevocationProperties.java"
            )
            props.parent.mkdir(parents=True, exist_ok=True)
            props.write_text("public enum Mode { SINGLE_INSTANCE_LOCAL, DURABLE_DB_REQUIRED }", encoding="utf-8")

            with self.assertRaisesRegex(gate.GateError, "target_db_url_missing"):
                gate.validate_target_green_prerequisites(fake_repo, None)
            with self.assertRaisesRegex(gate.GateError, "isolated_h2_mem_forbidden"):
                gate.validate_target_green_prerequisites(fake_repo, "jdbc:h2:mem:testdb;MODE=MySQL")
            with self.assertRaisesRegex(gate.GateError, "unsafe_target_db_url_rejected"):
                gate.validate_target_green_prerequisites(fake_repo, "jdbc:mysql://127.0.0.1:3306/population_miniterm")
            with self.assertRaisesRegex(gate.GateError, "unsafe_target_db_url_rejected"):
                gate.validate_target_green_prerequisites(fake_repo, "jdbc:mysql://prod-db.internal:3306/population_test")

            accepted = gate.validate_target_green_prerequisites(
                fake_repo, "jdbc:mysql://127.0.0.1:3306/population_issue64_test?useSSL=false"
            )
            self.assertIn("population_issue64_test", accepted)

    def test_jti_digest_and_db_url_sanitization_do_not_leak_secrets(self) -> None:
        raw_jti = "sensitive-jti-uuid-1234"
        digest = gate.short_jti_digest(raw_jti)
        self.assertEqual(len(digest), 12)
        self.assertNotIn("sensitive", digest)

        sanitized = gate.sanitize_db_url("jdbc:mysql://user:topsecret@127.0.0.1:3306/issue64_test?password=abc")
        self.assertEqual(sanitized, "jdbc:mysql://127.0.0.1:3306/issue64_test")
        self.assertNotIn("topsecret", sanitized)
        self.assertNotIn("password", sanitized)


if __name__ == "__main__":
    unittest.main()
