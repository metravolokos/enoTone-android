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


def mpeg1_layer3_frame(bitrate_byte: int, padding: int, seed: int) -> bytes:
    """One MPEG-1 Layer III frame, no CRC, 44.1 kHz, payload kept low-entropy.

    The stored length is derived from the header exactly as an encoder writes
    it: 144 * kbps * 1000 / 44100 + padding.
    """
    kbps = {0x90: 128, 0xA0: 160, 0xB0: 192}[bitrate_byte]
    size = 144 * kbps * 1000 // 44_100 + padding
    header = b"\xFF\xFB" + bytes((bitrate_byte, 0x00))
    return header + bytes(((seed + index) % 128) for index in range(size - 4))


def mpeg1_layer3_vbr_frames(count: int, seed: int = 0) -> bytes:
    """Variable-bitrate audio: 128 and 192 kbps frames alternate, no Xing/VBRI."""
    out = b""
    for index in range(count):
        bitrate_byte = 0x90 if index % 2 == 0 else 0xB0
        out += mpeg1_layer3_frame(bitrate_byte, 0, seed + index)
    return out


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


def unsynchronised_id3_frame(major: int, frame_id: str, data: bytes) -> bytes:
    """Frame whose declared size counts the stored bytes, not the decoded ones.

    ID3v2.4 structure, 3.3: the frame size is "the size of the data in the
    final frame, after encryption, compression and unsynchronisation". A writer
    stuffs 0x00 into the frame data first and then declares the length that is
    physically on disk; tag-level flag 0x80 does not change that rule.
    """
    return id3_frame(major, frame_id, unsynchronise(data))


def build_id3v24_unsync() -> bytes:
    """ID3v2.4 (flag 0x80), per-frame unsync, two UTF-16 LE frames with BOM.

    Frame sizes here count the *stored* bytes (the inserted 0x00 included),
    which is the v2.4 model: unsync is applied to every frame and its size
    points at the data in the final frame. Both frames start with the BOM
    FF FE, so each one holds two sequences the writer must escape, and every
    inserted 0x00 shifts the next frame two bytes away from where a whole-tag
    de-unsync reader looks for it (defect R-1):

      FF FE (BOM)            stores as FF 00 FE
      FF 00 (letter U+00FF)  stores as FF 00 00
    """
    frames = (unsynchronised_id3_frame(4, "TIT2", utf16_text("Unsync \u00ff Title")) +
              unsynchronised_id3_frame(4, "TPE1", utf16_text("Ансамбль \u00ff")))
    return id3_tag(4, 0x80, frames) + mpeg1_layer3_frames(40, seed=9)


def flac_body(title: str, artist: str) -> bytes:
    comments = vorbis_comment([f"TITLE={title}", f"ARTIST={artist}"])
    return (b"fLaC" +
            flac_block(0, flac_stream_info_payload()) +
            flac_block(4, comments, last=True))


def build_flac_id3_preamble() -> bytes:
    """ID3v2.3 preamble whose body contains a literal `fLaC` decoy text frame.

    The decoy sits *before* the real marker, so a reader that only searches the
    first bytes for `fLaC` finds the decoy inside a TXXX frame and then fails on
    the metadata block header. The ID3 header size field (10 + synchsafe size)
    points exactly at the real marker, which is what a reader must use.
    """
    preamble = id3_tag(3, 0x00,
                       id3_frame(3, "TIT2", utf16_text("ID3 Decoy")) +
                       id3_frame(3, "TXXX", latin1_text(b"fLaC decoy")) +
                       id3_frame(3, "TPE1", utf16_text("Preamble Writer")))
    return preamble + flac_body("Prelude", "Ensemble")


