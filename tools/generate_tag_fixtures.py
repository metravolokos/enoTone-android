#!/usr/bin/env python3
"""Generate small deterministic tag fixtures for FastAudioTagReader tests.

Bytes are built with struct.pack only: no external libraries, no timestamps and
no randomness, so regenerating the files yields identical bytes. Each fixture is
a real, byte-accurate container layout (ID3v2.3/v2.4 block, FLAC metadata
chain, MPEG-1 Layer III frames), never a mock of the parser's API.
"""
from pathlib import Path
import struct
import sys

DEFAULT_TARGET = Path(__file__).resolve().parent.parent / \
    "data" / "library" / "src" / "test" / "resources" / "tags"

MPEG_FRAME_SIZE = 417  # MPEG-1 Layer III, 128 kbps, 44.1 kHz, no padding.


def synchsafe(value: int) -> bytes:
    """ID3v2 tag/frame size: four bytes, seven significant bits each."""
    return bytes(((value >> 21) & 0x7F, (value >> 14) & 0x7F,
                  (value >> 7) & 0x7F, value & 0x7F))


def id3_frame(major: int, frame_id: str, data: bytes,
              status: int = 0, flags: int = 0) -> bytes:
    size = synchsafe(len(data)) if major == 4 else struct.pack(">I", len(data))
    return frame_id.encode("ascii") + size + bytes((status, flags)) + data


def utf16_text(text: str) -> bytes:
    """Encoding byte 1 (UTF-16 with BOM), little endian, as taggers write it."""
    return b"\x01" + b"\xFF\xFE" + text.encode("utf-16-le")


def utf8_text(text: str) -> bytes:
    return b"\x03" + text.encode("utf-8")


def latin1_text(raw: bytes) -> bytes:
    """Encoding byte 0 (spec: ISO-8859-1) with caller-supplied payload bytes."""
    return b"\x00" + raw


def id3_tag(major: int, flags: int, body: bytes) -> bytes:
    return b"ID3" + bytes((major, 0, flags)) + synchsafe(len(body)) + body


def unsynchronise(data: bytes) -> bytes:
    """Insert 0x00 after 0xFF followed by 0x00 or a byte >= 0xE0 (ID3v2 rules)."""
    out = bytearray()
    for index, byte in enumerate(data):
        out.append(byte)
        if byte == 0xFF and index + 1 < len(data):
            following = data[index + 1]
            if following == 0x00 or following >= 0xE0:
                out.append(0x00)
    return bytes(out)


def mpeg1_layer3_frames(count: int, seed: int = 0) -> bytes:
    """Constant-bitrate audio: 0xFF 0xFB 0x90 0x00 then low-entropy payload."""
    body = bytes(((seed + index) % 128) for index in range(MPEG_FRAME_SIZE - 4))
    frame = b"\xFF\xFB\x90\x00" + body
    return frame * count


def flac_stream_info_payload(sample_rate: int = 44_100,
                             channels: int = 2, total_samples: int = 0) -> bytes:
    """34-byte STREAMINFO: min/max block size, frame sizes, then packed fields."""
    packed = (sample_rate << 44) | ((channels - 1) << 41) | (15 << 36) | total_samples
    return (struct.pack(">HH", 4096, 4096) + b"\x00\x00\x07\x68\x00\x00\x07\x68" +
            struct.pack(">Q", packed) + b"\x00" * 16)


def vorbis_comment(fields: list[str], vendor: str = "enotone-fixture") -> bytes:
    """VORBIS_COMMENT payload: LE vendor length, vendor, count, LE entries."""
    raw_vendor = vendor.encode("utf-8")
    out = struct.pack("<I", len(raw_vendor)) + raw_vendor
    out += struct.pack("<I", len(fields))
    for field in fields:
        encoded = field.encode("utf-8")
        out += struct.pack("<I", len(encoded)) + encoded
    return out


def flac_block(block_type: int, payload: bytes, last: bool = False,
               declared_size: int | None = None) -> bytes:
    size = len(payload) if declared_size is None else declared_size
    return bytes(((0x80 if last else 0x00) | block_type,)) + \
        struct.pack(">I", size)[1:] + payload


def write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    print(f"{path.name}: {len(data)} bytes; first 32: {data[:32].hex(' ')}")


def build_id3v23_ext_header() -> bytes:
    """ID3v2.3 with the extended-header flag (0x40) and a valid extended header."""
    frames = (id3_frame(3, "TIT2", utf16_text("Extended Header")) +
              id3_frame(3, "TPE1", utf16_text("Header Ensemble")))
    # v2.3: the size field counts the six bytes that follow it (two flag bytes
    # and the padding-size field), never itself.
    ext_header = struct.pack(">I", 6) + b"\x00\x00" + struct.pack(">I", 0)
    body = ext_header + frames
    return id3_tag(3, 0x40, body) + mpeg1_layer3_frames(40, seed=5)


