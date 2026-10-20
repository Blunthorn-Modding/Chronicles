import json
import struct
import unittest
import zlib
from pathlib import Path
import sculk_assets as assets


def pixels(path):
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise AssertionError('Not PNG')
    offset = 8
    compressed = b''
    while offset < len(data):
        size = struct.unpack('>I', data[offset:offset+4])[0]
        kind = data[offset+4:offset+8]
        body = data[offset+8:offset+8+size]
        if kind == b'IHDR':
            width, height, depth, color, _, _, interlace = struct.unpack('>IIBBBBB', body)
            assert (width, height, depth, color, interlace) == (16, 16, 8, 6, 0)
        if kind == b'IDAT':
            compressed += body
        offset += size + 12
    raw = zlib.decompress(compressed)
    stride = width * 4
    previous = [0] * stride
    output = []
    offset = 0
    for _ in range(height):
        filter_type = raw[offset]
        row = list(raw[offset+1:offset+1+stride])
        offset += stride + 1
        for i in range(stride):
            left = row[i-4] if i >= 4 else 0
            up = previous[i]
            corner = previous[i-4] if i >= 4 else 0
            if filter_type == 1:
                row[i] = (row[i] + left) & 255
            elif filter_type == 2:
                row[i] = (row[i] + up) & 255
            elif filter_type == 3:
                row[i] = (row[i] + (left + up)//2) & 255
            elif filter_type == 4:
                estimate = left + up - corner
                distances = (abs(estimate-left), abs(estimate-up), abs(estimate-corner))
                prediction = (left, up, corner)[distances.index(min(distances))]
                row[i] = (row[i] + prediction) & 255
            else:
                assert filter_type == 0
        output.extend(tuple(row[i:i+4]) for i in range(0, stride, 4))
        previous = row
    return output


class SculkAssetTests(unittest.TestCase):
    def test_derived_textures_are_static_retain_alpha_and_zero_is_brown(self):
        root = assets.ASSETS / 'quiet_below/textures/block/vitality'
        names = {p.name for p in (root/'stage_1').glob('*.png')}
        self.assertEqual(len(names), 17)
        for name in names:
            variants = [pixels(root/('stage_'+str(stage))/name) for stage in range(1, 4)]
            self.assertEqual([v[3] for v in variants[0]], [v[3] for v in variants[1]])
            self.assertEqual([v[3] for v in variants[1]], [v[3] for v in variants[2]])
            self.assertTrue(all(r >= g >= b for r, g, b, a in variants[2] if a))
        self.assertFalse(list(root.rglob('*.mcmeta')))
        vein = pixels(root/'stage_3/sculk_vein.png')
        self.assertTrue(any(p[3] == 0 for p in vein))
        self.assertTrue(any(p[3] != 0 for p in vein))

    def test_stage_selectors_reference_existing_models_and_keep_native_stage_zero(self):
        def check_model(value, stage):
            if isinstance(value, list):
                for v in value:
                    check_model(v, stage)
            else:
                model = value['model']
                if stage == 0:
                    self.assertTrue(model.startswith('minecraft:block/sculk'))
                else:
                    self.assertTrue(model.startswith('quiet_below:block/vitality/stage_'+str(stage)+'/'))
                    self.assertTrue((assets.ASSETS/'quiet_below/models'/(model.split(':', 1)[1]+'.json')).is_file())
        for block in assets.BLOCKS:
            data = json.loads((assets.ASSETS/'minecraft/blockstates'/(block+'.json')).read_text())
            if 'variants' in data:
                stages = {stage: 0 for stage in range(4)}
                for key, value in data['variants'].items():
                    selected = dict(v.split('=') for v in key.split(','))
                    stage = int(selected[assets.PROPERTY])
                    stages[stage] += 1
                    check_model(value, stage)
                self.assertEqual(len(set(stages.values())), 1)
                self.assertGreater(stages[0], 0)
            else:
                for part in data['multipart']:
                    check_model(part['apply'], int(part['when'][assets.PROPERTY]))
                for mask in range(64):
                    for stage in range(4):
                        state = {name: str(bool(mask & (1 << i))).lower() for i, name in enumerate(('down','up','north','south','west','east'))}
                        state[assets.PROPERTY] = str(stage)
                        matched = [p for p in data['multipart'] if all(state[k] == v for k, v in p['when'].items())]
                        self.assertEqual(len(matched), mask.bit_count() if mask else 6)

    def test_models_reuse_native_geometry_and_all_derived_textures_exist(self):
        root = assets.ASSETS/'quiet_below/models/block/vitality'
        models = list(root.rglob('*.json'))
        self.assertEqual(len(models), 27)
        for path in models:
            model = json.loads(path.read_text())
            self.assertEqual(set(model), {'parent', 'textures'})
            self.assertTrue(model['parent'].startswith('minecraft:block/sculk'))
            for texture in model['textures'].values():
                self.assertTrue(texture.startswith('quiet_below:block/vitality/'))
                self.assertTrue((assets.ASSETS/'quiet_below/textures'/(texture.split(':',1)[1]+'.png')).is_file())
