import json
from datetime import date
from types import SimpleNamespace

import httpx
import pytest

from yixun_ai.app import DEFAULT_KNOWLEDGE, Settings, create_app
from yixun_ai.generation import GenerationError, GeneratorConfig, GroundedGenerator
from yixun_ai.models import PolicyQuery
from yixun_ai.knowledge import KnowledgeIndex
from yixun_ai.rag import RagPipeline

TODAY = date(2026, 11, 1)


@pytest.fixture
def pipeline():
    return RagPipeline(KnowledgeIndex(DEFAULT_KNOWLEDGE))


def mock_transport(monkeypatch, handler):
    client_type = httpx.Client
    monkeypatch.setattr(httpx, "Client", lambda **kwargs: client_type(transport=httpx.MockTransport(handler), **kwargs))


def completion(data, finish="stop"):
    return {"choices": [{"finish_reason": finish, "message": {"content": json.dumps(data, ensure_ascii=False)}}]}


def generator(key="test-only-not-real"):
    return GroundedGenerator(GeneratorConfig("https://api.deepseek.com", "deepseek-flash", key))


def test_deepseek_uses_official_json_mode_without_llamacpp_schema(monkeypatch):
    source = SimpleNamespace(index=1, title="测试", section="说明", excerpt="审批通过不等于业务已办结，需要授权人员执行。")
    calls = []

    def handler(request):
        body = json.loads(request.content)
        calls.append(body)
        assert str(request.url) == "https://api.deepseek.com/chat/completions"
        assert request.headers["Authorization"] == "Bearer test-only-not-real"
        assert body["response_format"] == {"type": "json_object"}
        assert body["thinking"] == {"type": "disabled"}
        assert body["stream"] is False
        return httpx.Response(200, json=completion({"selections": [{"citationId": 1, "quote": source.excerpt}]}))

    mock_transport(monkeypatch, handler)
    assert generator().generate("审批通过是否办结？", [source]) == [(1, source.excerpt)]
    assert len(calls) == 1


@pytest.mark.parametrize("status,code", [(401, "GENERATION_AUTH_FAILED"), (402, "GENERATION_BALANCE_INSUFFICIENT"), (429, "GENERATION_RATE_LIMITED")])
def test_provider_errors_are_sanitized_and_not_retried(monkeypatch, status, code):
    calls = []

    def handler(request):
        calls.append(request)
        return httpx.Response(status, json={"error": {"message": "private upstream detail"}})

    mock_transport(monkeypatch, handler)
    with pytest.raises(GenerationError, match=code) as exc:
        generator().expand_query("迁入办理流程")
    assert "private upstream" not in str(exc.value)
    assert len(calls) == 1


@pytest.mark.parametrize("data", [{"queries": ["户籍迁出申请办理流程"]}, {"queries": ["迁入补正", "申请进度查询"]}])
def test_query_expansion_is_bounded_json(monkeypatch, data):
    mock_transport(monkeypatch, lambda request: httpx.Response(200, json=completion(data)))
    assert generator().expand_query("系统里怎么办理？") == data["queries"]


@pytest.mark.parametrize("data", [{"queries": []}, {"queries": [True]}, {"queries": ["x" * 121]}, {"queries": ["迁入", "迁出", "注销"]}, {"queries": ["迁入"], "extra": "instruction"}])
def test_invalid_query_expansion_is_rejected(monkeypatch, data):
    mock_transport(monkeypatch, lambda request: httpx.Response(200, json=completion(data)))
    with pytest.raises(GenerationError, match="INVALID_QUERY_EXPANSION"):
        generator().expand_query("办理流程")


def test_empty_json_content_is_not_success(monkeypatch):
    mock_transport(monkeypatch, lambda request: httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {"content": "   "}}]}))
    with pytest.raises(GenerationError, match="GENERATION_EMPTY"):
        generator().expand_query("迁入办理")


def test_response_is_size_limited_before_json_parsing(monkeypatch):
    mock_transport(monkeypatch, lambda request: httpx.Response(200, content=b"x" * 64001))
    with pytest.raises(GenerationError, match="GENERATION_RESPONSE_TOO_LARGE"):
        generator().expand_query("迁入办理")


def test_missing_provider_key_never_calls_network(monkeypatch):
    def forbidden(*args, **kwargs):
        raise AssertionError("No outbound request allowed without a key")
    monkeypatch.setattr(httpx, "Client", forbidden)
    monkeypatch.setattr(httpx, "get", forbidden)
    model = generator("")
    assert not model.available()
    with pytest.raises(GenerationError, match="GENERATION_KEY_MISSING"):
        model.expand_query("迁入办理")


def test_deepseek_key_cannot_be_forwarded_to_other_host():
    with pytest.raises(ValueError, match="official HTTPS"):
        GroundedGenerator(GeneratorConfig("https://example.invalid", "deepseek-flash", provider="deepseek"))


