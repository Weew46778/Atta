#!/usr/bin/env python3
"""
Build a MineAva Persian voice-pack ZIP from a folder of WAV/OGG/MP3 files.

Usage:
    python3 tools/make_voice_pack.py \
        --name "Ava" \
        --voice-dir /path/to/persian/female/wavs \
        --output ./ava-voice.zip \
        [--extra phrases_extra.json] \
        [--note "Licensed female Persian voice"]

The ZIP layout produced:

    voice_pack.json
    salam.wav
    ava-hello.wav
    ...

If `--extra` JSON is provided with keys mapping a phrase to a file name,
those phrases are added to `voice_pack.json`. Otherwise every audio file in
`voice-dir` becomes a phrase key derived from its filename (drop extension,
replace `-`/`_` with spaces, lowercased).

This script deliberately does NOT contain copyrighted audio. It expects you to
point it at recordings you have the right to distribute inside the app.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

AUDIO_EXTS = {".wav", ".ogg", ".mp3", ".m4a", ".flac", ".aac", ".opus", ".aiff"}


def phrase_for_file(name: str) -> str:
    stem = Path(name).stem
    return stem.replace("_", " ").replace("-", " ").strip().lower()


def collect_default_phrases(voice_dir: Path) -> dict[str, str]:
    phrases: dict[str, str] = {}
    for f in sorted(voice_dir.iterdir()):
        if f.is_file() and f.suffix.lower() in AUDIO_EXTS:
            phrases[phrase_for_file(f.name)] = f.name
    return phrases


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--name", default="Ava", help="Voice name shown in the app.")
    ap.add_argument("--voice-dir", required=True, type=Path, help="Directory with audio files.")
    ap.add_argument("--output", required=True, type=Path, help="Output .zip path.")
    ap.add_argument("--extra", type=Path, default=None, help="Optional phrases_extra.json.")
    ap.add_argument("--note", default="", help="Optional pack note.")
    ap.add_argument("--language", default="fa-IR", help="BCP-47 language tag.")
    args = ap.parse_args()

    if not args.voice_dir.is_dir():
        print(f"ERROR: voice dir not found: {args.voice_dir}", file=sys.stderr)
        return 2

    phrases = collect_default_phrases(args.voice_dir)

    if args.extra:
        if not args.extra.is_file():
            print(f"ERROR: extra file not found: {args.extra}", file=sys.stderr)
            return 2
        extra = json.loads(args.extra.read_text(encoding="utf-8"))
        if not isinstance(extra, dict):
            print("ERROR: --extra must contain a JSON object mapping phrase -> file.",
                  file=sys.stderr)
            return 2
        for phrase, fname in extra.items():
            # Accept both "file.wav" and a {"file":"file.wav"} object.
            if isinstance(fname, dict):
                fname = fname.get("file") or fname.get("filename")
            if not isinstance(fname, str) or not Path(fname).name:
                print(f"ERROR: bad extra phrase entry: {phrase!r}", file=sys.stderr)
                return 2
            phrases[phrase] = Path(fname).name

    if not phrases:
        print("ERROR: no audio files found in --voice-dir", file=sys.stderr)
        return 2

    # Validate referenced files actually exist.
    missing = [fn for fn in phrases.values() if not (args.voice_dir / fn).is_file()]
    if missing:
        print("ERROR: referenced files missing:", ", ".join(missing), file=sys.stderr)
        return 2

    manifest = {
        "voice": args.name,
        "language": args.language,
        "speed": 1.0,
        "note": args.note,
        "phrases": dict(sorted(phrases.items())),
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("voice_pack.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        for fname in phrases.values():
            source = args.voice_dir / Path(fname).name
            zf.write(source, arcname=Path(fname).name)

    print(f"OK: created {args.output} ({len(phrases)} phrases, "
          f"{args.output.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
