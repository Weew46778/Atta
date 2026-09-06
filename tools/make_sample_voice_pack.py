#!/usr/bin/env python3
"""
Create a working MineAva sample voice-pack ZIP without requiring licensed audio.

It writes short two-tone WAVs (so the full Install -> Enable -> Playback path can
be tested), builds the same voice_pack.json that a real pack uses, and zips it.

Usage:
    python3 tools/make_sample_voice_pack.py --output artifacts/voice-pack-ava-sample.zip

To produce a REAL female Persian voice pack, put licensed WAVs in a folder and use
tools/make_voice_pack.py instead; replace each generated WAV with the real recording
keeping the same filenames.
"""

from __future__ import annotations

import argparse
import json
import math
import struct
import tempfile
import wave
import zipfile
from pathlib import Path

PHRASES = [
    ("سلام", "salam.wav"),
    ("سلام! من آوا هستم", "ava-hello.wav"),
    ("خوش آمدی", "welcome.wav"),
    ("سرور روشن شد", "server-on.wav"),
    ("خاموش شد", "server-off.wav"),
    ("منابع آماده است", "resources.wav"),
    ("بکاپ آماده شد", "backup.wav"),
    ("خروجی آماده است", "output.wav"),
    ("خطا", "error.wav"),
    ("ارسال شد", "sent.wav"),
]

SAMPLE_RATE = 8000
DURATION = 0.45


def write_tone(path: Path) -> None:
    count = int(SAMPLE_RATE * DURATION)
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for i in range(count):
            t = i / SAMPLE_RATE
            freq = 620.0 if i < (count * 0.45) else 940.0
            value = int(12000.0 * math.sin(2.0 * math.pi * freq * t))
            frames += struct.pack("<h", max(-32768, min(32767, value)))
        wf.writeframes(bytes(frames))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", type=Path, default=Path("artifacts/voice-pack-ava-sample.zip"))
    ap.add_argument("--name", default="Ava (نمونه)")
    args = ap.parse_args()

    with tempfile.TemporaryDirectory() as td:
        work = Path(td)
        manifest = {
            "voice": args.name,
            "language": "fa-IR",
            "speed": 1.0,
            "note": "Sample test pack with generated tone WAVs. Replace files with the licensed female Persian recordings.",
            "phrases": {phrase: fname for phrase, fname in PHRASES},
        }
        for _, fname in PHRASES:
            write_tone(work / fname)

        args.output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("voice_pack.json", json.dumps(manifest, ensure_ascii=False, indent=2))
            for _, fname in PHRASES:
                zf.write(work / fname, arcname=fname)

    print(f"OK: {args.output} ({len(PHRASES)} phrases, {args.output.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
