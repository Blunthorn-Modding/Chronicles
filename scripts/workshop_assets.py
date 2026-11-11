"""Compile original pixel-geometry workstation sprites; no upstream artwork is read.

Run from the repo: python scripts/workshop_assets.py
Resource packs can replace the generated chronicle_core:workshop GUI sprites.
"""
from pathlib import Path
import json
import struct
import zlib

ASSETS = Path(__file__).resolve().parents[1] / 'core/src/main/resources/assets/chronicle_core/textures/gui/sprites/workshop'
SURFACE = (198, 198, 198, 255)
DARK = (32, 32, 32, 255)
HIGHLIGHT = (240, 240, 240, 255)
SHADE = (85, 85, 85, 255)
RECESS = (139, 139, 139, 255)
GLYPH = (104, 104, 104, 255)
CLEAR = (0, 0, 0, 0)


def png(width, height, pixel):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    raw = b''.join(b'\0' + bytes(channel for x in range(width) for channel in pixel(x, y)) for y in range(height))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')


def sprites():
    def panel(x, y):
        if (x, y) in {(0, 0), (23, 0), (0, 23), (23, 23)}:
            return CLEAR
        if x in (0, 23) or y in (0, 23):
            return DARK
        if x == 22 or y == 22:
            return SHADE
        if x == 1 or y == 1:
            return HIGHLIGHT
        return SURFACE

    def slot(x, y, result=False):
        if x == 0 or y == 0:
            return (62, 87, 92, 255) if result else (55, 55, 55, 255)
        if x == 17 or y == 17:
            return (186, 213, 216, 255) if result else HIGHLIGHT
        if result and (x in (1, 16) or y in (1, 16)):
            return (104, 140, 149, 255)
        return RECESS

    return {
        'panel': (24, 24, panel),
        'slot': (18, 18, lambda x, y: slot(x, y)),
        'result': (18, 18, lambda x, y: slot(x, y, True)),
        'plus': (10, 10, lambda x, y: GLYPH if 1 <= y <= 8 and x in (4, 5) or 1 <= x <= 8 and y in (4, 5) else CLEAR),
        'arrow': (28, 14, lambda x, y: GLYPH if x <= 18 and 5 <= y <= 8 or 18 <= x <= 20 + min(y, 13-y) else CLEAR),
    }


def generate(target=ASSETS):
    target.mkdir(parents=True, exist_ok=True)
    for name, (width, height, pixel) in sprites().items():
        (target / (name + '.png')).write_bytes(png(width, height, pixel))
    metadata = {'gui': {'scaling': {'type': 'nine_slice', 'width': 24, 'height': 24, 'border': 2}}}
    (target / 'panel.png.mcmeta').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    generate()
    print('Generated five original resource-packable workstation sprites.')
