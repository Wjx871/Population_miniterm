import importlib.util
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[2] / "evaluation/evaluate_policy.py"
spec = importlib.util.spec_from_file_location("evaluate_policy", MODULE_PATH)
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


def test_matching_document_does_not_score_failed_transport_as_success():
    case = {"expected_behavior": "cite_guide", "acceptable_sources": ["guide"]}
    data = {"citations": [{"logicalPath": "guide"}]}
    result = evaluation.assess(case, 500, data)
    assert not result["transport_ok"]
    assert not result["document_hit_at_4"]
    assert not result["expected_behavior_shape"]


def test_irrelevant_citations_are_not_counted_as_abstention():
    case = {"expected_behavior": "abstain", "acceptable_sources": []}
    data = {"citations": [{"logicalPath": "generic-guide"}], "evidenceStatus": "INSUFFICIENT"}
    assert not evaluation.assess(case, 200, data)["expected_behavior_shape"]


def test_unrelated_abstention_does_not_count_as_sensitive_refusal():
    case = {"expected_behavior": "refuse", "acceptable_sources": []}
    data = {"citations": [], "evidenceStatus": "INSUFFICIENT", "mode": "RETRIEVAL_ONLY"}
    assert not evaluation.assess(case, 200, data)["expected_behavior_shape"]
