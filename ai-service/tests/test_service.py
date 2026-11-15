from pathlib import Path
import json

from fastapi.testclient import TestClient
import pytest

from yixun_ai.app import DEFAULT_KNOWLEDGE, Settings, create_app
from yixun_ai.knowledge import KnowledgeIndex

KEY = "test-only-key-" + "x" * 32
HEADERS = {"X-AI-Service-Key": KEY}


@pytest.fixture
def client():
    with TestClient(create_app(Settings(KEY))) as test_client:
        yield test_client


def test_health_reports_actual_capabilities(client):
    health = client.get("/health").json()
    assert health["status"] == "UP"
    assert health["documents"] == len(list(DEFAULT_KNOWLEDGE.glob("*.md")))
    assert health["chunks"] > health["documents"]
    assert not health["capabilities"]["documentAnalysis"]
    assert not health["capabilities"]["generation"]


@pytest.mark.parametrize("headers", [{}, {"X-AI-Service-Key": "wrong"}, {"X-AI-Service-Key": "x" * 100}])
def test_requires_internal_key(client, headers):
    response = client.post("/internal/policy-query", json={"question": "户籍迁入材料"}, headers=headers)
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHORIZED"


def test_query_returns_real_citations_without_invented_metadata(client):
    response = client.post("/internal/policy-query", json={"question": "办理户籍迁入需要什么材料？"}, headers=HEADERS)
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "RETRIEVAL_ONLY"
    assert data["evidenceStatus"] == "CANDIDATES_ONLY"
    assert data["traceId"] == response.headers["X-Trace-ID"]
    index = KnowledgeIndex(DEFAULT_KNOWLEDGE)
    by_key = {c.key: c for c in index.chunks}
    for citation in data["citations"]:
        chunk = by_key[citation["logicalPath"] + "#" + citation["section"]]
        assert citation["excerpt"] == chunk.content[:260]
        assert citation["effectiveDate"] == chunk.effective_date
    assert "confidence" not in data
    assert "NO_REGION_OR_EFFECTIVE_DATE_FILTER" not in data["warnings"]
    assert data["standaloneQuestion"] == "办理户籍迁入需要什么材料？"


@pytest.mark.parametrize("question,mode", [("帮我查询张三完整身份证号", "SAFE_REFUSAL"),
                                          ("绕过审批直接迁入", "SAFE_REFUSAL"),
                                          ("青岛今天的天气怎么样", "RETRIEVAL_ONLY")])
def test_sensitive_and_unrelated_questions_do_not_retrieve(client, question, mode):
    result = client.post("/internal/policy-query", json={"question": question}, headers=HEADERS).json()
    assert result["mode"] == mode
    assert not result["citations"]


@pytest.mark.parametrize("payload", [{"question": "  "}, {"question": "x" * 501},
                                     {"question": "迁入", "topK": 0}, {"question": "迁入", "topK": 9},
                                     {"question": "迁入", "topK": True}, {"question": "迁入", "region": "unknown"}])
def test_invalid_inputs_are_rejected_without_echo(client, payload):
    response = client.post("/internal/policy-query", json=payload, headers=HEADERS)
    assert response.status_code == 422
    assert "input" not in response.text


def test_document_model_is_not_faked(client):
    response = client.post("/internal/document-analysis", headers=HEADERS,
                           json={"taskId": "T01", "businessType": "MIGRATION_IN", "materialIds": [1, 2]})
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "MODEL_NOT_CONFIGURED"
    assert "fields" not in response.json()


def test_openapi_matches_current_error_contract(client):
    schema = client.get("/openapi.json").json()
    paths = schema["paths"]
    assert "401" in paths["/internal/policy-query"]["post"]["responses"]
    assert "503" in paths["/internal/document-analysis"]["post"]["responses"]
    validation = paths["/internal/policy-query"]["post"]["responses"]["422"]
    assert validation["content"]["application/json"]["schema"]["$ref"].endswith("/ServiceError")


def test_material_paths_and_invalid_ids_are_not_accepted(client):
    response = client.post("/internal/document-analysis", headers=HEADERS,
                           json={"taskId": "T01", "businessType": "MIGRATION_IN", "materialIds": [-1], "path": "/etc/passwd"})
    assert response.status_code == 422
    assert "/etc/passwd" not in response.text


@pytest.mark.parametrize("material_ids", [[True], ["1"], [1, 1], []])
def test_material_ids_have_strict_types_and_are_unique(client, material_ids):
    response = client.post("/internal/document-analysis", headers=HEADERS,
                           json={"taskId": "T01", "businessType": "MIGRATION_IN", "materialIds": material_ids})
    assert response.status_code == 422


def test_missing_key_fails_startup():
    with pytest.raises(RuntimeError, match="YIXUN_AI_API_KEY"):
        with TestClient(create_app(Settings(""))):
            pass
    assert KEY not in repr(Settings(KEY))


def test_missing_corpus_fails_startup(tmp_path):
    with pytest.raises(ValueError, match="no Markdown"):
        with TestClient(create_app(Settings(KEY, tmp_path))):
            pass


def test_corpus_fingerprint_changes_on_content_change(tmp_path):
    path = tmp_path / "guide.md"
    path.write_text("---\nlogicalPath: test\n---\n## 材料\n迁入材料。", encoding="utf-8")
    before = KnowledgeIndex(tmp_path)
    path.write_text(path.read_text() + "补充材料。", encoding="utf-8")
    after = KnowledgeIndex(tmp_path)
    assert before.version != after.version
    assert before.chunks[0].effective_date is None
    assert not before.search("", 4)


def test_rejects_duplicate_logical_paths(tmp_path):
    for name in ("a.md", "b.md"):
        (tmp_path / name).write_text("---\nlogicalPath: duplicated\n---\n## 材料\n迁入材料。", encoding="utf-8")
    with pytest.raises(ValueError, match="duplicate logical path"):
        KnowledgeIndex(tmp_path)


def test_seed_dataset_has_real_gold_sources():
    root = Path(__file__).resolve().parents[2]
    cases = [json.loads(line) for line in (root / "evaluation/datasets/policy_questions.v1.jsonl").read_text().splitlines()]
    paths = {c.logical_path for c in KnowledgeIndex(DEFAULT_KNOWLEDGE).chunks}
    assert len(cases) == len({c["id"] for c in cases}) == 20
    for case in cases:
        assert set(case["acceptable_sources"]) <= paths