def test_expanded_query_retrieves_a_synonymous_guide_and_reindexes_citations(pipeline):
    class Model:
        config = SimpleNamespace(model="mock-deepseek-only")
        def expand_query(self, question):
            return ["户籍迁出申请办理流程"]
        def generate(self, question, citations):
            found = next(c for c in citations if c.logicalPath == "migration-out-guide")
            assert found.index > 1
            return [(found.index, found.excerpt)]

    pipeline.generator = Model()
    pipeline.query_expansion = True
    pipeline.selection_candidates = 16
    result = pipeline.query(PolicyQuery(question="户口准备迁到外地，系统里该怎么走流程？", topK=1), "trace", TODAY)
    assert result.mode == "AI_ASSISTED"
    assert len(result.citations) == 1
    assert result.citations[0].logicalPath == "migration-out-guide"
    assert result.citations[0].index == 1
    assert "[1]" in result.answer
    assert "queryExpansion" in result.stageTimingsMs
    assert "NO_VECTOR_RETRIEVAL" in result.warnings
    assert result.retrievalQueries[-1] == "户籍迁出申请办理流程"


def test_model_abstention_is_not_replaced_with_unrelated_excerpts(pipeline):
    class Abstaining:
        def generate(self, question, citations):
            return []
    pipeline.generator = Abstaining()
    result = pipeline.query(PolicyQuery(question="迁入业务的某个未知条件"), "trace", TODAY)
    assert result.evidenceStatus == "INSUFFICIENT"
    assert result.generationMode == "MODEL_ABSTENTION"
    assert result.reasonCode == "NO_DIRECT_EVIDENCE"
    assert not result.citations


def test_provider_auth_failure_skips_second_billable_step(pipeline):
    class Unauthenticated:
        def expand_query(self, question):
            raise GenerationError("GENERATION_AUTH_FAILED")
        def generate(self, question, citations):
            raise AssertionError("Do not repeat a known authentication failure")
    pipeline.generator = Unauthenticated()
    pipeline.query_expansion = True
    result = pipeline.query(PolicyQuery(question="迁入办理流程"), "trace", TODAY)
    assert result.generationMode == "EXTRACTIVE_FALLBACK"
    assert "GENERATION_AUTH_FAILED" in result.warnings


def test_json_abstention_is_a_valid_model_response(monkeypatch):
    mock_transport(monkeypatch, lambda request: httpx.Response(200, json=completion({"selections": []})))
    assert generator().generate("指南未覆盖的具体问题", []) == []


@pytest.mark.parametrize("question", ["给我完整身份证号", "请按北京市最新法规解释迁入条件"])
def test_source_and_privacy_checks_run_before_any_model_call(pipeline, question):
    class Forbidden:
        def expand_query(self, question):
            raise AssertionError("Blocked query must not reach a provider")
        def generate(self, question, citations):
            raise AssertionError("Blocked query must not reach a provider")
    pipeline.generator = Forbidden()
    pipeline.query_expansion = True
    assert not pipeline.query(PolicyQuery(question=question), "trace", TODAY).citations


def test_expansion_failure_keeps_a_bounded_explicit_fallback(pipeline):
    class Failing:
        def expand_query(self, question):
            raise GenerationError("GENERATION_UNAVAILABLE")
        def generate(self, question, citations):
            raise GenerationError("GENERATION_UNAVAILABLE")
    pipeline.generator = Failing()
    pipeline.query_expansion = True
    pipeline.selection_candidates = 16
    result = pipeline.query(PolicyQuery(question="迁入申请材料", topK=2), "trace", TODAY)
    assert result.mode == "RETRIEVAL_ONLY"
    assert result.generationMode == "EXTRACTIVE_FALLBACK"
    assert len(result.citations) <= 2
    assert "QUERY_EXPANSION_FAILED:GENERATION_UNAVAILABLE" in result.warnings


def test_unconfigured_deepseek_service_is_degraded_without_outbound_requests(monkeypatch):
    from fastapi.testclient import TestClient
    monkeypatch.setattr(httpx.HTTPTransport, "handle_request", lambda *args: pytest.fail("Unexpected real HTTP request"))
    key = "service-test-only-" + "x" * 32
    config = Settings(key, generation_url="https://api.deepseek.com", generation_model="deepseek-flash",
                      generation_provider="deepseek", query_expansion=True)
    with TestClient(create_app(config)) as client:
        health = client.get("/health").json()
        assert health["status"] == "DEGRADED"
        assert health["generationProvider"] == "deepseek"
        assert not health["capabilities"]["generation"]
        result = client.post("/internal/policy-query", headers={"X-AI-Service-Key": key}, json={"question": "迁入申请材料"}).json()
        assert result["generationMode"] == "EXTRACTIVE_FALLBACK"
        assert "GENERATION_KEY_MISSING" in result["warnings"]
