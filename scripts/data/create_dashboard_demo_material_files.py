#!/usr/bin/env python3
"""Create the local fictional PDFs referenced by dashboard demo SQL.

The generated files are intentionally small, reproducible and ignored by Git.
They contain no real identity or business material.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from generate_dashboard_demo_data import DEMO_MATERIAL_FILES


def main() -> None:
    parser = argparse.ArgumentParser(description="Create fictional dashboard demo material PDFs")
    parser.add_argument("--output-dir", type=Path, default=Path("data/uploads"))
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    for filename, content in DEMO_MATERIAL_FILES.items():
        target = args.output_dir / filename
        target.write_bytes(content)
    print(f"Created {len(DEMO_MATERIAL_FILES)} demo material files in {args.output_dir}")


if __name__ == "__main__":
    main()
