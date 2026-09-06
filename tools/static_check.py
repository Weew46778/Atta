#!/usr/bin/env python3
"""
Lightweight pre-build health check for MineAva.

Runs without a JDK / Android SDK and catches the most common mistakes that would
break the Kotlin/Android build:

  - unbalanced braces / parentheses / square brackets in .kt files
  - malformed JSON in assets
  - activities declared in code but missing from AndroidManifest.xml
  - required top-level files / directories missing
  - non-ASCII source files that lack a UTF-8 marker are still fine (UTF-8 default),
    but detect accidental null bytes / control chars.

Usage:
    python3 tools/static_check.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN_ROOT = ROOT / "app" / "src" / "main" / "java"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

FAILURES: list[str] = []

PAIRS = {"(": ")", "[": "]", "{": "}"}


def check_kotlin(path: Path) -> None:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        FAILURES.append(f"[encoding] {path.relative_to(ROOT)} is not valid UTF-8")
        return

    if "\x00" in text:
        FAILURES.append(f"[encoding] {path.relative_to(ROOT)} contains NUL bytes")

    # Strip string literals and comments before balancing so emoji/braces in text
    # don't skew counters. This is a heuristic, not a full parser.
    stripped = _strip_strings_and_comments(text)
    stack: list[str] = []
    for i, ch in enumerate(stripped):
        if ch in PAIRS:
            stack.append(ch)
        elif ch in ")]}":
            if not stack:
                FAILURES.append(f"[brace] {path.relative_to(ROOT)}:{i} unmatched '{ch}'")
                return
            if PAIRS[stack[-1]] != ch:
                FAILURES.append(
                    f"[brace] {path.relative_to(ROOT)}:{i} expected "
                    f"'{PAIRS[stack[-1]]}' but found '{ch}'"
                )
                return
            stack.pop()
    if stack:
        FAILURES.append(f"[brace] {path.relative_to(ROOT)} unclosed '{stack[-1]}'")


def _strip_strings_and_comments(text: str) -> str:
    out: list[str] = []
    i = 0
    n = len(text)
    state = "code"  # code | line_comment | block_comment | string
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == "code":
            if c == "/" and nxt == "/":
                state = "line_comment"
                out.append("  ")
                i += 2
                continue
            if c == "/" and nxt == "*":
                state = "block_comment"
                out.append("  ")
                i += 2
                continue
            # Kotlin raw strings: """...""" — skip fully so braces inside docs don't count.
            if c == '"' and nxt == '"' and i + 2 < n and text[i + 2] == '"':
                out.append("   ")
                i += 3
                # skip to the closing triple quote
                while i < n:
                    if (
                        text[i] == '"'
                        and i + 2 < n
                        and text[i + 1] == '"'
                        and text[i + 2] == '"'
                    ):
                        out.append("   ")
                        i += 3
                        break
                    out.append(" ")
                    i += 1
                continue
            if c == '"':
                state = "string"
                out.append(c)
                i += 1
                continue
            if c == "'":
                # Char literal; skip it as a "string" of one char.
                out.append(c)
                i += 1
                continue
            out.append(c)
            i += 1
        elif state == "line_comment":
            if c == "\n":
                state = "code"
                out.append(c)
            else:
                out.append(" ")
            i += 1
        elif state == "block_comment":
            if c == "*" and nxt == "/":
                state = "code"
                out.append("  ")
                i += 2
            else:
                out.append(" ")
                i += 1
        elif state == "string":
            out.append(c)
            if c == "\\":
                out.append(nxt or " ")
                i += 2
                continue
            if c == '"':
                state = "code"
            i += 1
    return "".join(out)


def check_json(path: Path) -> None:
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        FAILURES.append(f"[json] {path.relative_to(ROOT)}: {e}")


def check_activities() -> None:
    manifest_text = MANIFEST.read_text(encoding="utf-8")
    declared = set(re.findall(r'android:name="(\.[A-Za-z0-9_]+)"', manifest_text))
    declared_names = {m.split(".")[-1] for m in declared}
    kotlin_source = "\n".join(
        p.read_text(encoding="utf-8") for p in KOTLIN_ROOT.rglob("*.kt")
    )
    # Only classes that actually subclass an Android Activity should be declared in the manifest.
    activity_classes = {
        m.group(1)
        for m in re.finditer(
            r"^class\s+([A-Za-z0-9_]+)\s*:([^\n{]+?)\b(AppCompatActivity|Activity|ListActivity|FragmentActivity)\b",
            kotlin_source,
            re.M,
        )
    }
    orphan = activity_classes - declared_names
    if orphan:
        FAILURES.append(
            f"[manifest] activity classes declared in code but not in AndroidManifest: "
            f"{sorted(orphan)}"
        )


def main() -> int:
    if not KOTLIN_ROOT.is_dir():
        FAILURES.append(f"[layout] missing {KOTLIN_ROOT.relative_to(ROOT)}")
    if not MANIFEST.is_file():
        FAILURES.append(f"[layout] missing {MANIFEST.relative_to(ROOT)}")

    for p in sorted(KOTLIN_ROOT.rglob("*.kt")):
        check_kotlin(p)

    for p in sorted(ASSETS.glob("*.json")) if ASSETS.is_dir() else []:
        check_json(p)

    if MANIFEST.is_file():
        check_activities()

    if FAILURES:
        print("HEALTH CHECK FAILED:", file=sys.stderr)
        for f in FAILURES:
            print("  -", f, file=sys.stderr)
        return 1

    kt_count = len(list(KOTLIN_ROOT.rglob("*.kt")))
    print(f"HEALTH CHECK OK: {kt_count} Kotlin files, assets + manifest valid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
