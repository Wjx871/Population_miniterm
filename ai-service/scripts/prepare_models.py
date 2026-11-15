"""下载固定 revision 的模型到项目缓存，并保存校验清单。"""
import argparse
import platform
from hashlib import sha256
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPECS = {
    'embedding': ('BAAI/bge-small-zh-v1.5', None),
    'reranker': ('cross-encoder/mmarco-mMiniLMv2-L12-H384-v1', None),
    'generation': ('Qwen/Qwen2.5-3B-Instruct-GGUF', 'qwen2.5-3b-instruct-q4_k_m.gguf'),
}

def file_hash(path):
    digest=sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):
            digest.update(block)
    return digest.hexdigest()

def main():
    if platform.system() == 'Darwin':
        raise SystemExit('用户已禁止在本机下载模型。请使用远程 API 或独立远程服务器；本次未下载任何文件。')
    from huggingface_hub import HfApi, snapshot_download, hf_hub_download
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only', choices=list(SPECS))
    args=parser.parse_args()
    manifest_path=ROOT/'model-manifest.json'
    manifest=json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    for role,(repo,filename) in SPECS.items():
        if args.only and role != args.only: continue
        pinned=manifest.get(role, {})
        revision=pinned.get('revision') or HfApi().model_info(repo).sha
        target=ROOT/'cache/models'/role
        if pinned.get('files') and all((target/name).is_file() and file_hash(target/name)==value for name,value in pinned['files'].items()):
            print(role,repo,revision,'already verified',flush=True)
            continue
        if filename:
            hf_hub_download(repo, filename, revision=revision, local_dir=target)
        else:
            snapshot_download(repo, revision=revision, local_dir=target,
                allow_patterns=['*.json','*.txt','*.model','model.safetensors','1_Pooling/*'],
                ignore_patterns=['onnx/*','openvino/*'])
        hashes={}
        for path in sorted(target.rglob('*')):
            if path.is_file() and '.cache' not in path.relative_to(target).parts:
                hashes[path.relative_to(target).as_posix()]=file_hash(path)
        manifest[role]={'repo':repo,'revision':revision,'files':hashes,'filename':filename}
        manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
        print(role, repo, revision, 'files:',len(hashes),flush=True)

if __name__=='__main__': main()
