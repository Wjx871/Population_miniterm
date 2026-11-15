"""在交互终端录入百度 OCR 应用凭证，写入本机后端忽略文件。"""

from getpass import getpass
import os
from pathlib import Path
import re
import sys
from tempfile import NamedTemporaryFile


def main() -> None:
    if not sys.stdin.isatty():
        raise SystemExit("请在自己的终端窗口交互运行。")
    config = Path(__file__).resolve().parents[2] / ".env.local"
    if not config.is_file() or config.is_symlink():
        raise SystemExit("未找到常规 .env.local，请先完成项目本地配置。")
    settings = {
        "IDCARD_OCR_BAIDU_API_KEY": getpass("百度 OCR 应用 API Key（隐藏输入）：").strip(),
        "IDCARD_OCR_BAIDU_SECRET_KEY": getpass("百度 OCR 应用 Secret Key（隐藏输入）：").strip(),
    }
    if not all(re.fullmatch(r"[A-Za-z0-9_-]{8,256}", value) for value in settings.values()):
        raise SystemExit("凭证格式有误，配置未修改。")
    lines = [
        line for line in config.read_text(encoding="utf-8").splitlines()
        if line.strip().removeprefix("export ").partition("=")[0].strip() not in settings
    ]
    lines.extend(f"{name}={value}" for name, value in settings.items())
    with NamedTemporaryFile(mode="w", encoding="utf-8", dir=config.parent, delete=False) as output:
        temporary = Path(output.name)
        try:
            output.write("\n".join(lines) + "\n")
            output.close()
            os.chmod(temporary, 0o600)
            temporary.replace(config)
        finally:
            temporary.unlink(missing_ok=True)
    print("OCR 应用凭证已写入本机后端配置，重启后端后生效。")


if __name__ == "__main__":
    main()
