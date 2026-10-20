"""Compile deterministic static sculk palettes and stage-aware native block models."""
from pathlib import Path
import copy
import hashlib
import json
import zipfile
import workspace as w

PROPERTY = 'quiet_below_vitality'
BLOCKS = ('sculk', 'sculk_vein', 'sculk_catalyst', 'sculk_sensor', 'sculk_shrieker')
ASSETS = w.REPO / 'campaigns/quiet-below/src/main/resources/assets'
CLIENT = w.WORK / 'cache/gradle/caches/fabric-loom' / w.LOCK['minecraft'] / 'minecraft-client.jar'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def generate():
    textures = ASSETS / 'quiet_below/textures/block/vitality'
    w.run([w.JAVA, w.REPO / 'testing/assets/SculkPalette.java', CLIENT, textures,
           w.REPORTS / 'sculk-vitality-palette.png'])
    source_hashes = {}
    models = set()
    with zipfile.ZipFile(CLIENT) as zip:
        def read(name):
            data = zip.read(name)
            source_hashes[name] = hashlib.sha256(data).hexdigest()
            return json.loads(data)

        def replace_models(value, stage):
            if isinstance(value, list):
                return [replace_models(v, stage) for v in value]
            value = copy.deepcopy(value)
            if 'model' in value:
                original = value['model']
                if not original.startswith('minecraft:block/sculk'):
                    raise RuntimeError('Unexpected native sculk model: ' + original)
                models.add(original)
                if stage:
                    value['model'] = 'quiet_below:block/vitality/stage_' + str(stage) + '/' + original.rsplit('/', 1)[1]
            return value

        for block in BLOCKS:
            original = read('assets/minecraft/blockstates/' + block + '.json')
            if 'variants' in original:
                variants = {}
                for key, value in original['variants'].items():
                    for stage in range(4):
                        variants[(key + ',' if key else '') + PROPERTY + '=' + str(stage)] = replace_models(value, stage)
                result = {'variants': variants}
            else:
                parts = []
                for part in original['multipart']:
                    if any(k in part['when'] for k in ('AND', 'OR')):
                        raise RuntimeError('Review complex native multipart conditions')
                    for stage in range(4):
                        parts.append({'when': dict(part['when'], **{PROPERTY: str(stage)}),
                                      'apply': replace_models(part['apply'], stage)})
                result = {'multipart': parts}
            write(ASSETS / 'minecraft/blockstates' / (block + '.json'), result)

        def resolve(model):
            namespace, name = model.split(':', 1)
            data = read('assets/' + namespace + '/models/' + name + '.json')
            parent = data.get('parent')
            merged = resolve(parent if ':' in parent else 'minecraft:' + parent) if parent else {}
            merged.update(data.get('textures', {}))
            return merged

        for model in sorted(models):
            original = resolve(model)
            for stage in range(1, 4):
                replacement = {}
                for key, texture in original.items():
                    if texture.startswith('#'):
                        continue
                    normalized = texture if ':' in texture else 'minecraft:' + texture
                    if normalized.startswith('minecraft:block/sculk'):
                        replacement[key] = 'quiet_below:block/vitality/stage_' + str(stage) + '/' + texture.rsplit('/', 1)[1]
                if not replacement:
                    raise RuntimeError('No sculk texture variables found for ' + model)
                write(ASSETS / 'quiet_below/models/block/vitality' / ('stage_' + str(stage)) /
                      (model.rsplit('/', 1)[1] + '.json'), {'parent': model, 'textures': replacement})
        for name in zip.namelist():
            if name.startswith('assets/minecraft/textures/block/sculk') and name.endswith('.png'):
                source_hashes[name] = hashlib.sha256(zip.read(name)).hexdigest()
    write(w.REPO / 'testing/assets/sculk-source-hashes.json',
          {'minecraft': w.LOCK['minecraft'], 'source': 'installed pinned minecraft-client.jar', 'sha256': source_hashes})
    print('Generated stage selectors for five native blocks and', len(models) * 3, 'derived models')


if __name__ == '__main__':
    generate()
