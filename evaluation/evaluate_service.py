"""通过内部 HTTP 接口评测远程 RAG；不加载模型、不读取提供商 Key，不覆盖结果。"""
import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
import math
import os
from pathlib import Path
import statistics
import time
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def assess(case, result):
    citations = result.get("citations", [])[:4]
    paths = [c.get("logicalPath") for c in citations]
    ranks = [i+1 for i, path in enumerate(paths) if path in case["acceptable_sources"]]
    behavior = case["expected_behavior"]
    passed = (bool(ranks) if behavior == "cite_guide" else
              result.get("mode") == "SAFE_REFUSAL" and not paths if behavior == "refuse" else
              result.get("evidenceStatus") == "INSUFFICIENT" and not paths)
    expected_chunks = case.get("acceptable_chunks", [])
    return {"document_hit_at_4": bool(ranks) if case["acceptable_sources"] else None,
            "reciprocal_rank_at_4": 1/min(ranks) if ranks else 0 if case["acceptable_sources"] else None,
            "section_hit_at_4": any(c.get("chunkId") in expected_chunks for c in citations) if expected_chunks else None,
            "reason_match": result.get("reasonCode") == case["expected_reason"] if "expected_reason" in case else None,
            "behavior_shape": passed, "human_answer_grade": None}


def evaluate(base, key, dataset, ids, expected_index=None):
    raw = dataset.read_bytes()
    expected = dataset.with_suffix(".sha256")
    if expected.exists() and sha256(raw).hexdigest() != expected.read_text().strip():
        raise ValueError("Fixed dataset checksum changed")
    cases = [json.loads(line) for line in raw.decode().splitlines() if line.strip()]
    if ids:
        selected = set(ids.split(","))
        if not selected <= {c["id"] for c in cases}:
            raise ValueError("Unknown case ID")
        cases = [c for c in cases if c["id"] in selected]
    with urlopen(base + "/health", timeout=5) as response:
        health = json.load(response)
    if expected_index and health.get("knowledgeVersion") != expected_index:
        raise RuntimeError("Unexpected knowledge version; no evaluation questions sent")
    if not health.get("capabilities", {}).get("generation"):
        raise RuntimeError("Generation not ready; no evaluation questions sent")
    results = []
    for case in cases:
        payload = {name: case[name] for name in ("question", "history", "regionCode", "asOf") if name in case}
        payload["topK"] = 4
        started = time.perf_counter()
        try:
            request = Request(base + "/internal/policy-query", data=json.dumps(payload).encode(),
                              headers={"Content-Type": "application/json", "X-AI-Service-Key": key})
            with urlopen(request, timeout=65) as response:
                result = json.load(response)
            checks = assess(case, result)
            row = {"id": case["id"], "question": case["question"], "checks": checks, "response": result}
        except (OSError, ValueError) as exc:
            row = {"id": case["id"], "question": case["question"], "error": type(exc).__name__,
                   "checks": assess(case, {}), "response": {}}
        row["elapsed_ms"] = round((time.perf_counter()-started)*1000, 3)
        results.append(row)
        print(case["id"], row["response"].get("mode", "ERROR"), row["response"].get("generationMode"), row["checks"]["behavior_shape"], flush=True)
    positives = [r for r in results if r["checks"]["document_hit_at_4"] is not None]
    durations = sorted(r["elapsed_ms"] for r in results)
    return {"created_at_utc": datetime.now(timezone.utc).isoformat(), "target": "PYTHON_INTERNAL_RAG_HTTP",
            "dataset": dataset.name, "dataset_sha256": sha256(raw).hexdigest(), "health": health,
            "conditions": {"concurrency": 1, "repetitions": 1, "selected_ids": ids or "all", "automatic_retries": 0},
            "limitations": ["人工诊断集，文档命中不等于答案正确率。", "未验证 Java 后处理或页面。", "云端模型别名可能动态变化；此处仅记录配置与测试时点。"],
            "summary": {"cases": len(results), "positive_cases": len(positives),
                        "document_hits_at_4": sum(r["checks"]["document_hit_at_4"] for r in positives),
                        "mrr_at_4": statistics.mean(r["checks"]["reciprocal_rank_at_4"] for r in positives) if positives else None,
                        "behavior_shape_passes": sum(r["checks"]["behavior_shape"] for r in results),
                        "verified_generation_cases": sum(r["response"].get("generationMode") == "VERIFIED_EXTRACTIVE" for r in results),
                        "generation_fallback_cases": sum(r["response"].get("generationMode") == "EXTRACTIVE_FALLBACK" for r in results),
                        "latency_ms_p50": statistics.median(durations),
                        "latency_ms_p95": durations[math.ceil(.95*len(durations))-1],
                        "failed_cases": [r["id"] for r in results if not r["checks"]["behavior_shape"]]},
            "results": results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8091")
    parser.add_argument("--service-key-file", type=Path, required=True)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--ids", default="H03,H05,H24")
    parser.add_argument("--expect-knowledge-version")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    url = urlsplit(base)
    if url.username or url.password or url.path or url.query or url.fragment or url.scheme not in ("http", "https") or (url.scheme == "http" and url.hostname not in ("127.0.0.1", "localhost", "::1")):
        parser.error("Use a loopback HTTP origin or a verified HTTPS origin")
    if args.output.exists():
        parser.error("Output already exists; preserve prior evidence")
    values = dict(line.split("=", 1) for line in args.service_key_file.read_text().splitlines() if "=" in line and not line.startswith("#"))
    key = values.get("YIXUN_AI_API_KEY") or values.get("POLICY_RAG_SERVICE_KEY")
    if not key:
        parser.error("Internal service key is missing")
    report = evaluate(base, key, args.dataset, args.ids, args.expect_knowledge_version)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(args.output, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(json.dumps(report["summary"], ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
