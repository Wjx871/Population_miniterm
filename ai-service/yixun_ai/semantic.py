"""可选的本地语义检索与重排序；不在请求过程中下载模型。"""

from hashlib import sha256
import json
import platform
from pathlib import Path
import tempfile


class SemanticModels:
    def __init__(self, model_dir: Path, cache_dir: Path, index):
        if platform.system() == "Darwin":
            raise RuntimeError("本机模型推理已停用，请改用远程检索服务。")
        import numpy as np
        import torch
        from sentence_transformers import SentenceTransformer, CrossEncoder

        self.np = np
        torch.set_num_threads(2)
        manifest = json.loads((model_dir.parents[1] / "model-manifest.json").read_text()) if (model_dir.parents[1] / "model-manifest.json").exists() else {}
        self.versions = {}
        # 校验缓存模型，避免同路径换权重后复用旧索引。
        for role in ("embedding", "reranker"):
            spec = manifest.get(role)
            if not spec:
                raise ValueError(f"Missing manifest for {role}; run prepare_models.py")
            for name, expected in spec["files"].items():
                digest = sha256()
                with (model_dir / role / name).open("rb") as stream:
                    for block in iter(lambda: stream.read(1024*1024), b""):
                        digest.update(block)
                if digest.hexdigest() != expected:
                    raise ValueError(f"Model hash mismatch: {role}")
            self.versions[role] = f'{spec["repo"]}@{spec["revision"]}'
        self.encoder = SentenceTransformer(str(model_dir / "embedding"), device="cpu", local_files_only=True, trust_remote_code=False)
        self.reranker = CrossEncoder(str(model_dir / "reranker"), device="cpu", max_length=384,
                                    local_files_only=True, trust_remote_code=False)
        self.texts = [f"{c.title} / {c.section}\n{c.content}" for c in index.chunks]
        key = sha256((index.version + self.versions["embedding"] + "title-section-v1").encode()).hexdigest()
        cache_dir.mkdir(parents=True, exist_ok=True)
        path = cache_dir / (key + ".npz")
        matrix = None
        if path.exists():
            try:
                with np.load(path, allow_pickle=False) as stored:
                    candidate = stored["embeddings"]
                    dimension = (self.encoder.get_embedding_dimension() if hasattr(self.encoder, "get_embedding_dimension")
                                 else self.encoder.get_sentence_embedding_dimension())
                    if candidate.shape == (len(self.texts), dimension) and np.isfinite(candidate).all() and np.allclose(np.linalg.norm(candidate, axis=1), 1, atol=.001):
                        matrix = candidate
            except (ValueError, OSError, KeyError):
                pass
        if matrix is None:
            matrix = self.encoder.encode(self.texts, batch_size=8, normalize_embeddings=True, show_progress_bar=False)
            with tempfile.NamedTemporaryFile(dir=cache_dir, suffix=".npz", delete=False) as tmp:
                np.savez_compressed(tmp, embeddings=matrix)
                tmp_path = Path(tmp.name)
            tmp_path.replace(path)
        self.matrix = matrix
        self.keys = [c.key for c in index.chunks]
        self.by_key = {key: i for i, key in enumerate(self.keys)}

    def search(self, question: str, allowed_keys: set[str], limit: int):
        vector = self.encoder.encode(["为这个句子生成表示以用于检索相关文章：" + question], normalize_embeddings=True, show_progress_bar=False)[0]
        scores = self.matrix @ vector
        ordered = sorted((i for i, key in enumerate(self.keys) if key in allowed_keys), key=lambda i: (-float(scores[i]), self.keys[i]))
        return [(self.keys[i], float(scores[i])) for i in ordered[:limit]]

    def rerank(self, question: str, keys: list[str]) -> dict[str, float]:
        pairs = [(question, self.texts[self.by_key[key]]) for key in keys]
        if not pairs:
            return {}
        # raw logits；不包装成“答案正确概率”。
        import torch
        scores = self.reranker.predict(pairs, batch_size=4, show_progress_bar=False, activation_fn=torch.nn.Identity())
        return dict(zip(keys, (float(v) for v in scores)))
