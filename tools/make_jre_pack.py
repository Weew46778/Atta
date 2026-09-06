#!/usr/bin/env python3
"""
Package a portable Java runtime (JRE) for MineAva's on-device Java server.

MineAva's `OnDeviceJavaServerManager` expects a ZIP that extracts to a folder
containing `bin/java`, `lib`, `conf`, etc. This script packages any directory that
already has that layout, so you can turn a mobile/ARM JRE build into an installable
MineAva JRE pack.

Usage:
    python3 tools/make_jre_pack.py \
        --jre-dir /path/to/mobile-jre \
        --output artifacts/mineava-jre-arm64.zip

The script does NOT download a JRE, because there is no single universal, license-safe
"run-on-any-Android" JVM. It packages the one you already have (e.g. a ported OpenJDK
build for your phone architecture). Check the runtime's license before distributing.
"""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

REQUIRED = ("bin/java", "lib", "conf", "legal", "release")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--jre-dir", required=True, type=Path)
    ap.add_argument("--output", type=Path, default=Path("artifacts/mineava-jre-arm64.zip"))
    ap.add_argument("--include", nargs="*", default=[],
                    help="Extra top-level entries to include (e.g. include/jni).")
    args = ap.parse_args()

    if not args.jre_dir.is_dir():
        print(f"ERROR: JRE dir not found: {args.jre_dir}", file=sys.stderr)
        return 2

    missing = [p for p in REQUIRED if not (args.jre_dir / p).exists()]
    if missing:
        print(
            "ERROR: JRE directory is missing required entries: " + ", ".join(missing),
            file=sys.stderr,
        )
        return 2

    entries = list(REQUIRED) + list(args.include)
    args.output.parent.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for top in entries:
            base = Path(args.jre_dir) / top
            if not base.exists():
                continue
            for path in sorted(base.rglob("*")):
                if path.is_file():
                    arc = Path(top) / path.relative_to(base)
                    zf.write(path, arcname=arc.as_posix())

    print(f"OK: packaged {args.output} ({args.output.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
