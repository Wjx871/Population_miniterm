"""构建明确白名单的轻量发布包；排除密钥、虚拟环境、模型和模型下载脚本。"""
from hashlib import sha256
import io
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "ai-service"
MODULES = ("__init__.py", "app.py", "models.py", "knowledge.py", "policy.py", "rag.py", "generation.py", "remote_retrieval.py")


def build():
    files = {"yixun_ai/" + name: (SERVICE / "yixun_ai" / name).read_bytes() for name in MODULES}
    for path in (ROOT / "src/main/resources/policy-knowledge").glob("*.md"):
        files["knowledge/" + path.name] = path.read_bytes()
    for name in ("requirements-api.lock", "deploy/bootstrap.sh", "deploy/yixun-ai.service", "deploy/set-deepseek-key.py"):
        files[name] = (SERVICE / name).read_bytes()
    requirements = files["requirements-api.lock"].decode().lower()
    assert not any(word + "==" in requirements for word in ("torch", "numpy", "transformers", "sentence-transformers", "nvidia-cuda-runtime"))
    manifest = {name: sha256(data).hexdigest() for name, data in sorted(files.items())}
    identity = sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()[:16]
    files["release-manifest.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    files["release-id"] = (identity + "\n").encode()
    output = ROOT / "tmp" / ("yixun-ai-" + identity + ".tar.gz")
    output.parent.mkdir(exist_ok=True)
    with tarfile.open(output, "w:gz") as archive:
        for name, data in sorted(files.items()):
            info = tarfile.TarInfo(name)
            info.size, info.mode, info.mtime = len(data), 0o644, 0
            archive.addfile(info, io.BytesIO(data))
    print(json.dumps({"path": str(output), "bytes": output.stat().st_size, "sha256": sha256(output.read_bytes()).hexdigest(), "release": identity}))


if __name__ == "__main__":
    build()