def build_flac_id3_bad_size() -> bytes:
    """ID3v2.3 preamble with a zeroed size field: the bounded search must recover.

    No decoy here on purpose: the declared offset points at nothing, so the
    reserve window search is the only way to find the real marker.
    """
    body = (id3_frame(3, "TIT2", utf16_text("ID3 Decoy")) +
            id3_frame(3, "TPE1", utf16_text("Preamble Writer")))
    tag = bytearray(id3_tag(3, 0x00, body))
    tag[6:10] = b"\x00\x00\x00\x00"  # declared tag size says "no body"
    return bytes(tag) + flac_body("Broken Size", "Recovered")


def build_cp1251_tags() -> bytes:
    """Encoding byte 0 declares ISO-8859-1; the bytes are Windows-1251 Cyrillic."""
    body = (id3_frame(3, "TIT2", latin1_text("Сердце".encode("cp1251"))) +
            id3_frame(3, "TPE1", latin1_text("Кино".encode("cp1251"))))
    return id3_tag(3, 0x00, body) + mpeg1_layer3_frames(40, seed=3)


def build_cp1251_mixed_tags() -> bytes:
    """CP1251 under encoding byte 0, mixed with quotes, dashes and Latin words.

    Every Cyrillic word is a separate word: the Latin fragments are whole words
    (`Live`, `feat`, `Ivan`, `Part`), never glued to Cyrillic letters, which is
    what a real tagger produces for "featured artist" style titles.
    """
    title = "«Осень» — Live (feat. Ivan)"
    artist = "Їжак Ґанок і Хор"
    album = "Дорога – Part 2"
    body = (id3_frame(3, "TIT2", latin1_text(title.encode("cp1251"))) +
            id3_frame(3, "TPE1", latin1_text(artist.encode("cp1251"))) +
            id3_frame(3, "TALB", latin1_text(album.encode("cp1251"))))
    return id3_tag(3, 0x00, body) + mpeg1_layer3_frames(40, seed=13)


def build_latin1_accented_tags() -> bytes:
    """Genuine ISO-8859-1 with accented letters: CP1251 detection must stand down."""
    body = (id3_frame(3, "TIT2", latin1_text("Café Müller".encode("iso-8859-1"))) +
            id3_frame(3, "TPE1", latin1_text("Grüße".encode("iso-8859-1"))))
    return id3_tag(3, 0x00, body) + mpeg1_layer3_frames(40, seed=19)


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


def build_mp3_vbr_no_xing() -> bytes:
    """ID3v2.3 tag and alternating 128/192 kbps frames: VBR with no frame count.

    Nothing in the stream declares a frame count (no Xing/Info, no VBRI), so the
    only way to guess a duration is to assume constant bitrate. The headers
    themselves prove that assumption wrong.
    """
    body = (id3_frame(3, "TIT2", utf16_text("Variable Rate")) +
            id3_frame(3, "TPE1", utf16_text("Sine Worker")))
    return id3_tag(3, 0x00, body) + mpeg1_layer3_vbr_frames(40, seed=23)


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


CP1251_MIXED_NOTE = """cp1251_mixed_tags.mp3

ID3v2.3, TIT2 / TPE1 / TALB frames, text-encoding byte 0x00, payload bytes are
Windows-1251 (CP1251). Exact values and their CP1251 byte codes:

  TIT2 = "\u00ab\u041e\u0441\u0435\u043d\u044c\u00bb \u2014 Live (feat. Ivan)"
  TPE1 = "\u0407\u0436\u0430\u043a \u0490\u0430\u043d\u043e\u043a \u0456 \u0425\u043e\u0440"
  TALB = "\u0414\u043e\u0440\u043e\u0433\u0430 \u2013 Part 2"

  \u00ab AB   \u00bb BB   \u2014 97 (em dash)   \u2013 96 (en dash)
  \u0407 AF   \u0457 BF   \u0490 A5   \u0491 B4   \u0404 A8   \u0454 AA   \u0406 B2   \u0456 B3

Mixed content must not switch detection off: the Cyrillic words, the typographic
quotes and dashes and the Ukrainian national letters are all valid CP1251, and
the Latin fragments are separate words (`Live`, `feat`, `Ivan`, `Part`).

Correct behaviour: title "\u00ab\u041e\u0441\u0435\u043d\u044c\u00bb \u2014 Live (feat. Ivan)",
artist "\u0407\u0436\u0430\u043a \u0490\u0430\u043d\u043e\u043a \u0456 \u0425\u043e\u0440",
album "\u0414\u043e\u0440\u043e\u0433\u0430 \u2013 Part 2".
"""


