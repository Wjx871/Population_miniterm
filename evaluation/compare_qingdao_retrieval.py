"""用冻结的公开问句对照知识库、词法、向量融合和重排。通过现有轻量 Python 环境运行。"""

import argparse
from datetime import date, datetime, timezone
from hashlib import sha256
import json
import os
from pathlib import Path
from statistics import mean
import sys
from tempfile import TemporaryDirectory

from evaluate_service import assess

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ai-service"))

from yixun_ai.knowledge import KnowledgeIndex
from yixun_ai.models import PolicyQuery
from yixun_ai.rag import RagPipeline
from yixun_ai.remote_retrieval import RemotePolicyRetrieval

DATASET = ROOT / "evaluation/datasets/qingdao_policy_pilot.v1.jsonl"
KNOWLEDGE = ROOT / "src/main/resources/policy-knowledge"
BASELINE_VERSION = "0a576251adbbcf697d32ed70728f28df60ba2d1a87952efcb4bb646d44982388"
ADDED = {"qingdao-household-reform-2023.md", "shandong-household-1-plus-6-status.md"}
AS_OF = date(2026, 11, 15)


def run(pipeline, cases):
    rows = []
    for case in cases:
        diagnostics = {}
        payload = PolicyQuery(question=case["question"], history=case.get("history", []))
        result = pipeline.query(payload, case["id"], AS_OF, diagnostics=diagnostics).model_dump()
        rows.append({"id": case["id"], "question": payload.question, "history": case.get("history", []),
                     "candidate_ranks": diagnostics, "checks": assess(case, result), "response": result})
    return rows


def retrieval_metrics(rows, cases, method):
    positives = []
    for row, case in zip(rows, cases, strict=True):
        if case["acceptable_chunks"]:
            ranking = row["candidate_ranks"].get(method)
            if ranking is None and method != "lexical":
                continue
            positives.append(next((i for i, key in enumerate((ranking or [])[:4], 1)
                                   if key in case["acceptable_chunks"]), None))
    expected = sum(bool(case["acceptable_chunks"]) for case in cases)
    if len(positives) != expected:
        return {"status": "INCOMPLETE", "completed": len(positives), "positive_cases": expected}
    return {"status": "MEASURED", "positive_cases": expected, "section_hit_at_1": positives.count(1),
            "section_hit_at_4": sum(rank is not None for rank in positives),
            "mrr_at_4": mean(1 / rank if rank else 0 for rank in positives)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--remote", action="store_true", help="使用已明确授权和配置的远程向量与重排 API")
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Output already exists")
    raw = DATASET.read_bytes()
    dataset_sha = sha256(raw).hexdigest()
    if dataset_sha != DATASET.with_suffix(".sha256").read_text().strip():
        raise ValueError("Fixed dataset checksum changed")
    cases = [json.loads(line) for line in raw.decode().splitlines()]
    with TemporaryDirectory(prefix="yixun-guides-") as temp:
        path = Path(temp)
        for doc in KNOWLEDGE.glob("*.md"):
            if doc.name not in ADDED:
                (path / doc.name).write_bytes(doc.read_bytes())
        old_index = KnowledgeIndex(path)
    if old_index.version != BASELINE_VERSION:
        raise ValueError("Before corpus differs from the frozen baseline")
    new_index = KnowledgeIndex(KNOWLEDGE)
    before = run(RagPipeline(old_index), cases)
    lexical = run(RagPipeline(new_index), cases)
    report = {
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "as_of": AS_OF.isoformat(), "dataset_sha256": dataset_sha,
        "conditions": {"generation": False, "query_expansion": False, "top_k_metrics": [1, 4]},
        "before": {"knowledge_version": old_index.version, "metrics": retrieval_metrics(before, cases, "lexical"),
                   "rows": before},
        "lexical": {"knowledge_version": new_index.version, "metrics": retrieval_metrics(lexical, cases, "lexical"),
                    "rows": lexical},
        "remote": {"status": "NOT_RUN_NO_PROVIDER"},
    }
    if args.remote:
        for name in ("YIXUN_AI_RETRIEVAL_URL", "YIXUN_AI_RETRIEVAL_KEY",
                     "YIXUN_AI_EMBEDDING_MODEL", "YIXUN_AI_RERANK_MODEL"):
            if not os.environ.get(name):
                parser.error(f"Missing {name}; no remote question was sent")
        retrieval = RemotePolicyRetrieval(new_index, os.environ["YIXUN_AI_RETRIEVAL_URL"],
                                         os.environ["YIXUN_AI_RETRIEVAL_KEY"], os.environ["YIXUN_AI_EMBEDDING_MODEL"],
                                         os.environ["YIXUN_AI_RERANK_MODEL"])
        rows = run(RagPipeline(new_index, semantic=retrieval), cases)
        hybrid = retrieval_metrics(rows, cases, "hybrid")
        reranked = retrieval_metrics(rows, cases, "reranked")
        report["remote"] = {"status": "MEASURED" if hybrid["status"] == reranked["status"] == "MEASURED" else "INCOMPLETE",
                            "knowledge_version": new_index.version, "versions": retrieval.versions,
                            "hybrid": hybrid, "reranked": reranked, "rows": rows}
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    for key in ("before", "lexical", "remote"):
        print(key, report[key].get("metrics", report[key].get("status")))


if __name__ == "__main__":
    main()
