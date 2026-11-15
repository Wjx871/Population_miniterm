"""可选的远程文本向量与重排客户端，仅索引已入库的公开政策。"""

from math import isfinite, sqrt
from urllib.parse import urlsplit

import httpx


class RemoteRetrievalError(RuntimeError):
    pass


class RemotePolicyRetrieval:
    def __init__(self, index, base_url, api_key, embedding_model, rerank_model, *, transport=None):
        endpoint = urlsplit(base_url)
        if (endpoint.scheme != "https" or not endpoint.hostname or endpoint.username or endpoint.password
                or endpoint.query or endpoint.fragment or not api_key or not embedding_model):
            raise ValueError("Remote retrieval needs an HTTPS API origin, model and dedicated key")
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.transport = transport
        self.embedding_model = embedding_model
        self.rerank_model = rerank_model
        self.versions = {"embedding": embedding_model}
        if rerank_model:
            self.versions["reranker"] = rerank_model
        self.texts = {c.key: f"{c.title}　{c.section}\n{c.content}" for c in index.chunks
                      if c.source_type == "OFFICIAL_POLICY" and c.source_url and c.source_url.startswith("https://")}
        self.vectors = {}

    def _request(self, route, payload):
        try:
            with httpx.Client(timeout=7.0, transport=self.transport, trust_env=False) as client:
                response = client.post(f"{self.base_url}/{route}", json=payload,
                                       headers={"Authorization": f"Bearer {self.api_key}"})
                response.raise_for_status()
                return response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise RemoteRetrievalError(f"{route.upper()}_UNAVAILABLE") from exc

    @staticmethod
    def _ordered(data, count, field):
        if not isinstance(data, list) or len(data) != count:
            raise RemoteRetrievalError("RETRIEVAL_RESPONSE_INVALID")
        indexed = {}
        for row in data:
            if not isinstance(row, dict) or type(row.get("index")) is not int or field not in row:
                raise RemoteRetrievalError("RETRIEVAL_RESPONSE_INVALID")
            indexed[row["index"]] = row[field]
        if set(indexed) != set(range(count)):
            raise RemoteRetrievalError("RETRIEVAL_RESPONSE_INVALID")
        return [indexed[i] for i in range(count)]

    def _embed(self, texts):
        result = self._request("embeddings", {"model": self.embedding_model, "input": texts})
        vectors = []
        for values in self._ordered(result.get("data") if isinstance(result, dict) else None, len(texts), "embedding"):
            if (not isinstance(values, list) or not 1 <= len(values) <= 4096
                    or any(type(value) not in (float, int) or abs(value) > 1e6 or not isfinite(value) for value in values)):
                raise RemoteRetrievalError("EMBEDDING_VECTOR_INVALID")
            norm = sqrt(sum(value * value for value in values))
            if not norm:
                raise RemoteRetrievalError("EMBEDDING_VECTOR_INVALID")
            vectors.append(tuple(value / norm for value in values))
        return vectors

    def supports(self, allowed):
        return not self.texts.keys().isdisjoint(allowed)

    def search(self, question, allowed, limit):
        keys = sorted(self.texts.keys() & allowed)
        if not keys:
            return []
        missing = [key for key in keys if key not in self.vectors]
        for offset in range(0, len(missing), 16):
            batch = missing[offset:offset + 16]
            self.vectors.update(zip(batch, self._embed([self.texts[key] for key in batch]), strict=True))
        query, = self._embed([question])
        if any(len(self.vectors[key]) != len(query) for key in keys):
            self.vectors.clear()
            raise RemoteRetrievalError("EMBEDDING_DIMENSION_MISMATCH")
        rows = [(key, sum(left * right for left, right in zip(self.vectors[key], query, strict=True))) for key in keys]
        return sorted(rows, key=lambda row: (-row[1], row[0]))[:limit]

    def rerank(self, question, candidates):
        if not self.rerank_model or not candidates or not all(key in self.texts for key in candidates):
            raise RemoteRetrievalError("RERANK_NOT_APPLICABLE")
        result = self._request("rerank", {"model": self.rerank_model, "query": question,
                                          "documents": [self.texts[key] for key in candidates],
                                          "top_n": len(candidates), "return_documents": False})
        scores = self._ordered(result.get("results") if isinstance(result, dict) else None, len(candidates), "relevance_score")
        if any(type(score) not in (int, float) or abs(score) > 1e6 or not isfinite(score) for score in scores):
            raise RemoteRetrievalError("RERANK_SCORE_INVALID")
        return dict(zip(candidates, scores, strict=True))
