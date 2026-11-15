"""复用 Java 基线的分块和评分规则，不将词法检索声称为语义检索。"""

from collections import Counter
from dataclasses import dataclass
from datetime import date
from hashlib import sha256
from math import log
from pathlib import Path
import re


def normalize(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def ngrams(text: str) -> list[str]:
    return [text[i:i + 2] for i in range(len(text))]


@dataclass(frozen=True)
class Chunk:
    title: str
    section: str
    category: str
    source_type: str
    version: str
    logical_path: str
    effective_date: str | None
    content: str
    region_codes: tuple[str, ...] = ()
    expires_on: str | None = None
    source_url: str | None = None
    aliases: tuple[str, ...] = ()

    @property
    def key(self) -> str:
        return f"{self.logical_path}#{self.section}"


class KnowledgeIndex:
    engine = "java-bigram-bm25-style-v1"

    def __init__(self, directory: Path):
        files = sorted(directory.glob("*.md"))
        if not files:
            raise ValueError("Knowledge directory has no Markdown documents")
        digest = sha256()
        chunks: list[Chunk] = []
        paths: set[str] = set()
        for path in files:
            raw = path.read_bytes()
            digest.update(path.name.encode() + b"\0" + raw + b"\0")
            parsed = self._parse(raw.decode("utf-8-sig"))
            logical_path = parsed[0].logical_path if parsed else ""
            if not parsed or logical_path in paths:
                raise ValueError("Empty document or duplicate logical path")
            paths.add(logical_path)
            chunks.extend(parsed)
        self.chunks = tuple(chunks)
        self.document_count = len(files)
        self.version = digest.hexdigest()
        self._frequencies = [Counter(ngrams(normalize(c.content))) for c in chunks]
        # 与 Java 一样：文档频率包含标题/章节，词频来自正文；不是标准分词 BM25。
        self._df = Counter(t for c in chunks for t in set(ngrams(normalize(c.content + c.title + c.section))))

    @staticmethod
    def _parse(markdown: str) -> list[Chunk]:
        parts = markdown.split("---", 2)
        if len(parts) != 3 or parts[0].strip():
            raise ValueError("Knowledge document needs front matter")
        meta = dict(line.split(":", 1) for line in parts[1].splitlines() if ":" in line)
        meta = {key.strip(): value.strip() for key, value in meta.items()}
        if not meta.get("logicalPath"):
            raise ValueError("Knowledge document needs logicalPath")
        for key in ("effectiveDate", "expiresOn", "publishedDate", "reviewedDate"):
            if meta.get(key):
                date.fromisoformat(meta[key])
        if meta.get("effectiveDate") and meta.get("expiresOn") and meta["effectiveDate"] > meta["expiresOn"]:
            raise ValueError("Knowledge validity interval is reversed")
        section, lines, chunks = "概述", [], []

        def flush() -> None:
            content = "\n".join(lines).strip()
            if content:
                chunks.append(Chunk(meta.get("title", "业务办理指南"), section,
                                    meta.get("category", "业务办理"), meta.get("sourceType", "PROJECT_GUIDE"),
                                    meta.get("version", "V1"), meta["logicalPath"],
                                    meta.get("effectiveDate"), content,
                                    tuple(v.strip() for v in meta.get("regionCodes", "").split(",") if v.strip()),
                                    meta.get("expiresOn"), meta.get("sourceUrl"),
                                    tuple(v.strip() for v in meta.get("aliases", "").split(";") if v.strip())))

        for line in parts[2].splitlines():
            if line.startswith("## "):
                flush()
                section, lines = line[3:].strip(), []
            else:
                lines.append(line)
        flush()
        if len({c.key for c in chunks}) != len(chunks):
            raise ValueError("Duplicate section in document")
        return chunks

    def search(self, question: str, top_k: int = 4, allowed_keys: set[str] | None = None) -> list[tuple[Chunk, float]]:
        if not 1 <= top_k <= 64:
            raise ValueError("internal candidate count must be between 1 and 64")
        tokens = ngrams(normalize(question))
        hits = []
        for chunk, frequencies in zip(self.chunks, self._frequencies):
            if allowed_keys is not None and chunk.key not in allowed_keys:
                continue
            heading = normalize(chunk.title + chunk.category + chunk.section)
            score = 0.0
            for token in tokens:
                frequency = frequencies[token]
                if frequency:
                    df = self._df[token]
                    score += log((len(self.chunks) - df + .5) / (df + .5) + 1) * (frequency * 2.2 / (frequency + 1.2))
                if token in heading:
                    score += 1.2
            if score > 0:
                hits.append((chunk, score))
        return sorted(hits, key=lambda item: (-item[1], item[0].key))[:top_k]
