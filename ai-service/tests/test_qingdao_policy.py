from datetime import date
import json
from pathlib import Path

import httpx
import pytest

from yixun_ai.app import DEFAULT_KNOWLEDGE
from yixun_ai.knowledge import KnowledgeIndex
from yixun_ai.models import PolicyQuery
from yixun_ai.rag import RagPipeline
from yixun_ai.remote_retrieval import RemotePolicyRetrieval

AS_OF = date(2026, 11, 15)
DATASET = Path(__file__).resolve().parents[2] / "evaluation/datasets/qingdao_policy_pilot.v1.jsonl"
CASES = [json.loads(line) for line in DATASET.read_text().splitlines()]


@pytest.fixture
def index():
    return KnowledgeIndex(DEFAULT_KNOWLEDGE)


@pytest.mark.parametrize("case", CASES, ids=[c["id"] for c in CASES])
def test_qingdao_pilot_checks_right_section_or_scope_reason(index, case):
    response = RagPipeline(index).query(
        PolicyQuery(question=case["question"], history=case.get("history", [])), "test", AS_OF)
    assert [c.chunkId for c in response.citations] == case["acceptable_chunks"]
    assert response.reasonCode == case.get("expected_reason")
    if case["id"] == "QD04":
        assert not response.scopeSource
    if case["id"] in ("QD05", "QD06"):
        assert response.scopeSource.sourceUrl.startswith("https://www.qingdao.gov.cn/")


@pytest.mark.parametrize("case", CASES[3:], ids=[c["id"] for c in CASES[3:]])
def test_scope_checked_before_external_api_request(index, case):
    def denied(_):
        pytest.fail("Out-of-scope case should be handled without an external API request")

    remote = RemotePolicyRetrieval(index, "https://example.invalid/v1", "test", "embed", "rank",
                                   transport=httpx.MockTransport(denied))
    response = RagPipeline(index, semantic=remote).query(
        PolicyQuery(question=case["question"], history=case.get("history", [])), "test", AS_OF)
    assert response.reasonCode == case["expected_reason"]


@pytest.mark.parametrize("rerank_status", [200, 429])
def test_remote_contract_alignment_and_rerank_fallback(index, rerank_status):
    requests = []

    def respond(request):
        payload = json.loads(request.content)
        requests.append((request.url.path, payload))
        if request.url.path.endswith("/embeddings"):
            rows = [{"index": i, "embedding": [float(i + 1), 1.0]} for i, _ in enumerate(payload["input"])]
            return httpx.Response(200, json={"data": rows[::-1]})
        scores = [{"index": i, "relevance_score": 1.0 / (i + 1)} for i, _ in enumerate(payload["documents"])]
        return httpx.Response(rerank_status, json={"results": scores[::-1]})

    remote = RemotePolicyRetrieval(index, "https://example.invalid/v1", "test", "embed", "rank",
                                   transport=httpx.MockTransport(respond))
    pipeline = RagPipeline(index, semantic=remote)
    for _ in range(2):
        diagnostics = {}
        response = pipeline.query(PolicyQuery(question=CASES[0]["question"]), "test", AS_OF, diagnostics=diagnostics)
        assert len(diagnostics["hybrid"]) == 3
        assert ("reranked" in diagnostics) == (rerank_status == 200)
        assert ("RERANK_UNAVAILABLE" in response.warnings) == (rerank_status == 429)
    batches = [p["input"] for route, p in requests if route.endswith("/embeddings") and len(p["input"]) > 1]
    assert len(batches) == 1
    assert all("青岛市人民政府" in text and "1+6" not in text for text in batches[0])


def test_remote_embedding_unavailable_keeps_lexical_result(index):
    remote = RemotePolicyRetrieval(index, "https://example.invalid/v1", "test", "embed", "rank",
                                   transport=httpx.MockTransport(lambda _: httpx.Response(401)))
    response = RagPipeline(index, semantic=remote).query(PolicyQuery(question=CASES[0]["question"]), "test", AS_OF)
    assert response.citations[0].chunkId == CASES[0]["acceptable_chunks"][0]
    assert "EMBEDDINGS_UNAVAILABLE" in response.warnings
    assert "embedding" not in response.retrievalEngine
