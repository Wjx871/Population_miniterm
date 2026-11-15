import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("diagnose", Path(__file__).with_name("diagnose-logs.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DiagnosisTests(unittest.TestCase):
    def test_compose_prefix_keeps_the_actual_time_range(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "compose.log"
            path.write_text("backend-1 | 2026-11-15 16:00:01.123 INFO http_request method=GET status=200 elapsedMs=1 exception=false", encoding="utf-8")
            result = module.diagnose([path])
        self.assertEqual(result["time_range"], ["2026-11-15 16:00:01.123", "2026-11-15 16:00:01.123"])
    def test_rules_reference_observed_lines_and_do_not_echo_sensitive_messages(self):
        lines = ["2026-11-08 12:00:00.000 WARN [traceId=probe] Redis unavailable; falling back PRIVATE_PAYLOAD",
                 "2026-11-08 12:00:01.000 WARN replica_connection_failed action=primary_fallback"]
        lines.extend(f"2026-11-08 12:00:02.000 INFO traceId=r{i} http_request method=GET status={500 if i == 0 else 200} elapsedMs=1200 exception=false" for i in range(10))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.log"
            path.write_text("\n".join(lines), encoding="utf-8")
            result = module.diagnose([path])
        self.assertEqual({finding["rule"] for finding in result["findings"]},
                         {"redis_fallback", "replica_fallback", "http_server_errors", "http_latency"})
        self.assertEqual(result["request_count"], 10)
        self.assertEqual(result["findings"][0]["evidence"][0]["line"], 1)
        self.assertNotIn("PRIVATE_PAYLOAD", str(result))

    def test_no_latency_conclusion_for_small_sample(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.log"
            path.write_text("http_request method=GET status=200 elapsedMs=9000 exception=false", encoding="utf-8")
            result = module.diagnose([path])
        self.assertEqual(result["findings"], [])


if __name__ == "__main__":
    unittest.main()