LATIN1_ACCENTED_NOTE = """latin1_accented_tags.mp3

ID3v2.3, TIT2 and TPE1 frames, text-encoding byte 0x00 with genuine
ISO-8859-1 payload bytes and accented Latin letters:

  TIT2 = "Caf\u00e9 M\u00fcller"   (payload: 43 61 66 E9 20 4D FC 6C 6C 65 72)
  TPE1 = "Gr\u00fc\u00dfe"          (payload: 47 72 FC DF 65)

This is the negative guard for CP1251 detection: the same bytes read as
Windows-1251 give "Caf\u0439 M\u044cller" / "Gr\u044c\u042fe", where the Cyrillic
letters are glued to Latin ones inside a single word. Detection must refuse
CP1251 and keep the ISO-8859-1 reading.

Correct behaviour: title "Caf\u00e9 M\u00fcller", artist "Gr\u00fc\u00dfe".
"""


FLAC_ID3_PREAMBLE_NOTE = """flac_id3_preamble.flac

FLAC stream with an ID3v2.3 preamble written by a tagger. Layout on disk:

  offset 0        ID3 header "ID3" 03 00 00 + synchsafe size
  offset 10       TIT2 "ID3 Decoy" (UTF-16 LE with BOM)
  ...             TXXX frame whose payload is the literal text "fLaC decoy"
  ...             TPE1 "Preamble Writer" (UTF-16 LE with BOM)
  offset 10+size  "fLaC" marker of the real stream
  thereafter      STREAMINFO block, then VORBIS_COMMENT with TITLE/ARTIST

The TXXX payload holds the bytes 66 4C 61 43 ("fLaC") *before* the real marker,
so searching the first 64 KiB for the first `fLaC` finds the decoy inside the
tag and the metadata chain then fails to parse.

Correct behaviour: compute the preamble end as 10 + synchsafe size (a v2.4
preamble with header flag 0x10 adds its 10-byte footer) and accept the marker
only at that offset. The window search stays as the reserve for broken sizes.
Expected tags: title "Prelude", artist "Ensemble".
"""


MP3_VBR_NO_XING_NOTE = """mp3_vbr_no_xing.mp3

ID3v2.3 tag, then 40 MPEG-1 Layer III frames at 44.1 kHz whose bitrate
alternates between 128 kbps and 192 kbps. No Xing/Info header and no VBRI
header anywhere in the stream, so the file carries no frame count at all.

  frame 0: header FF FB 90 00, stored length 144 * 128000 / 44100 = 417 bytes
  frame 1: header FF FB B0 00, stored length 144 * 192000 / 44100 = 626 bytes
  frame 2: header FF FB 90 00, 417 bytes ...

A reader that looks at the first header only sees a plausible 128 kbps frame and
divides the remaining file size by 417, producing a duration that is simply
wrong: a bitrate that keeps changing is exactly what "variable bitrate" means.

Correct behaviour: verify the following headers and report an unknown duration
(null) when they disagree; never invent a number from one header.
"""


FLAC_ID3_BAD_SIZE_NOTE = """flac_id3_bad_size.flac

Same shape as flac_id3_preamble.flac, but the ID3v2.3 size field is zeroed and
there is no decoy literal. The computed preamble end is therefore offset 10,
which does not hold `fLaC`, so the reader must fall back to the bounded window
search and still find the real marker.

Correct behaviour: title "Broken Size", artist "Recovered", code FLAC_VORBIS.
"""


