"""Positive, negative, and error self-tests for issue64_red_gate.py."""
from __future__ import annotations

import http.server
import json
import pathlib
import sys
import threading
import unittest
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

    def test_negative_control_when_old_token_returns_200_must_report_red(self) -> None:
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
        # 1. Redis stays DOWN until timeout
        routes_down = {
            ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "DOWN"}})],
        }
        with _MockInstance({}) as a, _MockInstance(routes_down) as b:
            res = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=0.6,
            )
        self.assertEqual(res.verdict, "ERROR")
        self.assertEqual(res.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res.vulnerability_reproduced)

        # 2. Prechecks failed -> ERROR even if 200 observed
        res_pre = gate.evaluate_post_empty_recovery(False, True, True, 200, 200, 200)
        self.assertEqual(res_pre.verdict, "ERROR")
        self.assertEqual(res_pre.exit_code, gate.EXIT_ERROR)

        # 3. Empty dir switch unverified -> ERROR even if 200 observed
        res_dir = gate.evaluate_post_empty_recovery(True, False, True, 200, 200, 200)
        self.assertEqual(res_dir.verdict, "ERROR")
        self.assertEqual(res_dir.exit_code, gate.EXIT_ERROR)

        # 4. Connection failure on instance A -> ERROR
        dead_port = gate.free_loopback_port()
        routes_up = {("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})]}
        with _MockInstance(routes_up) as b:
            res_conn = gate.verify_post_empty_recovery_over_http(
                dead_port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=1.0,
            )
        self.assertEqual(res_conn.verdict, "ERROR")
        self.assertEqual(res_conn.exit_code, gate.EXIT_ERROR)

        # 5. Fresh token failed with 500 while old returned 401 -> ERROR
        res_inc = gate.evaluate_post_empty_recovery(True, True, True, 401, 401, 500)
        self.assertEqual(res_inc.verdict, "ERROR")
        self.assertEqual(res_inc.exit_code, gate.EXIT_ERROR)
        self.assertFalse(res_inc.vulnerability_reproduced)

    def test_blocker_d_six_classifier_cases(self) -> None:
        # Case 1: oldA=200, oldB=200, fresh=500 -> ERROR (never RED when fresh fails)
        c1 = gate.evaluate_post_empty_recovery(True, True, True, 200, 200, 500)
        self.assertEqual((c1.verdict, c1.exit_code, c1.vulnerability_reproduced), ("ERROR", gate.EXIT_ERROR, False))

        # Case 2: oldA=200, oldB=401, fresh=200 -> RED
        c2 = gate.evaluate_post_empty_recovery(True, True, True, 200, 401, 200)
        self.assertEqual((c2.verdict, c2.exit_code, c2.vulnerability_reproduced), ("RED", gate.EXIT_RED, True))

        # Case 3: oldA=401, oldB=401, fresh=200 -> PASS
        c3 = gate.evaluate_post_empty_recovery(True, True, True, 401, 401, 200)
        self.assertEqual((c3.verdict, c3.exit_code, c3.vulnerability_reproduced), ("PASS", gate.EXIT_PASS, False))

        # Case 4: oldA=503, oldB=401, fresh=200 -> ERROR
        c4 = gate.evaluate_post_empty_recovery(True, True, True, 503, 401, 200)
        self.assertEqual((c4.verdict, c4.exit_code, c4.vulnerability_reproduced), ("ERROR", gate.EXIT_ERROR, False))

        # Case 5: oldA=200, oldB=500, fresh=200 -> ERROR (never RED if peer returned 500)
        c5 = gate.evaluate_post_empty_recovery(True, True, True, 200, 500, 200)
        self.assertEqual((c5.verdict, c5.exit_code, c5.vulnerability_reproduced), ("ERROR", gate.EXIT_ERROR, False))

        # Case 6: empty_dir_verified=False -> ERROR regardless of HTTP 200 statuses
        c6 = gate.evaluate_post_empty_recovery(True, False, True, 200, 200, 200)
        self.assertEqual((c6.verdict, c6.exit_code, c6.vulnerability_reproduced), ("ERROR", gate.EXIT_ERROR, False))

        # Additional HTTP check: verify_post_empty_recovery_over_http with oldA=200, oldB=200, fresh=500 -> ERROR
        routes_a = {("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200})]}
        routes_b = {
            ("/api/health", ""): [(200, {"code": 200, "data": {"redisStatus": "UP"}})],
            ("/api/auth/me", f"Bearer {self.OLD_TOKEN}"): [(200, {"code": 200})],
            ("/api/auth/me", f"Bearer {self.FRESH_TOKEN}"): [(500, {"code": 500})],
        }
        with _MockInstance(routes_a) as a, _MockInstance(routes_b) as b:
            http_c1 = gate.verify_post_empty_recovery_over_http(
                a.port, b.port, self.OLD_TOKEN, self.FRESH_TOKEN,
                prechecks_ok=True, empty_dir_verified=True, wait_timeout_s=2.0,
            )
        self.assertEqual((http_c1.verdict, http_c1.exit_code, http_c1.vulnerability_reproduced), ("ERROR", gate.EXIT_ERROR, False))

    def test_stage_separation_guards_target_green_cannot_fake_pass(self) -> None:
        repo_root = pathlib.Path(__file__).resolve().parents[2]
        self.assertEqual(gate.validate_stage_requirements("baseline-red", repo_root, None, None), "SHARED_REDIS_REQUIRED")
        # In current baseline/design state DURABLE_DB_REQUIRED is absent in src/main, so target-green MUST error.
        with self.assertRaises(gate.GateError):
            gate.validate_stage_requirements("target-green", repo_root, "jdbc:mysql://127.0.0.1:3306/test", "root")
        exit_code = gate.main(["--stage", "target-green"])
        self.assertEqual(exit_code, gate.EXIT_ERROR)

        # Even when DURABLE_DB_REQUIRED exists, empty/missing shared DB or per-JVM jdbc:h2:mem: MUST error.
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            fake_root = pathlib.Path(tmp)
            fake_props = fake_root / "src" / "main" / "java" / "com" / "wjx871" / "population" / "security" / "RevocationProperties.java"
            fake_props.parent.mkdir(parents=True, exist_ok=True)
            fake_props.write_text("public enum Mode { SINGLE_INSTANCE_LOCAL, SHARED_REDIS_REQUIRED, DURABLE_DB_REQUIRED }", encoding="utf-8")
            with self.assertRaises(gate.GateError):
                gate.validate_stage_requirements("target-green", fake_root, None, "root")
            with self.assertRaises(gate.GateError):
                gate.validate_stage_requirements("target-green", fake_root, "jdbc:h2:mem:isolated;MODE=MySQL", "sa")
            self.assertEqual(
                gate.validate_stage_requirements("target-green", fake_root, "jdbc:mysql://127.0.0.1:3306/pop_test", "pop_test"),
                "DURABLE_DB_REQUIRED",
            )

    def test_jti_digest_does_not_leak_raw_jti(self) -> None:
        raw_jti = "sensitive-jti-uuid-1234"
        digest = gate.short_jti_digest(raw_jti)
        self.assertEqual(len(digest), 12)
        self.assertNotIn("sensitive", digest)


if __name__ == "__main__":
    unittest.main()
