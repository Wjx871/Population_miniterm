"""仅供 Java 内部调用的 RAG 服务；不识别材料、不执行业务写入。"""

from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from datetime import datetime
from zoneinfo import ZoneInfo
from pathlib import Path
from time import perf_counter
from uuid import uuid4
import hmac
import json
import logging
import os

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import APIKeyHeader

from .knowledge import KnowledgeIndex
from .models import DocumentAnalysis, PolicyQuery, PolicyResult, ServiceError
from .generation import GeneratorConfig, GroundedGenerator
from .rag import RagBusy, RagPipeline
from .remote_retrieval import RemotePolicyRetrieval

LOGGER = logging.getLogger("uvicorn.error")
DEFAULT_KNOWLEDGE = Path(__file__).resolve().parents[2] / "src/main/resources/policy-knowledge"


@dataclass(frozen=True)
class Settings:
    api_key: str = field(repr=False)
    knowledge_dir: Path = DEFAULT_KNOWLEDGE
    retrieval: str = "lexical"
    rerank: bool = True
    model_dir: Path = Path(__file__).resolve().parents[1] / "cache/models"
    generation_url: str = ""
    generation_model: str = "yixun-local"
    generation_key: str = field(default="", repr=False)
    generation_timeout: float = 45.0
    generation_provider: str = "auto"
    query_expansion: bool = False
    retrieval_url: str = ""
    retrieval_key: str = field(default="", repr=False)
    embedding_model: str = ""
    rerank_model: str = ""

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(os.environ.get("YIXUN_AI_API_KEY", ""),
                   Path(os.environ.get("YIXUN_AI_KNOWLEDGE_DIR", str(DEFAULT_KNOWLEDGE))),
                   retrieval=os.environ.get("YIXUN_AI_RETRIEVAL", "lexical"),
                   rerank=os.environ.get("YIXUN_AI_RERANK", "true").lower() == "true",
                   generation_url=os.environ.get("YIXUN_AI_GENERATION_URL", ""),
                   generation_model=os.environ.get("YIXUN_AI_GENERATION_MODEL", "yixun-local"),
                   generation_key=os.environ.get("YIXUN_AI_GENERATION_KEY", ""),
                   generation_timeout=float(os.environ.get("YIXUN_AI_GENERATION_TIMEOUT", "45")),
                   generation_provider=os.environ.get("YIXUN_AI_GENERATION_PROVIDER", "auto"),
                   query_expansion=os.environ.get("YIXUN_AI_QUERY_EXPANSION", "false").lower() == "true",
                   retrieval_url=os.environ.get("YIXUN_AI_RETRIEVAL_URL", ""),
                   retrieval_key=os.environ.get("YIXUN_AI_RETRIEVAL_KEY", ""),
                   embedding_model=os.environ.get("YIXUN_AI_EMBEDDING_MODEL", ""),
                   rerank_model=os.environ.get("YIXUN_AI_RERANK_MODEL", ""))