ID3V24_UNSYNC_NOTE = """id3v24_unsync.mp3

ID3v2.4, tag header flag 0x80 (unsynchronisation), two text frames, both UTF-16
with BOM FF FE (encoding byte 0x01), little endian:

  TIT2 = "Unsync \u00ff Title"   (14 characters, 31 stored-less bytes of data)
  TPE1 = "\u0410\u043d\u0441\u0430\u043c\u0431\u043b\u044c \u00ff"      (10 characters)

Per ID3v2.4 structure 3.3 the frame size field is "the size of the data in the
final frame, after ... unsynchronisation": it counts the 0x00 bytes that the
writer inserted, so the stored body is larger than the decoded body.

Escaped sequences inside the frames (raw bytes on disk):

  BOM            FF FE  -> FF 00 FE
  letter U+00FF  FF 00  -> FF 00 00

TIT2 declares 33 (31 decoded + 2 inserted), TPE1 declares 25 (23 decoded + 2
inserted). Tag body size is 78 on disk, 74 after de-unsynchronisation, so a
reader that de-unsynchronises the whole tag and then trusts the stored frame
sizes lands two bytes past TPE1's header and loses the artist.

Correct behaviour: read every frame at its stored offset, de-unsynchronise only
that frame's data, and return title "Unsync \u00ff Title", artist
"\u0410\u043d\u0441\u0430\u043c\u0431\u043b\u044c \u00ff".
"""


def generate(root: Path) -> None:
    write(root / "id3v23_ext_header.mp3", build_id3v23_ext_header())
    write(root / "id3v24_unsync.mp3", build_id3v24_unsync())
    write(root / "flac_id3_preamble.flac", build_flac_id3_preamble())
    write(root / "flac_id3_bad_size.flac", build_flac_id3_bad_size())
    write(root / "cp1251_tags.mp3", build_cp1251_tags())
    write(root / "cp1251_mixed_tags.mp3", build_cp1251_mixed_tags())
    write(root / "latin1_accented_tags.mp3", build_latin1_accented_tags())
    write(root / "flac_corrupt_streaminfo.flac", build_flac_corrupt_streaminfo())
    write(root / "mp3_cbr_no_xing.mp3", build_mp3_cbr_no_xing())
    write(root / "mp3_vbr_no_xing.mp3", build_mp3_vbr_no_xing())
    write(root / "mp3_no_tags.mp3", build_mp3_no_tags())
    note = root / "cp1251_tags.txt"
    note.write_text(CP1251_NOTE, encoding="utf-8", newline="\n")
    print(f"{note.name}: {len(CP1251_NOTE.encode('utf-8'))} bytes of expected values")
    for name, text in (("flac_id3_preamble.txt", FLAC_ID3_PREAMBLE_NOTE),
                       ("flac_id3_bad_size.txt", FLAC_ID3_BAD_SIZE_NOTE),
                       ("cp1251_mixed_tags.txt", CP1251_MIXED_NOTE),
                       ("latin1_accented_tags.txt", LATIN1_ACCENTED_NOTE),
                       ("mp3_vbr_no_xing.txt", MP3_VBR_NO_XING_NOTE)):
        path = root / name
        path.write_text(text, encoding="utf-8", newline="\n")
        print(f"{path.name}: {len(text.encode('utf-8'))} bytes of expected values")
    unsync_note = root / "id3v24_unsync.txt"
    unsync_note.write_text(ID3V24_UNSYNC_NOTE, encoding="utf-8", newline="\n")
    print(f"{unsync_note.name}: "
          f"{len(ID3V24_UNSYNC_NOTE.encode('utf-8'))} bytes of expected values")
    print("Tag fixtures generated; no user music used.")


if __name__ == "__main__":
    generate(Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_TARGET)
