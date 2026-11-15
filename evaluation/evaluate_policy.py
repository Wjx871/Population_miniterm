"""评测现有 Java 公开问答入口。只调用登录、问答、健康与退出，不写业务数据。

自动分数只度量“命中预标注文档/拒绝形态”，不能替代人工答案正确性评判。
"""

import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
import math
import os
from pathlib import Path
import statistics
import subprocess
import time
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]


def call(base: str, path: str, body=None, token=None, timeout: int = 20):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    started = time.perf_counter()
    try:
        with urlopen(request, timeout=timeout) as response:
            return response.status, json.load(response), (time.perf_counter() - started) * 1000
    except HTTPError as exc:
        return exc.code, None, (time.perf_counter() - started) * 1000


def assess(case: dict, status: int, result: dict) -> dict:
    paths = [c.get("logicalPath") for c in result.get("citations", [])]
    expected = case["expected_behavior"]
    hit = bool(set(paths[:4]) & set(case["acceptable_sources"])) if expected == "cite_guide" else None
    withheld = (not paths and result.get("evidenceStatus") == "INSUFFICIENT")
    refusal = result.get("mode") == "SAFE_REFUSAL" and not paths
    behavior = hit if expected == "cite_guide" else refusal if expected == "refuse" else withheld
    return {"transport_ok": status == 200, "document_hit_at_4": hit if status == 200 else False if hit is not None else None,
            "expected_behavior_shape": status == 200 and bool(behavior), "human_answer_grade": None}


def fingerprint(paths: list[Path]) -> str:
    digest = sha256()
    for path in sorted(paths):
        digest.update(path.relative_to(ROOT).as_posix().encode() + b"\0" + path.read_bytes() + b"\0")
    return digest.hexdigest()


def evaluate(base: str, username: str, password: str, dataset: Path, query_timeout: int = 75) -> dict:
    cases = [json.loads(line) for line in dataset.read_text().splitlines() if line.strip()]
    ids = [case["id"] for case in cases]
    if len(ids) != len(set(ids)) or not cases:
        raise ValueError("Empty dataset or duplicate case ID")
    _, health, _ = call(base, "/api/health")
    status, login, _ = call(base, "/api/auth/login", {"username": username, "password": password})
    if status != 200 or not login or not login.get("data", {}).get("token"):
        raise RuntimeError(f"Login failed: HTTP {status}; no credentials or response saved")
    token = login["data"]["token"]
    results = []
    try:
        for case in cases:
            try:
                status, response, elapsed = call(base, "/api/assistant/policy/query",
                                                  {"question": case["question"], "history": case["history"]}, token, timeout=query_timeout)
                data = (response or {}).get("data") or {}
                results.append({"id": case["id"], "category": case["category"],
                                "status": status, "elapsed_ms": round(elapsed, 3),
                                "checks": assess(case, status, data), "response": data})
            except (OSError, ValueError) as exc:
                results.append({"id": case["id"], "category": case["category"], "status": None,
                                "elapsed_ms": None, "error": type(exc).__name__,
                                "checks": assess(case, 0, {}), "response": {}})
    finally:
        try:
            call(base, "/api/auth/logout", {}, token)
        except OSError:
            pass
    relevant = [r for r in results if r["checks"]["document_hit_at_4"] is not None]
    durations = sorted(r["elapsed_ms"] for r in results if r["elapsed_ms"] is not None)
    guides = list((ROOT / "src/main/resources/policy-knowledge").glob("*.md"))
    sources = list((ROOT / "src/main/java/com/wjx871/population/assistant").glob("*.java"))
    try:
        revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        revision = "unknown"
    jar = ROOT / "target/population-miniterm-1.0.0.jar"
    return {"schema_version": "1.0", "created_at_utc": datetime.now(timezone.utc).isoformat(),
            "target": "JAVA_PUBLIC_AGENT_ENDPOINT", "base_url": base, "username": username,
            "dataset": dataset.name, "dataset_sha256": sha256(dataset.read_bytes()).hexdigest(),
            "git_revision": revision, "assistant_source_sha256": fingerprint(sources),
            "knowledge_snapshot_sha256": fingerprint(guides), "knowledge_documents": len(guides),
            "jar_sha256": sha256(jar.read_bytes()).hexdigest() if jar.is_file() else None,
            "health": health, "conditions": {"concurrency": 1, "warmup": "none", "repetitions": 1,
                                               "split": "development", "query_timeout_seconds": query_timeout,
                                               "timing": "HTTP wall time including transport"},
            "limitations": ["评测的是 Java Agent 后处理后的最多 4 条引用，不是原始检索 Recall。",
                            "所有题目为开发诊断集，不代表独立测试集或真实政策准确率。",
                            "答案正确率、引用是否充分支持结论仍需按 reference_answer 人工复核。",
                            "源文件、知识库和本地 JAR 分别记录快照；不据此断言远程进程使用了对应 JAR。"],
            "summary": {"cases": len(results), "http_ok": sum(r["checks"]["transport_ok"] for r in results),
                        "document_hit_at_4": {"hits": sum(r["checks"]["document_hit_at_4"] for r in relevant), "eligible": len(relevant)},
                        "behavior_shape_passes": sum(r["checks"]["expected_behavior_shape"] for r in results),
                        "latency_ms": {"p50": statistics.median(durations) if durations else None,
                                       "p95": durations[math.ceil(.95 * len(durations)) - 1] if durations else None}},
            "results": results}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="population")
    parser.add_argument("--password-env", default="EVAL_PASSWORD")
    parser.add_argument("--dataset", type=Path, default=ROOT / "evaluation/datasets/policy_questions.v1.jsonl")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--query-timeout", type=int, default=75)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    parsed = urlsplit(base)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.path or parsed.query or parsed.fragment:
        parser.error("base-url must be an HTTP(S) origin without credentials, path or query")
    password = os.environ.get(args.password_env)
    if not password:
        parser.error(f"Set password environment variable {args.password_env}")
    if args.output.exists():
        parser.error("Output exists; choose a new path to preserve the baseline")
    if not 1 <= args.query_timeout <= 180:
        parser.error("query-timeout must be between 1 and 180 seconds")
    report = evaluate(base, args.username, password, args.dataset, args.query_timeout)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as file:
        json.dump(report, file, ensure_ascii=False, indent=2)
        file.write("\n")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