def build_id3v24_unsync() -> bytes:
    """ID3v2.4, unsynchronisation flag 0x80, body genuinely de-unsynchronisable."""
    frames = (id3_frame(4, "TIT2", utf16_text("Unsync \u00ff Title")) +
              id3_frame(4, "TPE1", utf16_text("Unsync Artist")))
    return id3_tag(4, 0x80, unsynchronise(frames)) + mpeg1_layer3_frames(40, seed=9)


def build_flac_id3_preamble() -> bytes:
    """ID3v2.3 block written by taggers, then fLaC at a non-zero offset."""
    preamble = id3_tag(3, 0x00,
                       id3_frame(3, "TIT2", utf16_text("ID3 Decoy")) +
                       id3_frame(3, "TPE1", utf16_text("Preamble Writer")))
    comments = vorbis_comment(["TITLE=Prelude", "ARTIST=Ensemble"])
    return (preamble + b"fLaC" +
            flac_block(0, flac_stream_info_payload()) +
            flac_block(4, comments, last=True))


def build_cp1251_tags() -> bytes:
    """Encoding byte 0 declares ISO-8859-1; the bytes are Windows-1251 Cyrillic."""
    body = (id3_frame(3, "TIT2", latin1_text("Сердце".encode("cp1251"))) +
            id3_frame(3, "TPE1", latin1_text("Кино".encode("cp1251"))))
    return id3_tag(3, 0x00, body) + mpeg1_layer3_frames(40, seed=3)


def build_flac_corrupt_streaminfo() -> bytes:
    """STREAMINFO declares 34 bytes but only 18 precede a valid VORBIS_COMMENT."""
    truncated = flac_stream_info_payload()[:18]
    comments = vorbis_comment(["TITLE=Salvage", "ARTIST=Recovery"])
    return (b"fLaC" +
            flac_block(0, truncated, declared_size=34) +
            flac_block(4, comments, last=True))


def build_mp3_cbr_no_xing() -> bytes:
    """ID3v2.3 tag and 100 CBR frames: no Xing/Info and no VBRI anywhere."""
    body = (id3_frame(3, "TIT2", utf16_text("Constant Rate")) +
            id3_frame(3, "TPE1", utf16_text("Sine Worker")))
    return id3_tag(3, 0x00, body) + mpeg1_layer3_frames(100, seed=11)


def build_mp3_no_tags() -> bytes:
    """Plain MPEG audio: no ID3v2 header and no trailing ID3v1 'TAG' block."""
    return mpeg1_layer3_frames(40, seed=17)


CP1251_NOTE = """cp1251_tags.mp3

ID3v2.3, TIT2 and TPE1 frames, text-encoding byte 0x00.
Per the ID3v2 specification encoding 0x00 is ISO-8859-1, but the payload bytes
are Windows-1251 (CP1251). Exact byte values:

  TIT2 payload after the encoding byte: D1 E5 F0 E4 F6 E5  = "Сердце"
  TPE1 payload after the encoding byte: CA E8 ED EE        = "Кино"

Correct behaviour: decode encoding 0x00 as Windows-1251 (or detect the
replacement-character run) and return title "Сердце", artist "Кино".
Current behaviour: CP1251 bytes read as ISO-8859-1 produce the mojibake
"Ð¡ÐµÑ€Ð´Ñ†Ðµ" / "ÐšÐ¸Ð½Ð¾".
"""


def generate(root: Path) -> None:
    write(root / "id3v23_ext_header.mp3", build_id3v23_ext_header())
    write(root / "id3v24_unsync.mp3", build_id3v24_unsync())
    write(root / "flac_id3_preamble.flac", build_flac_id3_preamble())
    write(root / "cp1251_tags.mp3", build_cp1251_tags())
    write(root / "flac_corrupt_streaminfo.flac", build_flac_corrupt_streaminfo())
    write(root / "mp3_cbr_no_xing.mp3", build_mp3_cbr_no_xing())
    write(root / "mp3_no_tags.mp3", build_mp3_no_tags())
    note = root / "cp1251_tags.txt"
    note.write_text(CP1251_NOTE, encoding="utf-8", newline="\n")
    print(f"{note.name}: {len(CP1251_NOTE.encode('utf-8'))} bytes of expected values")
    print("Tag fixtures generated; no user music used.")


if __name__ == "__main__":
    generate(Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_TARGET)
