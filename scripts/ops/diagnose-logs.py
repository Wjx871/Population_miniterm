"""Evidence-based rules for existing Java logs; does not call an AI service."""
import argparse
import json
import math
from pathlib import Path
import re

REQUEST = re.compile(r"http_request method=(\S+) status=(\d+) elapsedMs=(\d+) exception=(true|false)")
TRACE = re.compile(r"traceId=([A-Za-z0-9_-]+)")
TIME = re.compile(r"^(?:[^|]*\|\s*)?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)")


def diagnose(paths, p95_threshold_ms=1000, minimum_requests=10):
    requests, redis_errors, replica_errors, timestamps = [], [], [], []
    for path in paths:
        for number, line in enumerate(Path(path).read_text(encoding="utf-8-sig", errors="replace").splitlines(), 1):
            timestamp = TIME.search(line)
            if timestamp:
                timestamps.append(timestamp.group(1))
            trace = TRACE.search(line)
            evidence = {"file": str(path), "line": number, "traceId": trace.group(1) if trace else None}
            match = REQUEST.search(line)
            if match:
                requests.append({**evidence, "status": int(match.group(2)), "ms": int(match.group(3))})
            if "Redis unavailable; falling back" in line:
                redis_errors.append(evidence)
            if "replica_connection_failed action=primary_fallback" in line:
                replica_errors.append(evidence)
    findings = []

    def finding(rule, metric, evidence, cause, checks):
        findings.append({"rule": rule, "observed": metric, "evidence": evidence[:5],
                         "possible_cause": cause, "suggested_checks": checks})

    if redis_errors:
        finding("redis_fallback", {"warning_events": len(redis_errors), "rate_limited": True}, redis_errors,
                "Redis 连接、超时或数据处理异常触发回源，日志不能单独确定根因。",
                ["按 traceId 核对同一请求日志", "核对缓存 readErrors/writeErrors 增量", "检查 Redis 连通性、超时和资源"])
    if replica_errors:
        finding("replica_fallback", {"connection_failure_events": len(replica_errors)}, replica_errors,
                "从库连接获取失败；代码已进入受控回主路径。",
                ["核对路由 fallbacks 增量", "检查从库连接、连接池和复制线程", "观察回主后的主库连接压力"])
    failures = [request for request in requests if request["status"] >= 500]
    if failures:
        finding("http_server_errors", {"errors": len(failures), "requests": len(requests),
                                      "error_rate": len(failures) / len(requests)}, failures,
                "请求出现服务端失败；仅凭状态码不能判断数据库、应用或上游哪一项异常。",
                ["按 traceId 关联异常栈与依赖日志", "核对该窗口的缓存/路由异常", "恢复后复测原请求"])
    latencies = sorted(request["ms"] for request in requests)
    p95 = latencies[math.ceil(len(latencies) * .95) - 1] if latencies else None
    if len(requests) >= minimum_requests and p95 >= p95_threshold_ms:
        slow = sorted(requests, key=lambda request: request["ms"], reverse=True)
        finding("http_latency", {"p95_ms": p95, "threshold_ms": p95_threshold_ms,
                                 "requests": len(requests), "percentile_method": "nearest_rank"}, slow,
                "请求延迟超过配置阈值，需继续检查依赖等待或应用处理耗时。",
                ["关联慢请求 traceId", "检查慢 SQL 与连接池等待", "检查 Redis/从库超时和 OCR 上游耗时"])
    return {"sources": [str(path) for path in paths], "time_range": [min(timestamps), max(timestamps)] if timestamps else None,
            "request_count": len(requests), "server_error_count": len(failures), "p95_ms": p95,
            "window": "all parsed records in supplied files; rotate/select files for comparable windows",
            "findings": findings}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("logs", nargs="+")
    parser.add_argument("--output", required=True)
    parser.add_argument("--p95-threshold-ms", type=float, default=1000)
    args = parser.parse_args()
    result = diagnose(args.logs, args.p95_threshold_ms)
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"requests": result["request_count"], "findings": len(result["findings"])}))


if __name__ == "__main__":
    main()
