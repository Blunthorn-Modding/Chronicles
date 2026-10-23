"""Compile resource-packable GUI projections of the original Forge model.

This small orthographic renderer samples the original RGBA atlas without
recolouring it. No extra image library or downloaded artwork is required.
"""
from pathlib import Path
import json
import struct
import zlib
from workshop_assets import png

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'campaigns/quiet-below/src/main/resources/assets/quiet_below'
OUTPUT = ASSETS / 'textures/gui/sprites/workshop'
STATES = {'forge_off': 'ancient_forge', 'forge_idle': 'ancient_forge_kindling', 'forge_working': 'ancient_forge_active'}


def rgba(path):
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('Expected PNG atlas')
    offset, packed, dimensions = 8, bytearray(), None
    while offset < len(data):
        size = int.from_bytes(data[offset:offset+4], 'big')
        kind, body = data[offset+4:offset+8], data[offset+8:offset+8+size]
        checksum = int.from_bytes(data[offset+8+size:offset+12+size], 'big')
        if zlib.crc32(kind+body) != checksum:
            raise ValueError('PNG CRC mismatch')
        if kind == b'IHDR':
            width, height, depth, colour, compression, filtering, interlace = struct.unpack('>IIBBBBB', body)
            if (depth, colour, compression, filtering, interlace) != (8, 6, 0, 0, 0):
                raise ValueError('Expected non-interlaced 8-bit RGBA atlas')
            dimensions = width, height
        elif kind == b'IDAT':
            packed.extend(body)
        offset += 12+size
    if dimensions is None:
        raise ValueError('Missing atlas dimensions')
    width, height = dimensions
    raw = zlib.decompress(packed)
    stride = width*4
    if len(raw) != height*(stride+1):
        raise ValueError('Invalid atlas scanline size')
    prior, rows = bytearray(stride), []
    def paeth(a, b, c):
        value = a+b-c
        distances = abs(value-a), abs(value-b), abs(value-c)
        return (a, b, c)[distances.index(min(distances))]
    for y in range(height):
        offset = y*(stride+1)
        mode = raw[offset]
        row = bytearray(raw[offset+1:offset+1+stride])
        if mode not in range(5):
            raise ValueError('Unsupported PNG row filter')
        for index in range(stride):
            left = row[index-4] if index >= 4 else 0
            above = prior[index]
            upper_left = prior[index-4] if index >= 4 else 0
            adjustment = (0, left, above, (left+above)//2, paeth(left, above, upper_left))[mode]
            row[index] = (row[index]+adjustment) & 255
        rows.append(row)
        prior = row
    return width, height, rows


def generate(output=OUTPUT):
    output.mkdir(parents=True, exist_ok=True)
    for name, model in STATES.items():
        data = json.loads((ASSETS / 'models/block' / (model+'.json')).read_text(encoding='utf-8'))
        atlas = rgba(ASSETS / 'textures/block' / (model+'.png'))
        width, height, rows = atlas
        pixels = [[(0, 0, 0, 0) for _ in range(64)] for _ in range(64)]
        for element in sorted(data['elements'], key=lambda element: element['to'][1]):
            if element.get('rotation', {}).get('angle', 0) != 0:
                raise ValueError('Forge projection requires axis-aligned model')
            face = element.get('faces', {}).get('up')
            if face is None:
                continue
            if face.get('rotation', 0) != 0 or data['textures'][face['texture'][1:]] != 'quiet_below:block/'+model:
                raise ValueError('Unexpected Forge face texture/rotation')
            start, end = element['from'], element['to']
            u0, v0, u1, v1 = face['uv']
            for y in range(64):
                z = -3.5+(y+.5)*23/64
                if not start[2] <= z < end[2]:
                    continue
                for x in range(64):
                    world_x = -3.5+(x+.5)*23/64
                    if not start[0] <= world_x < end[0]:
                        continue
                    u = u0+(world_x-start[0])/(end[0]-start[0])*(u1-u0)
                    v = v0+(z-start[2])/(end[2]-start[2])*(v1-v0)
                    tx, ty = max(0, min(width-1, int(u*width/16))), max(0, min(height-1, int(v*height/16)))
                    colour = tuple(rows[ty][tx*4:tx*4+4])
                    if colour[3] not in (0, 255):
                        raise ValueError('Expected cutout Forge atlas, not translucent blending')
                    if colour[3]:
                        pixels[y][x] = colour
        (output / (name+'.png')).write_bytes(png(64, 64, lambda x, y: pixels[y][x]))


if __name__ == '__main__':
    generate()
    print('Compiled three original Forge model projections for the workshop GUI.')
