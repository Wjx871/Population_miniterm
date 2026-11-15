"""Read-only HTTP load probe. Saves timings/statuses, never response bodies or tokens."""
import argparse
import concurrent.futures
import json
import hashlib
import math
import os
from pathlib import Path
import time
import urllib.error
import urllib.request
from collections import Counter


def data_digest(value):
    """Ignore only volatile generation timestamps, retain every business value."""
    def stable(item):
        if isinstance(item, dict):
            return {key: stable(val) for key, val in item.items() if key != "generatedAt"}
        if isinstance(item, list):
            return [stable(val) for val in item]
        return item
    return hashlib.sha256(json.dumps(stable(value), sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--warmup", type=int, default=10)
    parser.add_argument("--timeout", type=float, default=10)
    parser.add_argument("--duration", type=float, default=0, help="Closed-loop duration in seconds; overrides --requests")
    parser.add_argument("--expected-data-sha256", help="Validate each response's business data, excluding generatedAt")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    if not 1 <= args.concurrency <= 128 or args.requests < 1 or args.warmup < 0 or args.timeout <= 0 or args.duration < 0:
        parser.error("invalid requests/concurrency/warmup/timeout")
    headers = {"Accept": "application/json"}
    token = os.getenv("OPS_BEARER_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token

    def request(_):
        started = time.perf_counter()
        status, valid, instance = 0, False, None
        try:
            with urllib.request.urlopen(urllib.request.Request(args.url, headers=headers), timeout=args.timeout) as response:
                status = response.status
                instance = response.headers.get("X-Instance-Id")
                payload = json.load(response)
                valid = status == 200 and payload.get("code") == 200
                if valid and args.expected_data_sha256:
                    valid = data_digest(payload.get("data")) == args.expected_data_sha256
        except urllib.error.HTTPError as error:
            status = error.code
        except (urllib.error.URLError, TimeoutError, ValueError, OSError):
            pass
        return {"ms": round((time.perf_counter() - started) * 1000, 3), "status": status,
                "success": valid, "instance": instance}

    for index in range(args.warmup):
        if not request(index)["success"]:
            raise SystemExit("Warmup failed; no performance conclusion was produced.")
    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        if args.duration:
            deadline = started + args.duration
            def worker(_):
                own = []
                while time.perf_counter() < deadline:
                    own.append(request(0))
                return own
            samples = [sample for group in executor.map(worker, range(args.concurrency)) for sample in group]
        else:
            samples = list(executor.map(request, range(args.requests)))
    elapsed = time.perf_counter() - started
    ordered = sorted(sample["ms"] for sample in samples)
    successful = sum(sample["success"] for sample in samples)
    result = {"url": args.url, "requests": len(samples), "duration_target_seconds": args.duration, "concurrency": args.concurrency,
              "business_data_validated": bool(args.expected_data_sha256), "load_model": "closed_loop",
              "warmup_requests": args.warmup, "elapsed_seconds": round(elapsed, 4),
              "percentile_method": "nearest_rank_all_requests", "p50_ms": ordered[math.ceil(len(ordered) * .50) - 1],
              "p95_ms": ordered[math.ceil(len(ordered) * .95) - 1], "throughput_rps": round(len(samples) / elapsed, 3),
              "successful_throughput_rps": round(successful / elapsed, 3),
              "error_rate": (len(samples) - successful) / len(samples),
              "statuses": dict(Counter(str(sample["status"]) for sample in samples)),
              "instances": dict(Counter(sample["instance"] for sample in samples)), "samples": samples}
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "samples"}, ensure_ascii=False))
    if successful != len(samples):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
