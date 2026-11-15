"""内部 v1 契约，与现有 Java AgentResponse 分开版本化。"""

from typing import Annotated, Literal
from datetime import date
from pydantic import BaseModel, ConfigDict, Field, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class PolicyQuery(StrictModel):
    question: str = Field(min_length=2, max_length=500)
    topK: int = Field(default=4, ge=1, le=8, strict=True)
    history: list["ConversationTurn"] = Field(default_factory=list, max_length=6)
    regionCode: str | None = Field(default=None, pattern=r"^\d{6}$")
    asOf: date | None = None

    @field_validator("question")
    @classmethod
    def nonblank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("question must not be blank")
        return value


class ConversationTurn(StrictModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=500)


class ErrorDetail(StrictModel):
    code: str
    message: str


class ServiceError(StrictModel):
    schemaVersion: Literal["1.0"] = "1.0"
    traceId: str
    error: ErrorDetail


class Citation(StrictModel):
    index: int
    logicalPath: str
    section: str
    title: str
    category: str
    sourceType: str
    sourceVersion: str
    effectiveDate: str | None
    excerpt: str
    retrievalScore: float
    chunkId: str | None = None
    semanticScore: float | None = None
    rerankScore: float | None = None
    sourceUrl: str | None = None
    regionCodes: list[str] = Field(default_factory=list)


class ScopeSource(StrictModel):
    title: str
    sourceVersion: str
    sourceUrl: str
    effectiveDate: str
    expiresOn: str | None = None


class PolicyResult(StrictModel):
    schemaVersion: Literal["1.0"] = "1.0"
    traceId: str
    mode: Literal["RETRIEVAL_ONLY", "SAFE_REFUSAL", "AI_ASSISTED"]
    evidenceStatus: Literal["CANDIDATES_ONLY", "INSUFFICIENT", "REFUSED"]
    answer: str
    citations: list[Citation]
    knowledgeVersion: str
    retrievalEngine: str
    timingMs: float
    warnings: list[str]
    standaloneQuestion: str | None = None
    reasonCode: str | None = None
    generationMode: str = "DISABLED"
    modelVersions: dict[str, str] = Field(default_factory=dict)
    stageTimingsMs: dict[str, float] = Field(default_factory=dict)
    retrievalQueries: list[str] = Field(default_factory=list)
    scopeSource: ScopeSource | None = None


class DocumentAnalysis(StrictModel):
    taskId: str = Field(min_length=1, max_length=64)
    businessType: Literal["MIGRATION_IN"]
    materialIds: list[Annotated[int, Field(strict=True, gt=0)]] = Field(min_length=1, max_length=10)

    @field_validator("materialIds")
    @classmethod
    def positive_ids(cls, value: list[int]) -> list[int]:
        if any(item <= 0 for item in value) or len(value) != len(set(value)):
            raise ValueError("materialIds must be positive and unique")
        return value
