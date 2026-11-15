"""完整的检索、融合、重排、来源约束与证据生成编排。"""

from datetime import date
from time import perf_counter
from threading import BoundedSemaphore

from .generation import GenerationError
from .knowledge import normalize
from .models import Citation, PolicyQuery, PolicyResult, ScopeSource
from .policy import REGIONS, eligible, plan_query, region_contains
from .remote_retrieval import RemotePolicyRetrieval, RemoteRetrievalError

NOTICE = "以上依据项目/系统指南；正式办理要求以主管部门最新规定为准。"
OFFICIAL_NOTICE = "以上为所引政策条款摘要。实际申请由主管部门结合材料核定。"


class RagBusy(RuntimeError):
    pass


class RagPipeline:
    def __init__(self, index, semantic=None, generator=None, *, rerank=True, query_expansion=False, selection_candidates=0):
        self.index, self.semantic, self.generator, self.rerank = index, semantic, generator, rerank
        self.query_expansion = query_expansion
        self.selection_candidates = selection_candidates
        if not 0 <= selection_candidates <= 32:
            raise ValueError("selection_candidates must be between 0 and 32")
        self._slot = BoundedSemaphore(1)
        self.versions = {} if semantic is None else dict(semantic.versions)
        if generator:
            self.versions["generator"] = generator.config.model

    def query(self, payload: PolicyQuery, trace_id: str, today: date, *, diagnostics=None) -> PolicyResult:
        if not self._slot.acquire(blocking=False):
            raise RagBusy("RAG_BUSY")
        try:
            return self._query(payload, trace_id, today, diagnostics)
        finally:
            self._slot.release()

    def _query(self, payload: PolicyQuery, trace_id: str, today: date, diagnostics) -> PolicyResult:
        started = perf_counter()
        timings, warnings = {}, []
        plan = plan_query(payload, today)
        base = dict(traceId=trace_id, knowledgeVersion=self.index.version, retrievalEngine=self.index.engine,
                    standaloneQuestion=plan.question, modelVersions=self.versions)

        def empty(reason, message, refused=False, generation_mode="DISABLED", scope_source=None):
            return PolicyResult(**base, mode="SAFE_REFUSAL" if refused else "RETRIEVAL_ONLY",
                                evidenceStatus="REFUSED" if refused else "INSUFFICIENT", answer=message,
                                citations=[], reasonCode=reason, timingMs=round((perf_counter()-started)*1000, 3),
                                warnings=warnings, stageTimingsMs=timings, generationMode=generation_mode,
                                scopeSource=scope_source)

        if plan.block_reason:
            if plan.block_reason == "OUT_OF_SCOPE":
                return empty("OUT_OF_SCOPE", "该问题不在当前人口业务指南范围内，无法据此回答。")
            if plan.block_reason in ("INVALID_DATE", "CONFLICTING_DATE"):
                return empty(plan.block_reason, "查询日期无效或问题中的日期与 asOf 不一致，请确认需要查询的日期。")
            if plan.block_reason == "CONFLICTING_REGION":
                return empty(plan.block_reason, "请确认实际办理地的城市，再按当地政策查询。")
            if plan.block_reason == "NO_APPLICABLE_SOURCE":
                return empty(plan.block_reason, "所问日期尚未到来，当前知识库无法确认届时适用的政策。")
            return empty(plan.block_reason, "助手不能查询个人敏感档案、绕过审批或执行任意指令。可继续咨询办理流程。", True)

        named = {c.logical_path: c for c in self.index.chunks if c.aliases and c.effective_date
                 and c.source_url and c.source_url.startswith("https://")
                 and any(normalize(alias) in normalize(plan.question) for alias in c.aliases)
                 and plan.region and any(region_contains(code, plan.region) for code in c.region_codes)}
        if len(named) == 1:
            c = next(iter(named.values()))
            scope = ScopeSource(title=c.title, sourceVersion=c.version, sourceUrl=c.source_url,
                                effectiveDate=c.effective_date, expiresOn=c.expires_on)
            if date.fromisoformat(c.effective_date) > (plan.period_start or plan.as_of):
                return empty("SOURCE_NOT_YET_EFFECTIVE", f"{c.version}自 {c.effective_date} 施行，无法支持所问时段。"
                             "请核对办理当时适用的正式文件。", scope_source=scope)
            if c.expires_on and date.fromisoformat(c.expires_on) < plan.as_of:
                return empty("SOURCE_TERM_ENDED", f"{c.version}原文有效期截至 {c.expires_on}。"
                             "当前知识库没有核验通过的续期依据，无法确认在所问日期仍适用。", scope_source=scope)
        allowed = {c.key: c for c in self.index.chunks if eligible(c, plan)}
        if not allowed:
            if plan.official_required and not plan.region:
                return empty("NO_APPLICABLE_SOURCE", "需要确认适用地区。请提供办理地的城市或明确的官方文件。")
            place = next((name for name, code in REGIONS.items() if code == plan.region), "")
            if place and not any(c.source_type == "OFFICIAL_POLICY" and
                                 any(region_contains(code, plan.region) for code in c.region_codes)
                                 for c in self.index.chunks):
                return empty("NO_APPLICABLE_SOURCE", f"知识库暂缺适用于{place}的正式政策，不能确认当地是否采用同样条件。请核对{place}主管部门的文件。")
            return empty("NO_APPLICABLE_SOURCE", "知识库缺少办理地和所问时段适用的政策依据，无法确认。请核对当地主管部门的正式文件。")
        queries = [plan.question]
        provider_error = None
        if self.query_expansion and self.generator:
            t = perf_counter()
            try:
                queries.extend(q for q in self.generator.expand_query(plan.question) if q != plan.question)
            except GenerationError as exc:
                warnings.append("QUERY_EXPANSION_FAILED:" + str(exc))
                if str(exc) in ("GENERATION_KEY_MISSING", "GENERATION_AUTH_FAILED", "GENERATION_BALANCE_INSUFFICIENT", "GENERATION_RATE_LIMITED"):
                    provider_error = exc
            timings["queryExpansion"] = round((perf_counter()-t)*1000, 3)
        base["retrievalQueries"] = queries
        if len(queries) > 1:
            base["retrievalEngine"] += "+query-expansion"
        t = perf_counter()
        runs = [[(c.key, score) for c, score in self.index.search(q, 16, set(allowed))] for q in queries]
        lexical_scores, lexical_fused = {}, {}
        for run in runs:
            for rank, (key, score) in enumerate(run, 1):
                lexical_scores[key] = max(lexical_scores.get(key, 0), score)
                lexical_fused[key] = lexical_fused.get(key, 0) + 1/(60+rank)
        lexical = sorted(lexical_fused.items(), key=lambda row: (-row[1], row[0]))
        timings["retrieval"] = round((perf_counter()-t)*1000, 3)
        if diagnostics is not None:
            diagnostics["lexical"] = [key for key, _ in lexical]
        dense = []
        remote = isinstance(self.semantic, RemotePolicyRetrieval)
        if self.semantic and (not remote or self.semantic.supports(allowed)):
            t = perf_counter()
            try:
                dense = self.semantic.search(plan.question, set(allowed), 16)
            except RemoteRetrievalError as exc:
                warnings.append(str(exc))
            timings["embeddingSearch"] = round((perf_counter()-t)*1000, 3)
        if dense:
            base["retrievalEngine"] += "+remote-embedding+rrf" if remote else "+semantic+rrf"
        dense_scores = dict(dense)
        # 每路各自按名次融合，不相加不同量纲的原始分数。
        fused = {}
        for weight, rows in ((.55, lexical), (.45, dense)):
            for rank, (key, _) in enumerate(rows, 1):
                fused[key] = fused.get(key, 0) + weight/(60+rank)
        candidates = sorted(fused, key=lambda key: (-fused[key], key))[:20]
        if diagnostics is not None and dense:
            diagnostics["hybrid"] = list(candidates)
        if self.selection_candidates:
            # 保留每条检索问句的前几项，避免“原问无同义词”的新候选被融合名次挤掉。
            heads = list(dict.fromkeys(key for run in runs for key, _ in run[:4]))
            candidates = heads + [key for key in sorted(fused, key=lambda key: (-fused[key], key)) if key not in heads]
        rerank_scores = {}
        if dense and self.rerank:
            t = perf_counter()
            try:
                rerank_scores = self.semantic.rerank(plan.question, candidates)
            except RemoteRetrievalError as exc:
                warnings.append(str(exc))
            if rerank_scores:
                candidates.sort(key=lambda key: (-rerank_scores[key], -fused[key], key))
                base["retrievalEngine"] += "+remote-rerank" if remote else "+crossencoder"
                if diagnostics is not None:
                    diagnostics["reranked"] = list(candidates)
            timings["rerank"] = round((perf_counter()-t)*1000, 3)
        # 阈值仅作低相关过滤；真正适用性已由来源约束处理，不宣称概率正确率。
        if not candidates or (not dense and max(lexical_scores.values(), default=0) < 2.7):
            return empty("LOW_RELEVANCE", "当前指南没有找到可支持回答的内容，请补充具体办理事项或联系业务人员。")
        if dense and max(dense_scores.values(), default=0) < .42 and max(lexical_scores.values(), default=0) < 5:
            return empty("LOW_RELEVANCE", "当前指南没有找到足够相关的依据，无法确认。")
        # 不重复堆积同一文档：每篇最多两块，让多条件问题有机会保留补充证据。
        selected, counts = [], {}
        for key in candidates:
            path = allowed[key].logical_path
            if counts.get(path, 0) >= (max(payload.topK, self.selection_candidates) if plan.official_required else 2):
                continue
            selected.append(key)
            counts[path] = counts.get(path, 0) + 1
            if len(selected) >= max(payload.topK, self.selection_candidates):
                break
        citations = []
        for i, key in enumerate(selected, 1):
            c = allowed[key]
            citations.append(Citation(index=i, logicalPath=c.logical_path, chunkId=key, section=c.section,
                                      title=c.title, category=c.category, sourceType=c.source_type,
                                      sourceVersion=c.version, effectiveDate=c.effective_date, excerpt=c.content[:260],
                                      retrievalScore=round(fused[key], 6), semanticScore=dense_scores.get(key),
                                      rerankScore=rerank_scores.get(key), sourceUrl=c.source_url, regionCodes=list(c.region_codes)))
        mode, generation_mode = "RETRIEVAL_ONLY", "DISABLED"
        candidate_citations = citations
        citations = candidate_citations[:1 if plan.official_required else payload.topK]
        # 降级仍给出完整原文依据，不只说“请参考下方”。
        answer = "\n\n".join(f"{c.excerpt} [{c.index}]" for c in citations[:3])
        if self.generator:
            t = perf_counter()
            try:
                if provider_error:
                    raise provider_error
                picks = self.generator.generate(plan.question, candidate_citations)
                if not picks:
                    timings["generation"] = round((perf_counter()-t)*1000, 3)
                    return empty("NO_DIRECT_EVIDENCE", "候选指南没有直接回答这个问题，无法据此确认；请补充具体情况或联系业务人员。",
                                 generation_mode="MODEL_ABSTENTION")
                numbers = list(dict.fromkeys(number for number, _ in picks))[:payload.topK]
                remap = {number: i for i, number in enumerate(numbers, 1)}
                by_number = {c.index: c for c in candidate_citations}
                citations = [by_number[number].model_copy(update={"index": remap[number]}) for number in numbers]
                # 命中正式条款时保留整段已复核条件，避免只显示后一句的办理范围。
                answer = ("\n\n".join(f"{c.excerpt} [{c.index}]" for c in citations) if plan.official_required else
                          "\n\n".join(f"{quote} [{remap[number]}]" for number, quote in picks if number in remap))
                mode, generation_mode = "AI_ASSISTED", "VERIFIED_EXTRACTIVE"
                if self.selection_candidates:
                    base["retrievalEngine"] += "+llm-evidence-selection"
            except GenerationError as exc:
                warnings.append(str(exc))
                generation_mode = "EXTRACTIVE_FALLBACK"
            timings["generation"] = round((perf_counter()-t)*1000, 3)
        if not dense:
            warnings.append("NO_VECTOR_RETRIEVAL" if self.query_expansion else "LEXICAL_BASELINE_ONLY")
        notice = OFFICIAL_NOTICE if plan.official_required else NOTICE
        if not plan.official_required:
            warnings.append("PROJECT_OR_SYSTEM_GUIDES_NOT_OFFICIAL_POLICY")
        return PolicyResult(**base, mode=mode, evidenceStatus="CANDIDATES_ONLY", answer=answer+"\n\n"+notice,
                            citations=citations, timingMs=round((perf_counter()-started)*1000, 3),
                            warnings=warnings, generationMode=generation_mode, stageTimingsMs=timings)
