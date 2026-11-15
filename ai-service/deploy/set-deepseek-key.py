#!/usr/bin/env python3
"""由用户在服务器交互终端输入已有 Key，不经过聊天、命令历史或云任务正文。"""
from getpass import getpass
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


def main():
    if os.geteuid() != 0 or not sys.stdin.isatty():
        raise SystemExit("Run with sudo in an interactive server terminal; do not put the key in a command argument.")
    key = getpass("DeepSeek API Key (hidden input): ").strip()
    if not re.fullmatch(r"[A-Za-z0-9._-]{16,512}", key):
        raise SystemExit("Invalid key format; no configuration changed.")
    directory = Path("/etc/yixun-ai")
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=directory, delete=False) as stream:
        stream.write("YIXUN_AI_GENERATION_KEY=" + key + "\n")
        temporary = Path(stream.name)
    temporary.chmod(0o600)
    temporary.replace(directory / "provider.env")
    subprocess.run(["systemctl", "restart", "yixun-ai"], check=True)
    print("Key saved privately and service restarted. No key was printed.")


if __name__ == "__main__":
    main()