def create_app(settings: Settings | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        config = settings or Settings.from_env()
        if len(config.api_key) < 32 or config.api_key.startswith("replace_"):
            raise RuntimeError("Set a random YIXUN_AI_API_KEY of at least 32 characters")
        app.state.config = config
        app.state.index = KnowledgeIndex(config.knowledge_dir)
        if config.retrieval not in ("lexical", "hybrid", "remote"):
            raise ValueError("YIXUN_AI_RETRIEVAL must be lexical, hybrid or remote")
        semantic = None
        if config.retrieval == "hybrid":
            from .semantic import SemanticModels
            semantic = SemanticModels(config.model_dir, config.model_dir.parent / "index", app.state.index)
        if config.retrieval == "remote":
            if config.rerank and not config.rerank_model:
                raise ValueError("Remote reranking needs YIXUN_AI_RERANK_MODEL")
            semantic = RemotePolicyRetrieval(app.state.index, config.retrieval_url, config.retrieval_key,
                                             config.embedding_model, config.rerank_model if config.rerank else "")
        generator = GroundedGenerator(GeneratorConfig(config.generation_url, config.generation_model,
                                                      config.generation_key, config.generation_timeout,
                                                      config.generation_provider)) if config.generation_url else None
        app.state.pipeline = RagPipeline(app.state.index, semantic, generator, rerank=config.rerank,
                                         query_expansion=config.query_expansion,
                                         selection_candidates=16 if config.query_expansion else 0)
        yield

    app = FastAPI(title="邑循内部 RAG 服务", version="0.3.0", lifespan=lifespan)
    api_key_header = APIKeyHeader(name="X-AI-Service-Key", auto_error=False)

    @app.middleware("http")
    async def trace_request(request: Request, call_next):
        request.state.trace_id = str(uuid4())
        started = perf_counter()
        response = await call_next(request)
        response.headers["X-Trace-ID"] = request.state.trace_id
        # 不记录请求正文、材料字段、服务密钥或客户端提供的任意日志内容。
        LOGGER.info(json.dumps({"event": "ai_request", "traceId": request.state.trace_id,
                                "method": request.method, "path": request.scope.get("route").path if request.scope.get("route") else "UNMATCHED",
                                "status": response.status_code, "elapsedMs": round((perf_counter() - started) * 1000, 3)}))
        return response

    def error(request: Request, status: int, code: str, message: str) -> JSONResponse:
        return JSONResponse(status_code=status, content={"schemaVersion": "1.0", "traceId": request.state.trace_id,
                                                         "error": {"code": code, "message": message}})

    @app.exception_handler(RequestValidationError)
    async def invalid_input(request: Request, exc: RequestValidationError):
        # 不回显 Pydantic 错误中的 input，避免把完整材料/密钥写回客户端。
        return error(request, 422, "INVALID_REQUEST", "请求字段、长度或类型不符合接口契约。")

    async def internal_auth(request: Request, key: str | None = Depends(api_key_header)):
        if key is None or not hmac.compare_digest(key.encode(), request.app.state.config.api_key.encode()):
            return False
        return True

    @app.get("/health")
    def health(request: Request):
        index = request.app.state.index
        pipeline = request.app.state.pipeline
        generation_ready = pipeline.generator.available() if pipeline.generator else False
        return {"status": "DEGRADED" if pipeline.generator and not generation_ready else "UP", "serviceVersion": "0.3.0", "knowledgeVersion": index.version,
                "documents": index.document_count, "chunks": len(index.chunks),
                "modelVersions": pipeline.versions,
                "generationConfigured": pipeline.generator is not None,
                "generationProvider": pipeline.generator.provider if pipeline.generator else None,
                "capabilities": {"lexicalRetrieval": True, "semanticRetrieval": pipeline.semantic is not None,
                                 "reranking": pipeline.semantic is not None and pipeline.rerank,
                                 "queryExpansion": generation_ready and pipeline.query_expansion,
                                 "generation": generation_ready, "documentAnalysis": False}}

    @app.post("/internal/policy-query", response_model=PolicyResult,
              responses={401: {"model": ServiceError}, 422: {"model": ServiceError}, 503: {"model": ServiceError}})
    def query(payload: PolicyQuery, request: Request, authorized: bool = Depends(internal_auth)):
        if not authorized:
            return error(request, 401, "UNAUTHORIZED", "内部服务密钥无效。")
        try:
            return request.app.state.pipeline.query(payload, request.state.trace_id, datetime.now(ZoneInfo("Asia/Shanghai")).date())
        except RagBusy:
            return error(request, 503, "RAG_BUSY", "当前模型正在处理其他请求，请稍后重试。")

    @app.post("/internal/document-analysis", status_code=503, response_model=ServiceError,
              responses={401: {"model": ServiceError}, 422: {"model": ServiceError}})
    def document_analysis(payload: DocumentAnalysis, request: Request, authorized: bool = Depends(internal_auth)):
        if not authorized:
            return error(request, 401, "UNAUTHORIZED", "内部服务密钥无效。")
        return error(request, 503, "MODEL_NOT_CONFIGURED", "文档识别模型尚未接入，未读取或处理材料。")

    return app
