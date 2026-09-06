#!/usr/bin/env python3
"""
Render a real Persian (female) voice pack from a Piper TTS ONNX model.

This is the honest way to produce the WAV files for a real embedded female Persian
voice: you install `piper-tts` on a desktop/build machine, download an open fa-IR
Piper model, and run this script. The result is a ZIP in the exact MineAva voice-pack
format (voice_pack.json + WAVs), which can be imported in-app.

Pipeline:
  1. pip install piper-tts
  2. Either use an existing fa-IR model, e.g.:
       huggingface-cli download MahtaFetrat/Mana-Persian-Piper
     or download rhasspy/piper-voices into model/
  3. python3 tools/render_voice_pack_from_piper.py \
       --model ./fa_IR-mana-medium.onnx \
       --config ./fa_IR-mana-medium.onnx.json \
       --output artifacts/ava-real-voice.zip

Phrases can be provided with --phrases-json, otherwise the script uses the same
Persian phrases that the app understands best. `piper` must be on PATH.

This script does NOT bundle any audio; it is a prover/composer tool.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

DEFAULT_PHRASES: dict[str, str] = {
    "سلام": "salam.wav",
    "سلام! من آوا هستم": "ava-hello.wav",
    "خوش آمدی": "welcome.wav",
    "سرور روشن شد": "server-on.wav",
    "خاموش شد": "server-off.wav",
    "منابع آماده است": "resources.wav",
    "بکاپ آماده شد": "backup.wav",
    "خروجی آماده است": "output.wav",
    "خطا": "error.wav",
    "ارسال شد": "sent.wav",
}


def find_piper() -> str | None:
    for name in ("piper", "piper-tts"):
        import shutil

        p = shutil.which(name)
        if p:
            return p
    return None


def render(model: Path, config: Path | None, phrase: str, wav: Path) -> None:
    piper = find_piper()
    if not piper:
        print("ERROR: piper-tts is not installed. Run: pip install piper-tts", file=sys.stderr)
        raise SystemExit(2)

    args = [piper, "--model", str(model)]
    if config:
        args += ["--config", str(config)]
    args += ["--output_file", str(wav)]

    proc = subprocess.run(
        args,
        input=phrase.encode("utf-8"),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"piper failed for {phrase!r}: {proc.stderr.decode('utf-8', 'replace')}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--model", required=True, type=Path, help="Path to .onnx Piper model")
    ap.add_argument("--config", type=Path, default=None, help="Path to .onnx.json model config")
    ap.add_argument("--output", type=Path, default=Path("artifacts/ava-real-voice.zip"))
    ap.add_argument("--phrases-json", type=Path, default=None, help="Optional dict phrase -> filename")
    ap.add_argument("--voice", default="آوا (مریم)")
    args = ap.parse_args()

    if not args.model.is_file():
        print(f"ERROR: model not found: {args.model}", file=sys.stderr)
        return 2
    if args.config and not args.config.is_file():
        print(f"ERROR: config not found: {args.config}", file=sys.stderr)
        return 2

    phrases = DEFAULT_PHRASES
    if args.phrases_json:
        data = json.loads(args.phrases_json.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            print("ERROR: --phrases-json must be a JSON object", file=sys.stderr)
            return 2
        phrases = data

    if not find_piper():
        print("ERROR: piper-tts not found. Make sure the `piper` CLI is on PATH.", file=sys.stderr)
        return 2

    manifest = {
        "voice": args.voice,
        "language": "fa-IR",
        "speed": 1.0,
        "note": "Rendered locally with piper-tts from an open fa-IR model. Check the model license before distribution.",
        "phrases": phrases,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as td:
        work = Path(td)
        for idx, (phrase, fname) in enumerate(phrases.items(), start=1):
            wav = work / Path(fname).name
            print(f"[{idx}/{len(phrases)}] {phrase}")
            render(args.model, args.config, phrase, wav)

        with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("voice_pack.json", json.dumps(manifest, ensure_ascii=False, indent=2))
            for _, fname in phrases.items():
                zf.write(work / Path(fname).name, arcname=Path(fname).name)

    print(f"OK: real voice pack written to {args.output} ({len(phrases)} phrases)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
