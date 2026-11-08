#!/usr/bin/env python3
"""Generate small original synthetic WAV files with Cyrillic/NFC/NFD names."""
from pathlib import Path
import math
import struct
import sys
import unicodedata
import wave

def generate(root: Path):
    for name in ("Ёж", unicodedata.normalize("NFD", "Ёж")):
        folder = root / "AllMusic" / name
        folder.mkdir(parents=True, exist_ok=True)
        with wave.open(str(folder / "01 - Сигнал.wav"), "wb") as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(8000)
            samples = (int(2000 * math.sin(i * 2 * math.pi * 440 / 8000)) for i in range(800))
            f.writeframes(b"".join(struct.pack("<h", x) for x in samples))
    print("Synthetic fixtures generated; no user music used.")

if __name__ == "__main__":
    generate(Path(sys.argv[1]) if len(sys.argv) > 1 else Path("build/fixtures"))
