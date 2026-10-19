"""Generate the original 16x16 M3 pixel textures without external assets.

Run from the repository root with:
    python campaigns/quiet-below/tools/generate_m3_textures.py
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/quiet_below/textures"


def png(path: Path, palette: dict[str, tuple[int, int, int, int]], rows: tuple[str, ...]) -> None:
    """Write one non-interlaced RGBA PNG from a hand-authored 16-pixel grid."""
    if len(rows) != 16 or any(len(row) != 16 for row in rows):
        raise ValueError(f"{path.name} must be a 16x16 grid")
    raw = b"".join(b"\0" + b"".join(bytes(palette[pixel]) for pixel in row) for row in rows)

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

    payload = b"\x89PNG\r\n\x1a\n"
    payload += chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    payload += chunk(b"IDAT", zlib.compress(raw, level=9))
    payload += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


LENS = {
    ".": (0, 0, 0, 0), "d": (70, 43, 31, 255), "b": (121, 70, 42, 255),
    "c": (181, 111, 57, 255), "h": (232, 167, 85, 255), "s": (62, 109, 119, 255),
    "t": (76, 160, 166, 255), "l": (145, 227, 214, 255), "a": (83, 53, 112, 255),
    "p": (149, 100, 190, 255), "w": (221, 190, 240, 255),
}
LENS_ROWS = (
    "................", "................", "....dcb.........", "...dcchb........",
    "..dcstscb.......", ".dcstltsscb.....", ".bctltlsscb.....", ".bcsltltsscb....",
    "..bcstltsscb....", "...bcsstscb.....", "....bcccbd......", ".....dbbd.......",
    "......dbaa......", ".......bpa......", "........ap......", "................",
)


def main() -> None:
    png(ROOT / "item/echo_lens.png", LENS, LENS_ROWS)


if __name__ == "__main__":
    main()
